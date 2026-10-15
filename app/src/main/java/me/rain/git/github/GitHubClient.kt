package me.rain.git.github

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

private val jsonType = "application/json; charset=utf-8".toMediaType()

data class Account(val login: String, val displayName: String, val avatarUrl: String, val id: Long = 0L)
data class RemoteRepo(
    val owner: String, val name: String, val description: String, val fullName: String,
    val privateRepo: Boolean, val defaultBranch: String, val updatedAt: String
) {
    val cloneUrl get() = "https://github.com/$owner/$name.git"
}
data class Workflow(val id: Long, val name: String, val path: String, val state: String)
data class Run(
    val id: Long, val name: String, val number: Int, val status: String,
    val conclusion: String, val branch: String, val sha: String, val url: String, val created: String
)
data class Job(val id: Long, val name: String, val status: String, val conclusion: String, val steps: List<Step>)
data class Step(val name: String, val status: String, val conclusion: String)
data class Artifact(val id: Long, val name: String, val size: Long, val expired: Boolean)
data class CollaborationItem(
    val number: Int, val title: String, val state: String, val author: String,
    val url: String, val updated: String, val comments: Int, val draft: Boolean
)

class GitHubClient(private val token: () -> String?) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun call(url: String, method: String = "GET", body: String? = null, auth: Boolean = true): String {
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "RainGit-Android")
            .header("X-GitHub-Api-Version", "2026-03-10")
        if (auth) token()?.let { request.header("Authorization", "Bearer $it") }
        if (body != null) request.method(method, body.toRequestBody(jsonType))
        else if (method != "GET") request.method(method, "".toRequestBody(jsonType))
        client.newCall(request.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrDefault(text.take(140))
                throw GitHubException(r.code, message, r.header("Retry-After")?.toLongOrNull() ?: 60L)
            }
            return text
        }
    }

    private fun endpoint(path: String) = "https://api.github.com$path"
    private fun repoPath(r: RemoteRepo) = "/repos/${enc(r.owner)}/${enc(r.name)}"

    suspend fun me() = withContext(Dispatchers.IO) {
        val j = JSONObject(call(endpoint("/user")))
        Account(j.getString("login"), j.optString("name").takeUnless { it.isBlank() } ?: j.getString("login"), j.optString("avatar_url"), j.optLong("id"))
    }

    suspend fun repos(): List<RemoteRepo> = withContext(Dispatchers.IO) {
        val all = mutableListOf<RemoteRepo>()
        var page = 1
        do {
            val a = JSONArray(call(endpoint("/user/repos?sort=updated&per_page=100&affiliation=owner,collaborator,organization_member&page=$page")))
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                all += RemoteRepo(
                    j.getJSONObject("owner").getString("login"), j.getString("name"), j.optString("description").let { if (it == "null") "" else it },
                    j.getString("full_name"), j.optBoolean("private"), j.optString("default_branch", "main"), j.optString("updated_at")
                )
            }
            page++
        } while (a.length() == 100)
        all
    }

    suspend fun workflows(r: RemoteRepo): List<Workflow> = withContext(Dispatchers.IO) {
        val a = JSONObject(call(endpoint("${repoPath(r)}/actions/workflows?per_page=100"))).getJSONArray("workflows")
        (0 until a.length()).map {
            a.getJSONObject(it).let { j -> Workflow(j.getLong("id"), j.getString("name"), j.optString("path"), j.optString("state")) }
        }
    }

    suspend fun runs(r: RemoteRepo): List<Run> = withContext(Dispatchers.IO) {
        val a = JSONObject(call(endpoint("${repoPath(r)}/actions/runs?per_page=50"))).getJSONArray("workflow_runs")
        (0 until a.length()).map {
            a.getJSONObject(it).let { j ->
                Run(j.getLong("id"), j.optString("name"), j.optInt("run_number"), j.optString("status"),
                    j.optString("conclusion").takeUnless { x -> x == "null" }.orEmpty(),
                    j.optString("head_branch"), j.optString("head_sha"), j.optString("html_url"), j.optString("created_at"))
            }
        }
    }

    suspend fun jobs(r: RemoteRepo, run: Run): List<Job> = withContext(Dispatchers.IO) {
        val a = JSONObject(call(endpoint("${repoPath(r)}/actions/runs/${run.id}/jobs?per_page=100"))).getJSONArray("jobs")
        (0 until a.length()).map { n ->
            val j = a.getJSONObject(n)
            val steps = j.optJSONArray("steps") ?: JSONArray()
            Job(j.getLong("id"), j.optString("name"), j.optString("status"), j.optString("conclusion"),
                (0 until steps.length()).map { i ->
                    steps.getJSONObject(i).let { s -> Step(s.optString("name"), s.optString("status"), s.optString("conclusion")) }
                })
        }
    }

    suspend fun artifacts(r: RemoteRepo, run: Run): List<Artifact> = withContext(Dispatchers.IO) {
        val a = JSONObject(call(endpoint("${repoPath(r)}/actions/runs/${run.id}/artifacts?per_page=100"))).getJSONArray("artifacts")
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { j -> Artifact(j.getLong("id"), j.getString("name"), j.optLong("size_in_bytes"), j.optBoolean("expired")) }
        }
    }

    suspend fun collaboration(r: RemoteRepo, filter: Int): List<CollaborationItem> = withContext(Dispatchers.IO) {
        val path = if (filter == 0) "pulls" else "issues"
        val data = JSONArray(call(endpoint("${repoPath(r)}/$path?state=open&sort=updated&direction=desc&per_page=60")))
        (0 until data.length()).mapNotNull { i ->
            val j = data.getJSONObject(i)
            if (filter == 1 && j.has("pull_request")) return@mapNotNull null
            CollaborationItem(j.optInt("number"), j.optString("title"), j.optString("state"),
                j.optJSONObject("user")?.optString("login").orEmpty(), j.optString("html_url"),
                j.optString("updated_at"), j.optInt("comments"), j.optBoolean("draft"))
        }
    }

    suspend fun createIssue(r: RemoteRepo, title: String, description: String): Int = withContext(Dispatchers.IO) {
        require(title.trim().isNotEmpty()) { "请输入 Issue 标题" }
        JSONObject(call(endpoint("${repoPath(r)}/issues"), "POST",
            JSONObject().put("title", title.trim()).put("body", description.trim()).toString())).getInt("number")
    }

    suspend fun dispatch(r: RemoteRepo, w: Workflow, branch: String, inputsText: String) = withContext(Dispatchers.IO) {
        require(branch.isNotBlank()) { "请输入分支" }
        val inputs = if (inputsText.isBlank()) JSONObject() else JSONObject(inputsText)
        call(endpoint("${repoPath(r)}/actions/workflows/${w.id}/dispatches"), "POST",
            JSONObject().put("ref", branch).put("inputs", inputs).toString())
    }

    suspend fun control(r: RemoteRepo, run: Run, operation: String) = withContext(Dispatchers.IO) {
        require(operation in setOf("cancel", "rerun", "rerun-failed-jobs"))
        call(endpoint("${repoPath(r)}/actions/runs/${run.id}/$operation"), "POST")
    }

    suspend fun run(r: RemoteRepo, id: Long): Run = withContext(Dispatchers.IO) {
        val j = JSONObject(call(endpoint("${repoPath(r)}/actions/runs/$id")))
        Run(j.getLong("id"), j.optString("name"), j.optInt("run_number"), j.optString("status"),
            j.optString("conclusion").takeUnless { it == "null" }.orEmpty(), j.optString("head_branch"),
            j.optString("head_sha"), j.optString("html_url"), j.optString("created_at"))
    }

    // Resolve each short-lived link at download time. Never forward the GitHub token to storage hosts.
    suspend fun download(r: RemoteRepo, suffix: String, destination: File,
        progress: suspend (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(endpoint("${repoPath(r)}$suffix"))
            .header("Authorization", "Bearer ${token() ?: throw IOException("请重新登录")}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2026-03-10")
            .header("User-Agent", "RainGit-Android").build()
        val resolver = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val transfer = client.newBuilder().callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).followSslRedirects(false).build()
        try {
            resolver.newCall(request).execute().use { first ->
                val response = if (first.code in listOf(301, 302, 303, 307, 308)) {
                    val location = first.header("Location") ?: throw IOException("下载链接为空")
                    val url = first.request.url.resolve(location) ?: throw IOException("无效下载链接")
                    require(url.isHttps) { "拒绝非 HTTPS 下载地址" }
                    transfer.newCall(Request.Builder().url(url).header("User-Agent", "RainGit-Android").build()).execute()
                } else {
                    if (!first.isSuccessful) throw GitHubException(first.code, "下载内容尚不可用", first.header("Retry-After")?.toLongOrNull() ?: 60L)
                    first
                }
                response.use { result ->
                    if (!result.isSuccessful) throw GitHubException(result.code, "下载失败")
                    val body = result.body ?: throw IOException("没有下载内容")
                    val expected = body.contentLength()
                    var total = 0L
                    var lastUpdate = 0L
                    destination.outputStream().buffered().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > 1024L * 1024 * 1024) throw IOException("文件超过 1 GiB 下载上限")
                                output.write(buffer, 0, count)
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastUpdate >= 300) { progress(total, expected); lastUpdate = now }
                            }
                        }
                    }
                    if (expected >= 0 && total != expected) throw IOException("下载不完整：$total / $expected 字节")
                    progress(total, expected)
                }
            }
        } catch (e: Exception) { destination.delete(); throw e }
    }

    private fun appendTail(text: StringBuilder, chunk: String, limit: Int) {
        text.append(chunk)
        if (text.length > limit) text.delete(0, text.length - limit)
    }

    suspend fun readableJobLogs(r: RemoteRepo, jobId: Long, cache: File): String = withContext(Dispatchers.IO) {
        try {
            download(r, "/actions/jobs/$jobId/logs", cache)
            require(cache.length() > 0) { "工作任务日志为空" }
            cache.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8192)
                val text = StringBuilder()
                var read = 0L
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    read += count
                    appendTail(text, String(buffer, 0, count), 1_500_000)
                }
                (if (read > 1_500_000) "[长日志仅保留末尾 150 万字符]\n" else "") + text
            }
        } finally { cache.delete() }
    }

    suspend fun readableLogs(r: RemoteRepo, run: Run, cache: File): String = withContext(Dispatchers.IO) {
        try {
            download(r, "/actions/runs/${run.id}/logs", cache)
            val text = StringBuilder()
            var total = 0L
            ZipFile(cache).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".txt", true) }.toList()
                // GitHub includes both a root job log and the same output split into step files.
                val jobLog = Regex("^\\d+_(.+)\\.txt$", RegexOption.IGNORE_CASE)
                val aggregateJobs = entries.filter { '/' !in it.name }
                    .mapNotNull { jobLog.matchEntire(it.name)?.groupValues?.get(1) }.toSet()
                val selected = entries.filter { entry ->
                    '/' !in entry.name || entry.name.substringBefore('/') !in aggregateJobs
                }.sortedWith(compareBy({ it.name.substringBeforeLast('/', "") },
                    { it.name.substringAfterLast('/').substringBefore('_').toIntOrNull() ?: Int.MAX_VALUE },
                    { it.name }))
                selected.forEach { entry ->
                    appendTail(text, "\n\n── ${entry.name} ──\n", 700_000)
                    zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                        val chars = CharArray(8192)
                        while (true) {
                            val count = reader.read(chars)
                            if (count < 0) break
                            total += count
                            require(total <= 64L * 1024 * 1024) { "日志解压内容过大，请选择单个任务查看" }
                            appendTail(text, String(chars, 0, count), 700_000)
                        }
                    }
                }
            }
            if (text.isEmpty()) "日志压缩包中没有文本日志"
            else (if (total > 700_000) "[长日志仅保留末尾 70 万字符]\n" else "") + text
        } finally { cache.delete() }
    }
}

class GitHubException(val status: Int, detail: String, val retryAfterSeconds: Long = 60L) : IOException("GitHub $status: $detail")

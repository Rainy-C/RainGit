package me.rain.git.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
import java.util.zip.ZipInputStream

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

private val jsonType = "application/json; charset=utf-8".toMediaType()

data class Account(val login: String, val displayName: String, val avatarUrl: String)
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
data class DeviceCode(val code: String, val userCode: String, val url: String, val interval: Int, val expires: Int)

class GitHubClient(private val token: () -> String?) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
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
                throw IOException("GitHub ${r.code}: $message")
            }
            return text
        }
    }

    private fun endpoint(path: String) = "https://api.github.com$path"
    private fun repoPath(r: RemoteRepo) = "/repos/${enc(r.owner)}/${enc(r.name)}"

    suspend fun me() = withContext(Dispatchers.IO) {
        val j = JSONObject(call(endpoint("/user")))
        Account(j.getString("login"), j.optString("name").takeUnless { it.isBlank() } ?: j.getString("login"), j.optString("avatar_url"))
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

    suspend fun download(r: RemoteRepo, suffix: String, destination: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(endpoint("${repoPath(r)}$suffix"))
            .header("Authorization", "Bearer ${token() ?: throw IOException("请重新登录")}")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "RainGit-Android")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载失败: HTTP ${response.code}")
                val content = response.body ?: throw IOException("没有下载内容")
                destination.outputStream().use { out ->
                    content.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > 512L * 1024 * 1024) throw IOException("归档文件超过 512 MB 限制")
                            out.write(buffer, 0, n)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    suspend fun readableLogs(r: RemoteRepo, run: Run, cache: File): String = withContext(Dispatchers.IO) {
        download(r, "/actions/runs/${run.id}/logs", cache)
        val text = StringBuilder()
        try {
            ZipInputStream(cache.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && text.length < 700_000) {
                    if (!entry.isDirectory && entry.name.endsWith(".txt", ignoreCase = true)) {
                        text.append("\n\n── ").append(entry.name.substringAfterLast('/')).append(" ──\n")
                        val bytes = ByteArray(8192)
                        while (text.length < 700_000) {
                            val n = zip.read(bytes)
                            if (n < 0) break
                            text.append(String(bytes, 0, n, Charsets.UTF_8))
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (text.isEmpty()) "日志压缩包中没有找到文本日志" else text.toString().take(700_000)
        } finally { cache.delete() }
    }

    private fun oauthPost(url: String, parameters: Map<String, String>): JSONObject {
        val form = FormBody.Builder().apply { parameters.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(url).post(form).header("Accept", "application/json").build()
        client.newCall(request).execute().use { r ->
            val j = JSONObject(r.body?.string().orEmpty())
            if (!r.isSuccessful) throw IOException(j.optString("error_description", "OAuth HTTP ${r.code}"))
            return j
        }
    }

    suspend fun startDeviceFlow(clientId: String): DeviceCode = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "请填写 GitHub OAuth Client ID" }
        val j = oauthPost("https://github.com/login/device/code", mapOf("client_id" to clientId, "scope" to "repo workflow read:user"))
        DeviceCode(j.getString("device_code"), j.getString("user_code"), j.getString("verification_uri"), j.optInt("interval", 5).coerceAtLeast(5), j.optInt("expires_in", 900))
    }

    suspend fun pollDevice(clientId: String, code: DeviceCode): String? = withContext(Dispatchers.IO) {
        val j = oauthPost("https://github.com/login/oauth/access_token", mapOf(
            "client_id" to clientId, "device_code" to code.code,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
        ))
        if (j.has("access_token")) return@withContext j.getString("access_token")
        when (j.optString("error")) {
            "authorization_pending" -> null
            "slow_down" -> throw SlowDownException()
            "expired_token" -> throw IOException("授权已过期，请重新获取验证码")
            "access_denied" -> throw IOException("用户已拒绝授权")
            else -> throw IOException(j.optString("error_description", "OAuth 授权失败"))
        }
    }
}
class SlowDownException : IOException("GitHub 要求降低轮询频率")

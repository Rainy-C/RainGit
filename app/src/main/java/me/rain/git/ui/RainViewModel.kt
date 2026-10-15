package me.rain.git.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import me.rain.git.auth.SecureStore
import me.rain.git.github.*
import me.rain.git.local.LocalGit
import java.io.File
import java.io.IOException

class RainViewModel(application: Application) : AndroidViewModel(application) {
    val store = SecureStore(application)
    private val client = GitHubClient { store.token() }
    private val git = LocalGit { store.token() }
    private var activeJob: Job? = null
    private var downloadJob: Job? = null
    private val actionsMutex = Mutex()
    private var contextVersion = 0L
    private var logVersion = 0L
    private var logJobId: Long? = null
    private var logRunId: Long? = null
    private var nextPollAt = 0L
    private var lastLogPoll = 0L
    private var logTask: Job? = null
    private var lastWorkflowPoll = 0L
    var actionsStatus by androidx.compose.runtime.mutableStateOf("等待刷新")
        private set
    var lastDownloadedPath by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var logStatus by androidx.compose.runtime.mutableStateOf("")
        private set
    var logLoading by androidx.compose.runtime.mutableStateOf(false)
        private set
    var downloading by androidx.compose.runtime.mutableStateOf(false)
        private set

    var themeMode by androidx.compose.runtime.mutableIntStateOf(store.themeMode)
        private set
    var downloadState by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var collaboration by androidx.compose.runtime.mutableStateOf<List<CollaborationItem>>(emptyList())
        private set
    var collaborationFilter by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    var account by androidx.compose.runtime.mutableStateOf<Account?>(null)
        private set
    var repos by androidx.compose.runtime.mutableStateOf<List<RemoteRepo>>(emptyList())
        private set
    var localDirs by androidx.compose.runtime.mutableStateOf<List<File>>(emptyList())
        private set
    var favoriteRepos by androidx.compose.runtime.mutableStateOf(store.favoriteRepos)
        private set
    var selectedRepo by androidx.compose.runtime.mutableStateOf<RemoteRepo?>(null)
        private set
    var selectedDir by androidx.compose.runtime.mutableStateOf<File?>(null)
        private set
    var snapshot by androidx.compose.runtime.mutableStateOf<LocalGit.Snapshot?>(null)
        private set
    var files by androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
        private set
    var workflows by androidx.compose.runtime.mutableStateOf<List<Workflow>>(emptyList())
        private set
    var runs by androidx.compose.runtime.mutableStateOf<List<Run>>(emptyList())
        private set
    var chosenRun by androidx.compose.runtime.mutableStateOf<Run?>(null)
        private set
    var jobs by androidx.compose.runtime.mutableStateOf<List<me.rain.git.github.Job>>(emptyList())
        private set
    var artifacts by androidx.compose.runtime.mutableStateOf<List<Artifact>>(emptyList())
        private set
    var logText by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var busy by androidx.compose.runtime.mutableStateOf(false)
        private set
    var message by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var editorPath by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var editorText by androidx.compose.runtime.mutableStateOf("")
        private set
    var diffText by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var detailTab by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var screen by androidx.compose.runtime.mutableStateOf("home")
        private set

    init {
        reloadLocal()
        if (store.token() != null) { account = store.cachedAccount(); refreshAccount() }
    }

    fun setTheme(v: Int) { store.themeMode = v; themeMode = v }
    fun selectCollaborationFilter(value: Int) { if (busy) return; collaborationFilter = value.coerceIn(0, 1); refreshCollaboration() }
    fun clearMessage() { message = null }
    fun closeLog() { logTask?.cancel(); logTask = null; logVersion++; logText = null; logJobId = null; logRunId = null; logStatus = ""; logLoading = false }
    fun pauseActions() { logTask?.cancel() }
    fun closeDiff() { diffText = null }
    fun closeEditor() { editorPath = null }
    fun editText(text: String) { editorText = text }
    fun navigate(page: String) { if (!busy) { contextVersion++; closeLog(); screen = page } }
    fun selectDetailTab(tab: Int) {
        if (busy) return
        contextVersion++
        closeLog()
        detailTab = tab
        nextPollAt = 0L
        if (tab < 2 && selectedDir != null) refreshLocal()
        if (tab == 2 && selectedRepo != null) refreshActions()
        if (tab == 3 && selectedRepo != null) refreshCollaboration()
    }
    fun closeDetail() {
        if (busy) return
        contextVersion++
        closeLog()
        selectedRepo = null
        selectedDir = null
        snapshot = null
        screen = "home"
    }

    private fun launchAction(action: suspend () -> Unit) {
        if (busy) return
        activeJob = viewModelScope.launch {
            busy = true
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                message = e.message ?: "操作失败"
            }
            finally { busy = false }
        }
    }

    private suspend fun <T> local(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun initializeAuthor(account: Account) {
        store.cacheAccount(account)
        if (store.author.isBlank()) store.author = account.login
        if (store.email.isBlank() && account.id > 0) {
            store.email = "${account.id}+${account.login}@users.noreply.github.com"
        }
    }

    fun refreshAccount() = launchAction {
        account = client.me()
        initializeAuthor(account!!)
        refreshReposInner()
    }

    private suspend fun refreshReposInner() {
        repos = client.repos()
        reloadLocal()
    }

    fun refreshRepos() = launchAction {
        refreshReposInner()
        message = "仓库列表已同步"
    }

    fun loginWithToken(token: String) = launchAction {
        val cleaned = token.trim()
        require(cleaned.isNotEmpty()) { "请输入 GitHub Token" }
        val verified = GitHubClient { cleaned }.me()
        store.saveToken(cleaned)
        account = verified
        initializeAuthor(verified)
        refreshReposInner()
        message = "已登录 ${verified.login}"
    }

    fun logout() {
        contextVersion++
        closeLog()
        activeJob?.cancel()
        downloadJob?.cancel()
        busy = false
        store.logout()
        account = null
        repos = emptyList()
        selectedRepo = null
        selectedDir = null
        snapshot = null
        workflows = emptyList()
        runs = emptyList()
        jobs = emptyList()
        artifacts = emptyList()
        collaboration = emptyList()
        chosenRun = null
        files = emptyList()
        editorPath = null
        diffText = null
        lastDownloadedPath = null
        screen = "home"
        message = "已退出账号"
    }

    fun toggleFavorite(fullName: String) {
        favoriteRepos = if (fullName in favoriteRepos) favoriteRepos - fullName else favoriteRepos + fullName
        store.favoriteRepos = favoriteRepos
    }

    fun reloadLocal() {
        localDirs = runCatching { store.root().listFiles()?.filter { !it.name.startsWith(".rain-") && git.isRepo(it) }?.sortedBy { it.name } ?: emptyList() }
            .getOrDefault(emptyList())
    }

    fun chooseRoot(uri: Uri) = launchAction {
        require(!downloading) { "请等待下载完成后再切换目录" }
        val root = store.setRootFromTree(uri)
        reloadLocal()
        message = "工作目录：${root.path}"
    }

    fun saveAuthor(name: String, address: String) {
        store.author = name.trim()
        store.email = address.trim()
        message = "Git 作者信息已保存"
    }

    fun usePrivateRoot() {
        if (downloading || busy) { message = "操作完成后再切换目录"; return }
        store.resetRoot()
        reloadLocal()
        message = "已切换至应用专属目录"
    }

    fun openRemote(repo: RemoteRepo, tab: Int = 0) {
        if (busy) return
        contextVersion++
        closeLog()
        nextPollAt = 0L
        actionsStatus = "等待刷新"
        selectedRepo = repo
        selectedDir = git.directory(store.root(), repo).takeIf { git.isRepo(it) }
        snapshot = null
        files = emptyList()
        chosenRun = null
        jobs = emptyList()
        artifacts = emptyList()
        workflows = emptyList()
        runs = emptyList()
        lastWorkflowPoll = 0L
        lastDownloadedPath = null
        collaboration = emptyList()
        detailTab = tab
        screen = "detail"
        if (tab == 2) refreshActions() else if (tab == 3) refreshCollaboration() else if (selectedDir != null) refreshLocal()
    }

    fun openLocal(dir: File) {
        if (busy) return
        contextVersion++
        closeLog()
        nextPollAt = 0L
        require(git.isRepo(dir))
        selectedDir = dir
        val origin = runCatching { git.remoteUrl(dir) }.getOrDefault("")
        selectedRepo = repos.firstOrNull { it.cloneUrl.removeSuffix(".git") == origin.removeSuffix(".git") }
            ?: repos.firstOrNull { git.directory(store.root(), it).canonicalFile == dir.canonicalFile }
        snapshot = null
        files = emptyList()
        chosenRun = null
        workflows = emptyList()
        runs = emptyList()
        detailTab = 0
        collaboration = emptyList()
        screen = "detail"
        refreshLocal()
    }

    fun clone() = launchAction {
        require(!downloading) { "下载完成后再克隆仓库" }
        val repo = selectedRepo ?: throw IOException("请先选择远程仓库")
        val dir = local { git.clone(repo, store.root()) }
        selectedDir = dir
        refreshLocalInner(dir)
        reloadLocal()
        message = "已克隆到 ${dir.path}"
    }

    private suspend fun refreshLocalInner(dir: File) {
        val result = local { git.excludeDist(dir); git.snapshot(dir) to git.files(dir) }
        if (selectedDir == dir) { snapshot = result.first; files = result.second }
    }

    fun refreshLocal() = launchAction {
        val dir = selectedDir ?: return@launchAction
        refreshLocalInner(dir)
    }

    fun gitOperation(name: String, value: String = "") = launchAction {
        val dir = selectedDir ?: throw IOException("请先 Clone 仓库")
        require(!downloading) { "下载完成后再进行 Git 写操作" }
        local { git.configureAuthor(dir, store.author, store.email) }
        try {
        val result = withContext(Dispatchers.IO) {
            when (name) {
                "fetch" -> git.fetch(dir)
                "pull" -> git.pull(dir)
                "push" -> git.push(dir)
                "stage" -> { git.stage(dir, value); "已暂存" }
                "unstage" -> { git.unstage(dir, value); "已取消暂存" }
                "unstage-all" -> { git.unstageAll(dir); "已取消全部暂存" }
                "discard" -> { git.discard(dir, value); "已撤销未暂存的修改：$value" }
                "tag" -> { git.createTag(dir, value); "已创建本地标签 $value" }
                "push-tags" -> git.pushTags(dir)
                "commit" -> "提交成功 ${git.commit(dir, value, store.author, store.email)}"
                "branch" -> { git.createBranch(dir, value); "已创建分支 $value" }
                "checkout" -> { git.checkout(dir, value); "已切换至 $value" }
                "stash" -> "Stash ${git.stash(dir)}"
                "apply" -> "Stash Apply ${git.applyStash(dir)}"
                "merge" -> git.merge(dir, value)
                "abort-merge" -> { git.abortMerge(dir); "已中止合并" }
                "delete-branch" -> { git.deleteBranch(dir, value); "已删除本地分支" }
                "restore-stash" -> { git.restoreStash(dir, value); "已恢复 Stash，原备份仍保留" }
                "drop-stash" -> { git.dropStash(dir, value); "已删除 Stash" }
                "remote" -> { git.setRemote(dir, value); "origin 地址已更新" }
                else -> throw IOException("不支持的 Git 操作")
            }
        }
        message = result
        if (name == "remote") {
            selectedRepo = repos.firstOrNull { it.cloneUrl.removeSuffix(".git") == value.removeSuffix(".git") }
            workflows = emptyList(); runs = emptyList(); chosenRun = null; jobs = emptyList(); artifacts = emptyList()
            contextVersion++
        }
        } finally { refreshLocalInner(dir) }
    }

    fun publishChanges(commitMessage: String, onPublished: () -> Unit = {}) = launchAction {
        require(!downloading) { "下载完成后再发布" }
        val dir = selectedDir ?: throw IOException("请先克隆仓库")
        require(commitMessage.isNotBlank()) { "请输入这次修改的简短说明" }
        require(store.author.isNotBlank() && store.email.contains('@')) { "请先在设置里补全提交身份" }
        val before = local { git.snapshot(dir) }
        require(before.conflicts.isEmpty()) { "发现冲突文件，请先处理冲突" }
        val dirty = (before.staged + before.changed + before.untracked + before.deleted).distinct()
        require(dirty.isNotEmpty()) { "没有待发布的文件。可直接推送已有提交" }
        var commitSha: String? = null
        try {
            local {
                git.fetch(dir)
                val behind = git.snapshot(dir).behind ?: 0
                if (behind > 0) throw IOException("远端比本地多 $behind 次提交，请先同步远端再发布")
                git.stage(dir, ".")
                commitSha = git.commit(dir, commitMessage, store.author, store.email)
                git.push(dir)
            }
            onPublished()
            message = "已推送 $commitSha · ${dirty.size} 个文件"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (commitSha != null) {
                message = "已提交 $commitSha，但推送失败：${e.message.orEmpty()}。请使用「推送已有提交」重试。"
            } else {
                message = e.message ?: "发布失败"
            }
        } finally {
            refreshLocalInner(dir)
        }
    }

    fun pushExisting() = launchAction {
        require(!downloading) { "下载完成后再推送" }
        val dir = selectedDir ?: throw IOException("请先克隆仓库")
        try {
            message = local { git.push(dir) }
        } finally {
            refreshLocalInner(dir)
        }
    }

    fun showDiff(staged: Boolean) = launchAction {
        val dir = selectedDir ?: return@launchAction
        diffText = withContext(Dispatchers.IO) { git.diff(dir, staged) }
    }

    fun openEditor(path: String) = launchAction {
        val dir = selectedDir ?: return@launchAction
        editorText = withContext(Dispatchers.IO) { git.read(dir, path) }
        editorPath = path
    }

    fun saveEditor() = launchAction {
        val dir = selectedDir ?: return@launchAction
        val path = editorPath ?: return@launchAction
        withContext(Dispatchers.IO) { git.write(dir, path, editorText) }
        editorPath = null
        refreshLocalInner(dir)
        message = "已保存文件（尚未提交）"
    }

    fun refreshActions() { viewModelScope.launch { pollActions(force = true) } }

    suspend fun pollActions(force: Boolean = false) {
        val repo = selectedRepo ?: return
        if (screen != "detail" || detailTab != 2 || account == null) return
        if (!force && android.os.SystemClock.elapsedRealtime() < nextPollAt) return
        if (actionsMutex.isLocked) return
        actionsMutex.withLock {
            val version = contextVersion
            val runId = chosenRun?.id
            try {
                val result = coroutineScope {
                    val recent = async { if (runId == null) client.runs(repo) else emptyList() }
                    val workflow = async { if (runId == null && (force || android.os.SystemClock.elapsedRealtime() - lastWorkflowPoll > 60000L || lastWorkflowPoll == 0L)) client.workflows(repo) else workflows }
                    val run = async { runId?.let { client.run(repo, it) } }
                    val current = run.await()
                    val jobList = async { current?.let { client.jobs(repo, it) } ?: emptyList() }
                    val artifactList = async { current?.let { client.artifacts(repo, it) } ?: emptyList() }
                    ActionsResult(recent.await(), workflow.await(), current, jobList.await(), artifactList.await())
                }
                if (version != contextVersion || selectedRepo != repo || chosenRun?.id != runId || detailTab != 2 || screen != "detail") return@withLock
                if (runId == null) {
                    runs = result.runs; workflows = result.workflows
                    if (force || android.os.SystemClock.elapsedRealtime() - lastWorkflowPoll > 60000L || lastWorkflowPoll == 0L)
                        lastWorkflowPoll = android.os.SystemClock.elapsedRealtime()
                }
                else { chosenRun = result.run; jobs = result.jobs; artifacts = result.artifacts }
                val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                actionsStatus = "$now 已更新 · 前台每 4 秒检查"
                nextPollAt = android.os.SystemClock.elapsedRealtime() + 3500L
                if (logText != null && !logLoading && (logJobId != null || logRunId != null)) {
                    logTask = viewModelScope.launch { refreshLog() }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val wait = if (e is GitHubException && e.status in listOf(403, 429)) e.retryAfterSeconds.coerceAtLeast(60) * 1000 else 15000L
                nextPollAt = android.os.SystemClock.elapsedRealtime() + wait
                if (version == contextVersion) actionsStatus = "${e.message} · ${wait / 1000} 秒后重试"
            }
        }
    }

    private data class ActionsResult(val runs: List<Run>, val workflows: List<Workflow>, val run: Run?,
        val jobs: List<me.rain.git.github.Job>, val artifacts: List<Artifact>)

    fun refreshCollaboration() = launchAction {
        val r = selectedRepo ?: return@launchAction
        collaboration = client.collaboration(r, collaborationFilter)
    }

    fun newIssue(title: String, body: String) = launchAction {
        val r = selectedRepo ?: return@launchAction
        val number = client.createIssue(r, title, body)
        collaborationFilter = 1
        collaboration = client.collaboration(r, 1)
        message = "Issue #$number 已创建"
    }

    fun dispatch(workflow: Workflow, branch: String, inputs: String) = launchAction {
        client.dispatch(selectedRepo ?: return@launchAction, workflow, branch, inputs)
        message = "已提交工作流运行请求；GitHub 可能需要几秒才会显示运行记录"
        delay(900)
        runs = client.runs(selectedRepo ?: return@launchAction)
    }

    fun chooseRun(run: Run) {
        if (busy) return
        closeLog()
        chosenRun = run
        jobs = emptyList()
        artifacts = emptyList()
        nextPollAt = 0L
        refreshActions()
    }

    fun backRun() {
        closeLog()
        chosenRun = null
        jobs = emptyList()
        artifacts = emptyList()
        nextPollAt = 0L
        refreshActions()
    }

    fun controlRun(operation: String) = launchAction {
        val r = selectedRepo ?: return@launchAction
        val run = chosenRun ?: return@launchAction
        client.control(r, run, operation)
        message = "请求已提交：$operation"
        runs = client.runs(r)
        nextPollAt = 0L
        refreshActions()
    }

    fun openRunLogs(run: Run) {
        closeLog()
        logRunId = run.id
        logText = ""
        logTask = viewModelScope.launch { refreshLog(force = true) }
    }

    fun readJobLogs(jobId: Long) {
        closeLog()
        logJobId = jobId
        logText = ""
        logTask = viewModelScope.launch { refreshLog(force = true) }
    }

    fun readLogs() { chosenRun?.let(::openRunLogs) }
    fun refreshLogNow() { if (logLoading) return; logTask = viewModelScope.launch { refreshLog(force = true) } }

    private suspend fun refreshLog(force: Boolean = false) {
        if (logLoading) return
        val repo = selectedRepo ?: return
        val job = logJobId
        val run = logRunId
        if (job == null && run == null) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastLogPoll < 10000L) return
        val version = logVersion
        val complete = if (job != null) jobs.any { it.id == job && it.status == "completed" }
            else chosenRun?.takeIf { it.id == run }?.status == "completed" || runs.any { it.id == run && it.status == "completed" }
        if (!force && complete && logText?.isNotBlank() == true && logStatus == "完整日志已更新") return
        logLoading = true
        lastLogPoll = now
        var cache: File? = null
        try {
            val logFile = File.createTempFile("rain-log-", ".tmp", getApplication<Application>().cacheDir)
            cache = logFile
            val text = if (job != null) client.readableJobLogs(repo, job, logFile)
                else client.readableLogs(repo, client.run(repo, run!!), logFile)
            if (version == logVersion) {
                logText = text
                logStatus = if (complete) "完整日志已更新" else "已获取当前可用日志 · 每 10 秒检查"
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (e is GitHubException && e.status in listOf(403, 429)) lastLogPoll = now + e.retryAfterSeconds.coerceAtLeast(60) * 1000L
            if (version == logVersion) logStatus = if (e is GitHubException && e.status in listOf(404, 409) && !complete)
                "GitHub 尚未提供此任务的日志 · 自动等待，可在网页查看实时输出"
            else "${e.message} · 可手动重试"
        } finally { cache?.delete(); if (version == logVersion) logLoading = false }
    }

    fun initRepository(name: String) = launchAction {
        val dir = local { git.init(store.root(), name.trim()) }
        reloadLocal()
        message = "已初始化 ${dir.name}，打开后可设置 origin"
    }

    fun showCommit(id: String) = launchAction {
        val dir = selectedDir ?: return@launchAction
        diffText = local { git.commitDetails(dir, id) }
    }

    fun createFile(path: String) = launchAction {
        val dir = selectedDir ?: return@launchAction
        local { git.createFile(dir, path) }
        refreshLocalInner(dir)
        editorPath = path.trim()
        editorText = ""
    }

    fun renameFile(path: String, target: String) = launchAction {
        val dir = selectedDir ?: return@launchAction
        local { git.renameFile(dir, path, target) }
        refreshLocalInner(dir)
        message = "已重命名，提交后同步到远端"
    }

    fun deleteFile(path: String) = launchAction {
        val dir = selectedDir ?: return@launchAction
        local { git.deleteFile(dir, path) }
        refreshLocalInner(dir)
        message = "已删除本地文件，提交后同步到远端"
    }

    fun downloadArtifact(artifact: Artifact, apkOnly: Boolean) {
        if (downloading) { message = "已有产物正在下载"; return }
        val repo = selectedRepo ?: return
        val project = selectedDir ?: git.directory(store.root(), repo)
        if (busy) { message = "请等待当前 Git 操作完成"; return }
        downloading = true
        downloadJob = viewModelScope.launch {
            var archive: File? = null
            var extracted: File? = null
            try {
                require(!artifact.expired) { "构建产物已过期" }
                val dist = local { git.downloadDirectory(project) }
                archive = local { File.createTempFile(".rain-", ".part", dist) }
                val zipFile = archive!!
                downloadState = "正在获取下载链接…"
                client.download(repo, "/actions/artifacts/${artifact.id}/zip", zipFile) { done, total ->
                    withContext(Dispatchers.Main) {
                        val size = "%.1f".format(java.util.Locale.US, done / 1048576.0)
                        downloadState = if (total > 0) "下载 ${done * 100 / total}% · $size MiB" else "已下载 $size MiB"
                    }
                }
                downloadState = "正在校验并写入 dist…"
                val downloadContext = currentCoroutineContext()
                val savedFile = local {
                    java.util.zip.ZipFile(zipFile).use { zip ->
                        require(zip.size() > 0) { "下载到空归档" }
                        val cleanName = artifact.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "artifact" }
                        val suffix = if (apkOnly) ".apk" else ".zip"
                        var target = File(dist, "$cleanName-${artifact.id}$suffix")
                        var sequence = 1
                        while (target.exists()) { target = File(dist, "$cleanName-${artifact.id}-${sequence++}$suffix") }
                        if (apkOnly) {
                            val apks = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.toList()
                            require(apks.size == 1) { if (apks.isEmpty()) "产物没有 APK，请下载 ZIP" else "产物包含多个 APK，请下载 ZIP 后选择" }
                            val entry = apks.single()
                            require(entry.size in 1..(1024L * 1024 * 1024)) { "APK 大小不合法" }
                            val part = File.createTempFile(".rain-apk-", ".part", dist)
                            extracted = part
                            val crc = java.util.zip.CRC32()
                            var count = 0L
                            zip.getInputStream(entry).use { input ->
                                part.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(65536)
                                    while (true) {
                                        downloadContext.ensureActive()
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        count += n
                                        require(count <= 1024L * 1024 * 1024) { "APK 超过大小限制" }
                                        crc.update(buffer, 0, n)
                                        output.write(buffer, 0, n)
                                    }
                                }
                            }
                            require(count == entry.size && crc.value == entry.crc) { "APK 校验失败" }
                            java.util.zip.ZipFile(part).use { apk -> require(apk.getEntry("AndroidManifest.xml") != null) { "不是有效 APK" } }
                            downloadContext.ensureActive()
                            require(part.renameTo(target)) { "无法完成 APK 保存" }
                        } else {
                            downloadContext.ensureActive()
                            require(zipFile.renameTo(target)) { "无法完成 ZIP 保存" }
                        }
                        target
                    }
                }
                if (selectedRepo == repo) lastDownloadedPath = savedFile.absolutePath
                message = "已下载至 ${savedFile.absolutePath}"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "下载失败" }
            finally {
                archive?.delete(); extracted?.delete()
                downloadState = null
                downloading = false
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel(); message = "已取消下载" }
}

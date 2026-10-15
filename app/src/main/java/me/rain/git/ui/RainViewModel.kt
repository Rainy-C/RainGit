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
import me.rain.git.auth.SecureStore
import me.rain.git.github.*
import me.rain.git.local.LocalGit
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

class RainViewModel(application: Application) : AndroidViewModel(application) {
    val store = SecureStore(application)
    private val client = GitHubClient { store.token() }
    private val git = LocalGit { store.token() }
    private var activeJob: Job? = null

    var account by androidx.compose.runtime.mutableStateOf<Account?>(null)
        private set
    var repos by androidx.compose.runtime.mutableStateOf<List<RemoteRepo>>(emptyList())
        private set
    var localDirs by androidx.compose.runtime.mutableStateOf<List<File>>(emptyList())
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
    var device by androidx.compose.runtime.mutableStateOf<DeviceCode?>(null)
        private set
    var codeText by androidx.compose.runtime.mutableStateOf<String?>(null)
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
        if (store.token() != null) refreshAccount()
    }

    fun clearMessage() { message = null }
    fun closeLog() { logText = null }
    fun closeDiff() { diffText = null }
    fun closeEditor() { editorPath = null }
    fun editText(text: String) { editorText = text }
    fun navigate(page: String) { screen = page }
    fun selectDetailTab(tab: Int) {
        detailTab = tab
        if (tab == 2 && selectedRepo != null) refreshActions()
    }
    fun closeDetail() {
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
                if (device != null && account == null) { device = null; codeText = null }
                message = e.message ?: "操作失败"
            }
            finally { busy = false }
        }
    }

    private suspend fun <T> local(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    fun refreshAccount() = launchAction {
        account = client.me()
        if (store.author.isBlank()) store.author = account!!.login
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
        if (store.author.isBlank()) store.author = verified.login
        refreshReposInner()
        message = "已登录 ${verified.login}"
    }

    fun startOAuth(clientId: String) = launchAction {
        store.oauthClientId = clientId
        device = client.startDeviceFlow(clientId.trim())
        codeText = device?.userCode
        message = "请在 GitHub 页面输入设备验证码，完成登录与 2FA"
        val pending = device ?: return@launchAction
        val deadline = System.currentTimeMillis() + pending.expires * 1000L
        var interval = pending.interval.toLong() * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            val value = try {
                client.pollDevice(clientId.trim(), pending)
            } catch (_: SlowDownException) {
                interval += 5000
                null
            }
            if (value != null) {
                val verified = GitHubClient { value }.me()
                store.saveToken(value)
                account = verified
                if (store.author.isBlank()) store.author = verified.login
                device = null
                codeText = null
                refreshReposInner()
                message = "GitHub 授权成功"
                return@launchAction
            }
        }
        device = null
        codeText = null
        throw IOException("验证码过期，请重新授权")
    }

    fun cancelOAuth() {
        if (device != null) {
            activeJob?.cancel()
            device = null
            codeText = null
            busy = false
        }
    }

    fun logout() {
        activeJob?.cancel()
        busy = false
        store.logout()
        account = null
        repos = emptyList()
        selectedRepo = null
        selectedDir = null
        snapshot = null
        workflows = emptyList()
        runs = emptyList()
        device = null
        screen = "home"
        message = "已退出账号"
    }

    fun reloadLocal() {
        localDirs = runCatching { store.root().listFiles()?.filter { git.isRepo(it) }?.sortedBy { it.name } ?: emptyList() }
            .getOrDefault(emptyList())
    }

    fun chooseRoot(uri: Uri) = launchAction {
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
        store.resetRoot()
        reloadLocal()
        message = "已切换至应用专属目录"
    }

    fun openRemote(repo: RemoteRepo, tab: Int = 0) {
        selectedRepo = repo
        selectedDir = git.directory(store.root(), repo).takeIf { git.isRepo(it) }
        snapshot = null
        files = emptyList()
        chosenRun = null
        jobs = emptyList()
        artifacts = emptyList()
        workflows = emptyList()
        runs = emptyList()
        detailTab = tab
        screen = "detail"
        if (tab == 2) refreshActions() else if (selectedDir != null) refreshLocal()
    }

    fun openLocal(dir: File) {
        require(git.isRepo(dir))
        selectedDir = dir
        selectedRepo = repos.firstOrNull { git.directory(store.root(), it).canonicalFile == dir.canonicalFile }
        snapshot = null
        files = emptyList()
        chosenRun = null
        workflows = emptyList()
        runs = emptyList()
        detailTab = 0
        screen = "detail"
        refreshLocal()
    }

    fun clone() = launchAction {
        val repo = selectedRepo ?: throw IOException("请先选择远程仓库")
        val dir = local { git.clone(repo, store.root()) }
        selectedDir = dir
        refreshLocalInner(dir)
        reloadLocal()
        message = "已克隆到 ${dir.path}"
    }

    private suspend fun refreshLocalInner(dir: File) {
        local {
            snapshot = git.snapshot(dir)
            files = git.files(dir)
        }
    }

    fun refreshLocal() = launchAction {
        val dir = selectedDir ?: return@launchAction
        refreshLocalInner(dir)
    }

    fun gitOperation(name: String, value: String = "") = launchAction {
        val dir = selectedDir ?: throw IOException("请先 Clone 仓库")
        val result = withContext(Dispatchers.IO) {
            when (name) {
                "fetch" -> git.fetch(dir)
                "pull" -> git.pull(dir)
                "push" -> git.push(dir)
                "stage" -> { git.stage(dir, value); "已暂存" }
                "unstage" -> { git.unstage(dir, value); "已取消暂存" }
                "commit" -> "提交成功 ${git.commit(dir, value, store.author, store.email)}"
                "branch" -> { git.createBranch(dir, value); "已创建分支 $value" }
                "checkout" -> { git.checkout(dir, value); "已切换至 $value" }
                "stash" -> "Stash ${git.stash(dir)}"
                "apply" -> "Stash Apply ${git.applyStash(dir)}"
                else -> throw IOException("不支持的 Git 操作")
            }
        }
        refreshLocalInner(dir)
        message = result
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

    fun refreshActions() = launchAction {
        val repo = selectedRepo ?: throw IOException("此本地仓库没有匹配的 GitHub 仓库")
        workflows = client.workflows(repo)
        runs = client.runs(repo)
    }

    fun dispatch(workflow: Workflow, branch: String, inputs: String) = launchAction {
        client.dispatch(selectedRepo ?: return@launchAction, workflow, branch, inputs)
        message = "已提交工作流运行请求；GitHub 可能需要几秒才会显示运行记录"
        delay(900)
        runs = client.runs(selectedRepo ?: return@launchAction)
    }

    fun chooseRun(run: Run) = launchAction {
        chosenRun = run
        jobs = emptyList()
        artifacts = emptyList()
        val r = selectedRepo ?: return@launchAction
        jobs = client.jobs(r, run)
        artifacts = client.artifacts(r, run)
    }

    fun backRun() {
        chosenRun = null
        jobs = emptyList()
        artifacts = emptyList()
    }

    fun controlRun(operation: String) = launchAction {
        val r = selectedRepo ?: return@launchAction
        val run = chosenRun ?: return@launchAction
        client.control(r, run, operation)
        message = "请求已提交：$operation"
        runs = client.runs(r)
    }

    fun readLogs() = launchAction {
        val r = selectedRepo ?: return@launchAction
        val run = chosenRun ?: return@launchAction
        val cache = File(getApplication<Application>().cacheDir, "actions-log-${run.id}.zip")
        logText = client.readableLogs(r, run, cache)
    }

    fun saveArtifact(artifact: Artifact, target: Uri, apkOnly: Boolean = false) = launchAction {
        if (artifact.expired) throw IOException("此 Artifact 已过期")
        val r = selectedRepo ?: return@launchAction
        val cache = File.createTempFile("rain-artifact-", ".zip", getApplication<Application>().cacheDir)
        try {
            client.download(r, "/actions/artifacts/${artifact.id}/zip", cache)
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(target, "w")?.use { out ->
                    if (!apkOnly) {
                        cache.inputStream().use { it.copyTo(out) }
                    } else {
                        var found = false
                        ZipInputStream(cache.inputStream().buffered()).use { zip ->
                            var item = zip.nextEntry
                            while (item != null) {
                                if (!item.isDirectory && item.name.endsWith(".apk", ignoreCase = true)) {
                                    val buffer = ByteArray(64 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val size = zip.read(buffer)
                                        if (size == -1) break
                                        total += size
                                        if (total > 1024L * 1024 * 1024) throw IOException("APK 大于 1 GB")
                                        out.write(buffer, 0, size)
                                    }
                                    found = true
                                    break
                                }
                                zip.closeEntry()
                                item = zip.nextEntry
                            }
                        }
                        if (!found) throw IOException("该 Artifact 中没有 APK 文件")
                    }
                } ?: throw IOException("无法写入目标文件")
            }
            message = if (apkOnly) "APK 已保存：${artifact.name}" else "Artifact ZIP 已保存：${artifact.name}"
        } finally { cache.delete() }
    }
}

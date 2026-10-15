package me.rain.git.local

import me.rain.git.github.RemoteRepo
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class LocalGit(private val token: () -> String?) {
    private fun auth(): CredentialsProvider = UsernamePasswordCredentialsProvider(
        "x-access-token", token() ?: throw IOException("GitHub 登录已失效")
    )

    fun directory(root: File, repo: RemoteRepo): File {
        require(Regex("[A-Za-z0-9_.-]+").matches(repo.owner) && Regex("[A-Za-z0-9_.-]+").matches(repo.name))
        return File(root, "${repo.owner}--${repo.name}")
    }

    fun isRepo(dir: File) = dir.isDirectory && (File(dir, ".git").exists() || File(dir, "HEAD").exists())

    fun clone(repo: RemoteRepo, root: File): File {
        val dir = directory(root, repo)
        require(!dir.exists() || dir.listFiles().isNullOrEmpty()) { "目标文件夹已存在，请直接打开本地仓库" }
        try {
            Git.cloneRepository().setURI(repo.cloneUrl).setDirectory(dir)
                .setCredentialsProvider(auth()).setCloneAllBranches(true).call().use { }
        } catch (e: Exception) {
            if (dir.exists() && !isRepo(dir)) dir.deleteRecursively()
            throw e
        }
        return dir
    }

    data class Snapshot(
        val branch: String, val staged: List<String>, val changed: List<String>,
        val untracked: List<String>, val deleted: List<String>, val conflicts: List<String>,
        val history: List<String>, val branches: List<String>,
        val ahead: Int? = null, val behind: Int? = null
    )

    fun snapshot(dir: File): Snapshot = Git.open(dir).use { git ->
        val status = git.status().call()
        val tracking = runCatching { BranchTrackingStatus.of(git.repository, git.repository.branch) }.getOrNull()
        Snapshot(
            git.repository.branch ?: "DETACHED",
            (status.added + status.changed + status.removed).sorted(),
            status.modified.sorted(), status.untracked.sorted(), status.missing.sorted(),
            status.conflicting.sorted(),
            git.log().setMaxCount(25).call().map { "${it.name.take(8)}  ${it.shortMessage}" },
            git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
                .map { it.name.removePrefix("refs/heads/").removePrefix("refs/remotes/") }.sorted(),
            tracking?.aheadCount, tracking?.behindCount
        )
    }

    fun fetch(dir: File) = Git.open(dir).use { git ->
        git.fetch().setCredentialsProvider(auth()).call()
        "Fetch 完成"
    }

    fun pull(dir: File): String = Git.open(dir).use { git ->
        val result = git.pull().setCredentialsProvider(auth()).call()
        if (!result.isSuccessful) throw IOException("Pull 未成功，请检查合并冲突")
        "Pull 成功"
    }

    fun push(dir: File): String = Git.open(dir).use { git ->
        val results = git.push().setCredentialsProvider(auth()).call().toList()
        if (results.isEmpty()) throw IOException("未找到可用 Push Remote")
        val failed = results.flatMap { it.remoteUpdates }
            .filter { it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK &&
                it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE }
        if (failed.isNotEmpty()) throw IOException(failed.joinToString("\n") { "${it.remoteName}: ${it.status} ${it.message.orEmpty()}" })
        "Push 完成"
    }

    fun stage(dir: File, path: String): Unit = Git.open(dir).use { git ->
        if (path == ".") {
            git.add().addFilepattern(".").call()
            git.add().setUpdate(true).addFilepattern(".").call()
        } else {
            if (File(dir, path).exists()) git.add().addFilepattern(path).call()
            else git.add().setUpdate(true).addFilepattern(path).call()
        }
    }

    fun unstage(dir: File, path: String): Unit = Git.open(dir).use { git ->
        git.reset().addPath(path).call()
    }

    fun commit(dir: File, message: String, name: String, email: String): String = Git.open(dir).use { git ->
        require(message.isNotBlank()) { "提交说明不能为空" }
        require(name.isNotBlank() && email.contains('@')) { "请在设置中填写 Git 作者和邮箱" }
        git.commit().setMessage(message.trim()).setAuthor(name.trim(), email.trim())
            .setCommitter(name.trim(), email.trim()).call().name.take(10)
    }

    fun createBranch(dir: File, branch: String): Unit = Git.open(dir).use { git ->
        require(Regex("[A-Za-z0-9][A-Za-z0-9._/-]*").matches(branch) && !branch.contains("..") && !branch.endsWith('/'))
        git.branchCreate().setName(branch).call()
    }

    fun checkout(dir: File, branch: String): Unit = Git.open(dir).use { git ->
        require(git.status().call().isClean) { "切换分支前请提交或暂存当前修改" }
        val locals = git.branchList().call().map { it.name.removePrefix(Constants.R_HEADS) }
        if (branch in locals) git.checkout().setName(branch).call()
        else {
            require(branch.startsWith("origin/")) { "未找到可切换的本地分支" }
            val short = branch.removePrefix("origin/")
            git.checkout().setCreateBranch(true).setName(short).setStartPoint(branch)
                .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK).call()
        }
    }

    fun stash(dir: File): String = Git.open(dir).use { git ->
        git.stashCreate().setIncludeUntracked(true).call()?.name?.take(10) ?: "没有可暂存的变更"
    }

    fun applyStash(dir: File): String = Git.open(dir).use { git ->
        git.stashApply().call()?.name?.take(10) ?: "没有 Stash"
    }

    fun diff(dir: File, cached: Boolean): String = Git.open(dir).use { git ->
        val out = ByteArrayOutputStream()
        DiffFormatter(out).use { formatter ->
            formatter.setRepository(git.repository)
            formatter.setDetectRenames(true)
            git.diff().setCached(cached).call().forEach { entry ->
                if (out.size() < 600_000) formatter.format(entry)
            }
        }
        out.toString("UTF-8").take(600_000).ifBlank { "暂无 Diff" }
    }

    fun files(dir: File): List<String> {
        val base = dir.canonicalFile
        return base.walkTopDown()
            .onEnter { it.name != ".git" }
            .filter { it.isFile && it.length() <= 512L * 1024 }
            .take(1200)
            .map { it.relativeTo(base).invariantSeparatorsPath }
            .sorted().toList()
    }

    private fun within(root: File, relative: String): File {
        require(relative.isNotBlank() && !relative.startsWith('/'))
        val base = root.canonicalFile
        val candidate = File(base, relative).canonicalFile
        require(candidate.path.startsWith(base.path + File.separator)) { "非法文件路径" }
        return candidate
    }

    fun read(dir: File, path: String): String {
        val file = within(dir, path)
        require(file.isFile && file.length() <= 512L * 1024) { "文件不存在或超过 512 KB" }
        val bytes = file.readBytes()
        require(bytes.none { it == 0.toByte() }) { "不支持编辑二进制文件" }
        return bytes.toString(Charsets.UTF_8)
    }

    fun write(dir: File, path: String, content: String) {
        require(content.toByteArray(Charsets.UTF_8).size <= 512 * 1024) { "文本文件不能超过 512 KB" }
        val file = within(dir, path)
        require(file.isFile && file.canWrite()) { "文件不可写" }
        file.writeText(content, Charsets.UTF_8)
    }
}

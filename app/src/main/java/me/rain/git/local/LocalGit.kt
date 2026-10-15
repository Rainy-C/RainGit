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

    fun isRepo(dir: File) = dir.isDirectory && File(dir, ".git").exists()

    fun clone(repo: RemoteRepo, root: File): File {
        val dir = directory(root, repo)
        require(!isRepo(dir)) { "仓库已存在，请直接打开" }
        require(!dir.exists() || dir.listFiles()?.all { it.name == "dist" } == true) { "目标目录包含其他文件" }
        val temporary = File(root, ".rain-clone-${java.util.UUID.randomUUID()}")
        try {
            Git.cloneRepository().setURI(repo.cloneUrl).setDirectory(temporary)
                .setCredentialsProvider(auth()).setCloneAllBranches(true).call().use { }
            val oldDist = File(dir, "dist")
            require(!oldDist.exists() || !File(temporary, "dist").exists()) {
                "远端仓库包含 dist，请先移动本地下载目录后重试"
            }
            excludeDist(temporary)
            val backup = File(root, ".rain-downloads-${java.util.UUID.randomUUID()}")
            val hadDirectory = dir.exists()
            if (hadDirectory) require(dir.renameTo(backup)) { "无法保留已有下载目录" }
            if (!temporary.renameTo(dir)) {
                if (hadDirectory && !backup.renameTo(dir)) throw IOException("克隆未完成，原下载文件保留在 ${backup.path}")
                throw IOException("无法完成仓库克隆")
            }
            val preservedDist = File(backup, "dist")
            if (preservedDist.exists() && !preservedDist.renameTo(File(dir, "dist"))) {
                throw IOException("仓库已克隆，原下载文件保留在 ${preservedDist.path}，请手动移入 dist")
            }
            if (hadDirectory) backup.delete()
            return dir
        } finally { temporary.deleteRecursively() }
    }

    data class Snapshot(
        val branch: String, val staged: List<String>, val changed: List<String>,
        val untracked: List<String>, val deleted: List<String>, val conflicts: List<String>,
        val history: List<String>, val branches: List<String>,
        val ahead: Int? = null, val behind: Int? = null,
        val stashes: List<StashEntry> = emptyList(), val tags: List<String> = emptyList()
    )

    data class StashEntry(val id: String, val name: String)

    fun snapshot(dir: File): Snapshot = Git.open(dir).use { git ->
        val status = git.status().call()
        val tracking = runCatching { BranchTrackingStatus.of(git.repository, git.repository.branch) }.getOrNull()
        Snapshot(
            git.repository.branch ?: "DETACHED",
            (status.added + status.changed + status.removed).sorted(),
            status.modified.sorted(), status.untracked.sorted(), status.missing.sorted(),
            status.conflicting.sorted(),
            if (git.repository.resolve("HEAD") == null) emptyList() else git.log().setMaxCount(40).call().map { "${it.name}  ${it.shortMessage}" },
            git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
                .map { it.name.removePrefix("refs/heads/").removePrefix("refs/remotes/") }.sorted(),
            tracking?.aheadCount, tracking?.behindCount,
            git.stashList().call().map { StashEntry(it.name, it.shortMessage) },
            git.tagList().call().map { it.name.removePrefix("refs/tags/") }
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
        val branch = git.repository.fullBranch
        require(branch != null && branch.startsWith(Constants.R_HEADS)) { "请先切换到本地分支" }
        val results = git.push().setRemote("origin").setRefSpecs(org.eclipse.jgit.transport.RefSpec("$branch:$branch"))
            .setCredentialsProvider(auth()).call().toList()
        if (results.isEmpty()) throw IOException("未找到可用 Push Remote")
        val failed = results.flatMap { it.remoteUpdates }
            .filter { it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK &&
                it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE }
        if (failed.isNotEmpty()) throw IOException(failed.joinToString("\n") { "${it.remoteName}: ${it.status} ${it.message.orEmpty()}" })
        git.repository.config.apply {
            val name = branch.removePrefix(Constants.R_HEADS)
            setString("branch", name, "remote", "origin")
            setString("branch", name, "merge", branch)
            save()
        }
        "Push 完成"
    }

    fun stage(dir: File, path: String): Unit = Git.open(dir).use { git ->
        excludeDist(dir)
        require(path != "dist" && !path.startsWith("dist/")) { "dist 是下载专用目录，不能暂存" }
        val status = git.status().call()
        val paths = if (path == ".") (status.modified + status.untracked + status.missing + status.conflicting)
            .filterNot { it == "dist" || it.startsWith("dist/") }
            else listOf(path)
        val present = paths.filter { File(dir, it).exists() }
        val missing = paths.filterNot { File(dir, it).exists() }
        if (present.isNotEmpty()) {
            val command = git.add()
            present.forEach { command.addFilepattern(it) }
            command.call()
        }
        if (missing.isNotEmpty()) {
            val command = git.add().setUpdate(true)
            missing.forEach { command.addFilepattern(it) }
            command.call()
        }
    }

    fun unstage(dir: File, path: String): Unit = Git.open(dir).use { git ->
        if (git.repository.resolve("HEAD") == null) git.rm().setCached(true).addFilepattern(path).call()
        else git.reset().addPath(path).call()
    }

    fun unstageAll(dir: File): Unit = Git.open(dir).use { git ->
        if (git.repository.resolve("HEAD") == null) {
            val index = git.repository.readDirCache()
            val paths = (0 until index.entryCount).map { index.getEntry(it).pathString }
            if (paths.isNotEmpty()) { val command = git.rm().setCached(true); paths.forEach { command.addFilepattern(it) }; command.call() }
        } else git.reset().call()
    }

    fun discard(dir: File, path: String): Unit = Git.open(dir).use { git ->
        require(path.isNotBlank() && path != "." && !path.startsWith('/') &&
            path.split('/').none { it == ".." }) { "非法文件路径" }
        val status = git.status().call()
        require(path !in status.untracked && path !in status.conflicting) {
            "新增文件或冲突文件不能直接撤销，请手动处理"
        }
        require(path in status.modified || path in status.missing) { "文件没有可撤销的未暂存修改" }
        git.checkout().addPath(path).call()
    }

    fun createTag(dir: File, tag: String): Unit = Git.open(dir).use { git ->
        require(org.eclipse.jgit.lib.Repository.isValidRefName("refs/tags/$tag")) { "标签名称不合法" }
        git.tag().setName(tag).setMessage("Release $tag").call()
    }

    fun pushTags(dir: File): String = Git.open(dir).use { git ->
        val results = git.push().setPushTags().setCredentialsProvider(auth()).call()
        val failed = results.flatMap { it.remoteUpdates }.filter {
            it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK &&
                it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE
        }
        if (failed.isNotEmpty()) throw IOException(failed.joinToString("; ") { "${it.remoteName}: ${it.status}" })
        "标签已推送"
    }

    fun commit(dir: File, message: String, name: String, email: String): String = Git.open(dir).use { git ->
        require(message.isNotBlank()) { "提交说明不能为空" }
        require(name.isNotBlank() && email.contains('@')) { "请在设置中填写 Git 作者和邮箱" }
        val status = git.status().call()
        require((status.added + status.changed + status.removed).none { it == "dist" || it.startsWith("dist/") }) {
            "dist 中存在已暂存文件，请先取消这些文件的暂存"
        }
        git.commit().setMessage(message.trim()).setAuthor(name.trim(), email.trim())
            .setCommitter(name.trim(), email.trim()).call().name.take(10)
    }

    fun createBranch(dir: File, branch: String): Unit = Git.open(dir).use { git ->
        require(org.eclipse.jgit.lib.Repository.isValidRefName("refs/heads/$branch")) { "分支名称不合法" }
        git.branchCreate().setName(branch).call()
    }

    fun checkout(dir: File, branch: String): Unit = Git.open(dir).use { git ->
        require(git.status().call().isClean) { "切换分支前请提交修改或保存到 Stash" }
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
        excludeDist(dir)
        require(git.status().call().untracked.none { it == "dist" || it.startsWith("dist/") }) {
            "仓库忽略规则重新包含了 dist，请先移除针对 dist 的反向规则"
        }
        git.stashCreate().setIncludeUntracked(true).call()?.name?.take(10) ?: "没有可暂存的变更"
    }

    fun applyStash(dir: File): String = Git.open(dir).use { git ->
        require(git.status().call().isClean) { "恢复前请提交修改或保存 Stash" }
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
            .onEnter { it.name != ".git" && (it == base || it != File(base, "dist")) && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) && it.length() <= 512L * 1024 }
            .take(1200)
            .map { it.relativeTo(base).invariantSeparatorsPath }
            .sorted().toList()
    }

    private fun within(root: File, relative: String): File {
        require(relative.isNotBlank() && !relative.startsWith('/'))
        require(relative.split('/').none { it == ".." || it.equals(".git", true) } && relative != "dist" && !relative.startsWith("dist/")) { "该路径受保护" }
        val base = root.canonicalFile
        val candidate = File(base, relative).canonicalFile
        require(candidate.path.startsWith(base.path + File.separator)) { "非法文件路径" }
        val actual = candidate.relativeTo(base).invariantSeparatorsPath
        require(actual.split('/').none { it.equals(".git", true) } && actual != "dist" && !actual.startsWith("dist/")) { "该路径受保护" }
        require(!java.nio.file.Files.isSymbolicLink(File(base, relative).toPath())) { "不能编辑符号链接" }
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

    fun remoteUrl(dir: File): String = Git.open(dir).use { it.repository.config.getString("remote", "origin", "url").orEmpty() }

    fun configureAuthor(dir: File, name: String, email: String): Unit = Git.open(dir).use { git ->
        if (name.isNotBlank() && email.contains('@')) git.repository.config.apply {
            setString("user", null, "name", name.trim())
            setString("user", null, "email", email.trim())
            save()
        }
    }

    fun excludeDist(dir: File) {
        if (!isRepo(dir)) return
        Git.open(dir).use { git ->
            val exclude = File(git.repository.directory, "info/exclude")
            require(exclude.parentFile.isDirectory || exclude.parentFile.mkdirs()) { "无法创建 Git 排除目录" }
            val old = if (exclude.exists()) exclude.readText() else ""
            if (old.lineSequence().none { it.trim() == "/dist/" }) exclude.appendText("\n/dist/\n")
        }
    }

    fun downloadDirectory(dir: File): File {
        require(dir.isDirectory || dir.mkdirs()) { "项目目录不可写" }
        if (isRepo(dir)) {
            Git.open(dir).use { git ->
                val index = git.repository.readDirCache()
                require((0 until index.entryCount).none { index.getEntry(it).pathString.let { p -> p == "dist" || p.startsWith("dist/") } }) {
                    "该仓库已跟踪 dist，下载已停止。请先将 dist 从 Git 索引中移除。"
                }
            }
            excludeDist(dir)
        }
        val target = File(dir, "dist")
        require(!java.nio.file.Files.isSymbolicLink(target.toPath())) { "dist 不能是符号链接" }
        require(target.isDirectory || target.mkdirs()) { "无法创建 dist" }
        require(target.canonicalFile.parentFile == dir.canonicalFile) { "dist 路径异常" }
        return target
    }

    fun init(root: File, name: String): File {
        require(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}").matches(name)) { "名称仅支持字母、数字、点、横线和下划线" }
        val dir = File(root, name)
        require(!dir.exists()) { "该目录已存在" }
        Git.init().setDirectory(dir).setInitialBranch("main").call().use { }
        excludeDist(dir)
        return dir
    }

    fun setRemote(dir: File, url: String) = Git.open(dir).use { git ->
        require(Regex("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?").matches(url)) { "请填写 GitHub HTTPS 仓库地址" }
        git.repository.config.apply {
            setString("remote", "origin", "url", url)
            setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
            save()
        }
    }

    fun merge(dir: File, branch: String): String = Git.open(dir).use { git ->
        require(git.status().call().isClean) { "合并前请提交修改或保存 Stash" }
        val ref = git.repository.findRef(branch) ?: throw IOException("分支不存在")
        val result = git.merge().include(ref).call()
        if (!result.mergeStatus.isSuccessful) throw IOException("合并状态：${result.mergeStatus}；请处理冲突后提交，或中止合并")
        "合并完成：$branch"
    }

    fun abortMerge(dir: File): Unit = Git.open(dir).use { git ->
        require(git.repository.repositoryState == org.eclipse.jgit.lib.RepositoryState.MERGING ||
            git.repository.repositoryState == org.eclipse.jgit.lib.RepositoryState.MERGING_RESOLVED) { "当前没有进行中的合并" }
        git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).setRef("HEAD").call()
    }

    fun deleteBranch(dir: File, branch: String): Unit = Git.open(dir).use { git ->
        require(branch != git.repository.branch) { "不能删除当前分支" }
        require(git.branchList().call().any { it.name == "refs/heads/$branch" }) { "只支持删除本地分支" }
        git.branchDelete().setBranchNames(branch).setForce(false).call()
    }

    fun restoreStash(dir: File, id: String): Unit = Git.open(dir).use { git ->
        require(git.status().call().isClean) { "恢复前请提交当前修改或保存 Stash" }
        require(git.stashList().call().any { it.name == id }) { "Stash 不存在" }
        git.stashApply().setStashRef(id).call()
    }

    fun dropStash(dir: File, id: String): Unit = Git.open(dir).use { git ->
        val index = git.stashList().call().toList().indexOfFirst { it.name == id }
        require(index >= 0) { "Stash 不存在" }
        git.stashDrop().setStashRef(index).call()
    }

    fun commitDetails(dir: File, id: String): String = Git.open(dir).use { git ->
        org.eclipse.jgit.revwalk.RevWalk(git.repository).use { walk ->
            val commit = walk.parseCommit(git.repository.resolve(id) ?: throw IOException("提交不存在"))
            val output = ByteArrayOutputStream()
            DiffFormatter(output).use { formatter ->
                formatter.setRepository(git.repository)
                val newTree = org.eclipse.jgit.treewalk.CanonicalTreeParser()
                git.repository.newObjectReader().use { reader ->
                    newTree.reset(reader, commit.tree)
                    val oldTree: org.eclipse.jgit.treewalk.AbstractTreeIterator = if (commit.parentCount == 0) {
                        org.eclipse.jgit.treewalk.EmptyTreeIterator()
                    } else org.eclipse.jgit.treewalk.CanonicalTreeParser().apply { reset(reader, walk.parseCommit(commit.getParent(0)).tree) }
                    formatter.scan(oldTree, newTree).forEach { if (output.size() < 600_000) formatter.format(it) }
                }
            }
            "${commit.name}\n${commit.authorIdent.name} <${commit.authorIdent.emailAddress}>\n\n${commit.fullMessage}\n\n${output.toString("UTF-8").take(600_000)}"
        }
    }

    fun createFile(dir: File, path: String) {
        val target = within(dir, path.trim())
        require(!target.exists()) { "文件已存在" }
        require(target.parentFile.isDirectory || target.parentFile.mkdirs()) { "无法创建目录" }
        require(target.createNewFile()) { "无法创建文件" }
    }

    fun renameFile(dir: File, path: String, destination: String) {
        val old = within(dir, path)
        val target = within(dir, destination.trim())
        require(old.isFile && !target.exists()) { "原文件不存在或目标已存在" }
        require(target.parentFile.isDirectory || target.parentFile.mkdirs()) { "无法创建目录" }
        require(old.renameTo(target)) { "重命名失败" }
    }

    fun deleteFile(dir: File, path: String) {
        val target = within(dir, path)
        require(target.isFile && target.delete()) { "无法删除文件" }
    }
}

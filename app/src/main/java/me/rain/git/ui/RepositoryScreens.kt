package me.rain.git.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rain.git.github.Artifact
import me.rain.git.github.RemoteRepo

@Composable
internal fun RemoteScreen(vm: RainViewModel, actionsOnly: Boolean) {
    var search by remember { mutableStateOf("") }
    var filter by remember(actionsOnly) { mutableIntStateOf(0) }
    val localNames = remember(vm.localDirs) { vm.localDirs.map { it.name }.toSet() }
    val filtered = remember(vm.repos, vm.favoriteRepos, localNames, search, filter) {
        vm.repos.filter { repo ->
            val present = "${repo.owner}--${repo.name}" in localNames
            (repo.fullName.contains(search, true) || repo.description.contains(search, true)) && when (filter) {
                1 -> repo.fullName in vm.favoriteRepos
                2 -> present
                3 -> repo.privateRepo
                else -> true
            }
        }.sortedWith(compareByDescending<RemoteRepo> { it.fullName in vm.favoriteRepos }.thenByDescending { "${it.owner}--${it.name}" in localNames }.thenByDescending { it.updatedAt })
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            SectionHeading(
                if (actionsOnly) "选择构建仓库" else "你的仓库",
                if (actionsOnly) "直达工作流、构建日志与 APK" else "${vm.repos.size} 个远程仓库 · ${vm.localDirs.size} 个本地仓库"
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("搜索名称或描述") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (search.isNotEmpty()) {{ IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "清空") } }} else null,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(7.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("全部", "收藏", "已克隆", "私有").forEachIndexed { index, name ->
                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(name) })
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.fullName }) { repo ->
                val local = vm.localDirs.any { it.name == "${repo.owner}--${repo.name}" }
                RepositoryTile(vm, repo, local, actionsOnly)
            }
            if (filtered.isEmpty()) item {
                EmptyPanel(Icons.Rounded.FolderOff, "这里还没有仓库", if (search.isNotBlank() || filter != 0) "尝试更换搜索条件" else "点击右上角刷新同步 GitHub 仓库")
            }
        }
    }
}

@Composable
private fun RepositoryTile(vm: RainViewModel, repo: RemoteRepo, local: Boolean, actionsOnly: Boolean) {
    Card(
        onClick = { vm.openRemote(repo, if (actionsOnly) 2 else if (local) 1 else 0) }, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(21.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Icon(if (actionsOnly) Icons.Rounded.Terminal else Icons.Rounded.Folder, null,
                        Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(repo.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(repo.owner, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.toggleFavorite(repo.fullName) }) {
                    Icon(if (repo.fullName in vm.favoriteRepos) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "收藏仓库", tint = if (repo.fullName in vm.favoriteRepos) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (repo.description.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(repo.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StateLabel(repo.defaultBranch)
                if (repo.privateRepo) StateLabel("私有", MaterialTheme.colorScheme.secondary)
                if (local) StateLabel("已克隆")
                Spacer(Modifier.weight(1f))
                Text(repo.updatedAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (actionsOnly) {
                    FilledTonalButton(onClick = { vm.openRemote(repo, 2) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Terminal, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp)); Text("构建记录")
                    }
                } else {
                    TextButton(onClick = { vm.openRemote(repo, 0) }, modifier = Modifier.weight(1f)) { Text("文件") }
                    if (local) {
                        FilledTonalButton(onClick = { vm.openRemote(repo, 1) }, modifier = Modifier.weight(1f)) { Text("发布") }
                    } else {
                        FilledTonalButton(onClick = { vm.openRemote(repo, 0); vm.clone() }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("克隆") }
                    }
                    TextButton(onClick = { vm.openRemote(repo, 2) }, modifier = Modifier.weight(1f)) { Text("构建") }
                }
            }
        }
    }
}

@Composable
internal fun LocalScreen(vm: RainViewModel) {
    var search by remember { mutableStateOf("") }
    val dirs = remember(vm.localDirs, search) { vm.localDirs.filter { it.name.contains(search, true) } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            SectionHeading("离线也能工作", "${vm.localDirs.size} 个已克隆仓库")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(search, { search = it }, placeholder = { Text("筛选本地仓库") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dirs, key = { it.absolutePath }) { dir ->
                Surface(onClick = { vm.openLocal(dir) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(17.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(dir.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(dir.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
            if (dirs.isEmpty()) item { EmptyPanel(Icons.Rounded.FolderOpen, "没有匹配的本地仓库", "从仓库页克隆后即可离线编辑") }
        }
    }
}

@Composable
internal fun SettingsScreen(vm: RainViewModel, pickDir: () -> Unit, grant: () -> Unit) {
    var author by remember { mutableStateOf(vm.store.author) }
    var email by remember { mutableStateOf(vm.store.email) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp)) {
                SectionHeading("提交身份", "Git Commit 的作者信息")
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(author, { author = it }, label = { Text("作者名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(email, { email = it }, label = { Text("邮箱地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.saveAuthor(author, email) }, modifier = Modifier.fillMaxWidth()) { Text("保存身份") }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp)) {
                SectionHeading("本地工作目录", "克隆仓库的保存位置")
                Spacer(Modifier.height(9.dp))
                Text(vm.store.root().path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = pickDir, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("更换目录") }
                TextButton(onClick = grant) { Text("授权访问公共目录") }
                TextButton(onClick = vm::usePrivateRoot) { Text("恢复应用专属目录") }
                Text("Android 公共目录需要系统授予“所有文件访问权限”。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("退出 GitHub 账号") }
    }
}

@Composable
internal fun RepositoryScreen(vm: RainViewModel, export: (Artifact, Boolean) -> Unit, openUrl: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        val dir = vm.selectedDir
        if (dir == null && vm.selectedRepo != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("仓库尚未克隆", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text("克隆后可编辑、提交和推送", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = vm::clone, enabled = !vm.busy) { Text("克隆") }
                }
            }
        } else if (dir != null) {
            val s = vm.snapshot
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountTree, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(s?.branch ?: "本地仓库", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    val total = (s?.staged.orEmpty() + s?.changed.orEmpty() + s?.untracked.orEmpty() + s?.deleted.orEmpty() + s?.conflicts.orEmpty()).distinct().size
                    StateLabel(if (total == 0) "工作区干净" else "$total 处变更", if (total == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                }
            }
        }
        TabRow(selectedTabIndex = vm.detailTab, containerColor = MaterialTheme.colorScheme.background) {
            listOf("文件", "发布", "构建").forEachIndexed { i, title ->
                Tab(selected = vm.detailTab == i, onClick = { vm.selectDetailTab(i) }, text = { Text(title) })
            }
        }
        when (vm.detailTab) {
            0 -> CodeScreen(vm)
            1 -> ChangesScreen(vm)
            2 -> ActionsScreen(vm, export, openUrl)
        }
    }
    vm.editorPath?.let { path ->
        var dirty by remember(path) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = vm::closeEditor,
            title = { Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { OutlinedTextField(vm.editorText, { dirty = true; vm.editText(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 420.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) },
            confirmButton = { TextButton(onClick = vm::saveEditor, enabled = dirty && !vm.busy) { Text("保存") } },
            dismissButton = { TextButton(onClick = vm::closeEditor) { Text("关闭") } }
        )
    }
    vm.diffText?.let { TextViewer("代码差异", it, vm::closeDiff) }
    vm.logText?.let { TextViewer("构建日志", it, vm::closeLog) }
}

@Composable
private fun CodeScreen(vm: RainViewModel) {
    val dir = vm.selectedDir
    if (dir == null) { EmptyPanel(Icons.Rounded.Code, "尚无本地文件", "克隆后即可在手机上浏览和修改源码"); return }
    var search by remember(dir) { mutableStateOf("") }
    var folder by remember(dir) { mutableStateOf("") }
    val all = vm.files
    val folders = remember(all, folder, search) {
        if (search.isNotBlank()) emptyList() else all.asSequence().filter { it.startsWith(folder) }
            .mapNotNull { it.removePrefix(folder).substringBefore('/', "").takeIf { part -> part.isNotEmpty() } }
            .distinct().sorted().toList()
    }
    val files = remember(all, folder, search) {
        if (search.isNotBlank()) all.filter { it.contains(search, true) }
        else all.filter { it.startsWith(folder) && !it.removePrefix(folder).contains('/') }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("搜索文件 · ${all.size} 个") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (search.isNotEmpty()) {{ IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "清空") } }} else null)
            if (search.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { folder = "" }) { Text("根目录") }
                    if (folder.isNotEmpty()) {
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(15.dp))
                        Text(folder.trimEnd('/'), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { folder = folder.dropLast(1).substringBeforeLast('/', "").let { if (it.isBlank()) "" else "$it/" } }) {
                            Icon(Icons.Rounded.ArrowUpward, "上一级")
                        }
                    }
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(folders, key = { "folder-$folder$it" }) { name ->
                ListItem(headlineContent = { Text(name) }, leadingContent = { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.secondary) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    modifier = Modifier.clickable { folder += "$name/" })
            }
            items(files, key = { it }) { path ->
                ListItem(headlineContent = { Text(if (search.isBlank()) path.removePrefix(folder) else path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Rounded.Description, null, Modifier.size(20.dp)) },
                    trailingContent = { Icon(Icons.Rounded.Edit, "编辑", Modifier.size(17.dp)) },
                    modifier = Modifier.clickable { vm.openEditor(path) })
            }
            if (folders.isEmpty() && files.isEmpty()) item { EmptyPanel(Icons.Rounded.SearchOff, "未找到文件", "更换关键字或返回上一级") }
        }
    }
}

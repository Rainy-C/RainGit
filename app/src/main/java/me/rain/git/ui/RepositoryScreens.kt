package me.rain.git.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
        onClick = { vm.openRemote(repo, if (actionsOnly) 2 else if (local) 1 else 0) }, modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
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
    var create by remember { mutableStateOf(false) }
    if (create) TextInputDialog("初始化本地仓库", "目录名称", "创建空仓库，随后在 Git 工具中设置 origin。", onDismiss = { create = false }) {
        vm.initRepository(it); create = false
    }
    val dirs = remember(vm.localDirs, search) { vm.localDirs.filter { it.name.contains(search, true) } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            SectionHeading("本地工作区", "${vm.localDirs.size} 个仓库") {
                IconButton(onClick = { create = true }, enabled = !vm.busy) { Icon(Icons.Rounded.Add, "初始化仓库") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(search, { search = it }, placeholder = { Text("筛选本地仓库") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dirs, key = { it.absolutePath }) { dir ->
                Surface(onClick = { vm.openLocal(dir) }, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(17.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(vm: RainViewModel, pickDir: () -> Unit, grant: () -> Unit, openUrl: (String) -> Unit) {
    var author by remember { mutableStateOf(vm.store.author) }
    var email by remember { mutableStateOf(vm.store.email) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val monetSupported = android.os.Build.VERSION.SDK_INT >= 31
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Surface(shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .75f)) {
                        Icon(Icons.Rounded.Person, null, Modifier.padding(15.dp).size(27.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vm.account?.displayName ?: "GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("@${vm.account?.login.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SectionHeading("外观", "跟随你的壁纸和系统风格") }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("莫奈动态取色", fontWeight = FontWeight.SemiBold)
                            Text(if (monetSupported) "从系统壁纸读取主题色" else "需要 Android 12 及以上版本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = vm.dynamicColorEnabled && monetSupported,
                            onCheckedChange = vm::setDynamicColor, enabled = monetSupported)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    Text("显示模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("跟随系统", "浅色", "深色").forEachIndexed { i, text ->
                            SegmentedButton(selected = vm.themeMode == i, onClick = { vm.setTheme(i) },
                                shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(text) }
                        }
                    }
                }
            }
        }
        item { SectionHeading("Git 身份", "提交记录会使用这里的署名") }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(author, { author = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("显示名称") }, leadingIcon = { Icon(Icons.Rounded.Badge, null) })
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("提交邮箱") }, leadingIcon = { Icon(Icons.Rounded.AlternateEmail, null) })
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { vm.saveAuthor(author, email) },
                        enabled = author.isNotBlank() && email.contains('@'), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(7.dp)); Text("保存提交身份")
                    }
                }
            }
        }
        item { SectionHeading("仓库存储", "管理本地文件的保存位置") }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("当前工作目录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(vm.store.root().path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = pickDir, modifier = Modifier.fillMaxWidth()) { Text("选择仓库目录") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = grant) { Text("存储权限") }
                        TextButton(onClick = vm::usePrivateRoot) { Text("恢复默认目录") }
                    }
                }
            }
        }
        item {
            Surface(onClick = { showAbout = true }, shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                ListItem(headlineContent = { Text("关于 RainGit") }, supportingContent = { Text("版本、作者与开源许可") },
                    leadingContent = { Icon(Icons.Rounded.Info, null) }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
            }
        }
        item {
            TextButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Logout, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("退出 GitHub 账号")
            }
        }
    }
    if (showAbout) AboutDialog(openUrl) { showAbout = false }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text("退出账号？") },
        text = { Text("登录凭证会从设备移除，本地仓库文件不会删除。") },
        confirmButton = { TextButton(onClick = { vm.logout(); confirmLogout = false }) { Text("退出登录") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } })
}

@Composable
internal fun RepositoryScreen(vm: RainViewModel, openUrl: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        val dir = vm.selectedDir
        if (dir == null && vm.selectedRepo != null && vm.detailTab < 2) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("仓库尚未克隆", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text("克隆后可编辑、提交和推送", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = vm::clone, enabled = !vm.busy && !vm.downloading) { Text("克隆") }
                }
            }
        } else if (dir != null) {
            val s = vm.snapshot
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp).fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountTree, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(s?.branch ?: "本地仓库", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    val total = (s?.staged.orEmpty() + s?.changed.orEmpty() + s?.untracked.orEmpty() + s?.deleted.orEmpty() + s?.conflicts.orEmpty()).distinct().size
                    StateLabel(if (s == null) "尚未读取" else if (total == 0) "工作区干净" else "$total 处变更", if (total == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                }
            }
        }
        TabRow(selectedTabIndex = vm.detailTab, containerColor = MaterialTheme.colorScheme.background) {
            listOf("代码", "发布", "构建", "协作").forEachIndexed { i, title ->
                Tab(selected = vm.detailTab == i, enabled = !vm.busy, onClick = { vm.selectDetailTab(i) }, text = { Text(title) })
            }
        }
        AnimatedContent(targetState = vm.detailTab,
            transitionSpec = { fadeIn(tween(190)) togetherWith fadeOut(tween(120)) },
            label = "repository-tab", modifier = Modifier.weight(1f)) { tab ->
            when (tab) {
                0 -> CodeScreen(vm)
                1 -> ChangesScreen(vm)
                2 -> ActionsScreen(vm, openUrl)
                3 -> CollaborationScreen(vm, openUrl)
            }
        }
    }
    vm.editorPath?.let { path ->
        var dirty by remember(path) { mutableStateOf(false) }
        var confirmClose by remember(path) { mutableStateOf(false) }
        val requestClose: () -> Unit = { if (dirty) confirmClose = true else vm.closeEditor() }
        AlertDialog(onDismissRequest = requestClose,
            title = { Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { OutlinedTextField(vm.editorText, { dirty = true; vm.editText(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 420.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) },
            confirmButton = { TextButton(onClick = vm::saveEditor, enabled = dirty && !vm.busy) { Text("保存") } },
            dismissButton = { TextButton(onClick = requestClose) { Text("关闭") } }
        )
        if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("放弃未保存内容？") },
            confirmButton = { TextButton(onClick = vm::closeEditor) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("继续编辑") } })
    }
    vm.diffText?.let { TextViewer("代码差异", it, vm::closeDiff) }
    vm.logText?.let { BuildLogViewer(it, vm::closeLog, vm.logStatus, vm.logLoading, vm::refreshLogNow, {
        vm.chosenRun?.url?.let(openUrl) ?: vm.selectedRepo?.let { repo -> openUrl("https://github.com/${repo.fullName}/actions") }
    }) }
}

@Composable
private fun CodeScreen(vm: RainViewModel) {
    val dir = vm.selectedDir
    if (dir == null) { EmptyPanel(Icons.Rounded.Code, "尚无本地文件", "克隆后即可在手机上浏览和修改源码"); return }
    var search by remember(dir) { mutableStateOf("") }
    var folder by remember(dir) { mutableStateOf("") }
    var create by remember(dir) { mutableStateOf(false) }
    var fileMenu by remember(dir) { mutableStateOf<String?>(null) }
    var rename by remember(dir) { mutableStateOf<String?>(null) }
    var delete by remember(dir) { mutableStateOf<String?>(null) }
    if (create) TextInputDialog("新建文件", "相对路径", "路径相对于当前目录", onDismiss = { create = false }) {
        vm.createFile(folder + it); create = false
    }
    rename?.let { path -> TextInputDialog("重命名文件", "相对仓库根目录的路径", "原路径：$path", path, { rename = null }) {
        vm.renameFile(path, it); rename = null
    } }
    delete?.let { path -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("删除文件？") },
        text = { Text("删除 $path 的本地文件。未提交的内容无法恢复，远端在你提交并推送后才会改变。") },
        confirmButton = { TextButton(onClick = { vm.deleteFile(path); delete = null }, enabled = !vm.busy) { Text("删除") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("取消") } }) }
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
            SectionHeading("项目文件", "文本文件 ≤ 512 KiB · 隐藏 dist 下载目录") {
                IconButton(onClick = { create = true }, enabled = !vm.busy) { Icon(Icons.Rounded.NoteAdd, "新建文件") }
            }
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
                    trailingContent = { Box {
                        IconButton(onClick = { fileMenu = path }) { Icon(Icons.Rounded.MoreVert, "文件操作") }
                        DropdownMenu(expanded = fileMenu == path, onDismissRequest = { fileMenu = null }) {
                            DropdownMenuItem(text = { Text("重命名") }, onClick = { fileMenu = null; rename = path }, enabled = !vm.busy)
                            DropdownMenuItem(text = { Text("删除") }, onClick = { fileMenu = null; delete = path }, enabled = !vm.busy)
                        }
                    } },
                    modifier = Modifier.clickable { vm.openEditor(path) })
            }
            if (folders.isEmpty() && files.isEmpty()) item { EmptyPanel(Icons.Rounded.SearchOff, "未找到文件", "更换关键字或返回上一级") }
        }
    }
}

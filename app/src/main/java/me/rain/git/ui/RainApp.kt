package me.rain.git.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rain.git.github.*
import me.rain.git.local.LocalGit
import java.io.File

private val sage = Color(0xFF4A6754)
private val orange = Color(0xFFA96742)
private val night = Color(0xFF121713)
private val paletteLight = lightColorScheme(
    primary = sage, onPrimary = Color.White, secondary = orange,
    surface = Color(0xFFFDFCF8), background = Color(0xFFF7F8F4),
    surfaceVariant = Color(0xFFE8ECE5), onSurface = Color(0xFF202922)
)
private val paletteDark = darkColorScheme(
    primary = Color(0xFFADCBB3), onPrimary = Color(0xFF1B3625), secondary = Color(0xFFE4AE88),
    surface = Color(0xFF1B211D), background = night,
    surfaceVariant = Color(0xFF303A33), onSurface = Color(0xFFE4EEE5)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RainApp(
    model: RainViewModel,
    onChooseDirectory: () -> Unit,
    onGrantStorage: () -> Unit,
    onExportArtifact: (Artifact, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) paletteDark else paletteLight,
        shapes = Shapes(extraLarge = RoundedCornerShape(26.dp), large = RoundedCornerShape(22.dp), medium = RoundedCornerShape(16.dp))) {
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(model.message) {
            val msg = model.message ?: return@LaunchedEffect
            model.clearMessage()
            snack.showSnackbar(msg)
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                if (model.account != null) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(if (model.screen == "detail") model.selectedRepo?.fullName ?: model.selectedDir?.name ?: "Local Git" else "RainGit",
                                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (model.screen != "detail") Text(model.account!!.login, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        navigationIcon = { if (model.screen == "detail") IconButton(onClick = model::closeDetail) {
                            Icon(Icons.Rounded.ArrowBack, "返回")
                        } },
                        actions = {
                            if (model.screen != "settings") IconButton(onClick = {
                                if (model.screen == "detail") {
                                    if (model.detailTab == 2) model.refreshActions() else model.refreshLocal()
                                } else model.refreshRepos()
                            }) { Icon(Icons.Rounded.Refresh, "刷新") }
                        }
                    )
                }
            },
            bottomBar = {
                if (model.account != null && model.screen != "detail") {
                    NavigationBar {
                        listOf(
                            Triple("home", "仓库", Icons.Rounded.Folder),
                            Triple("local", "本地", Icons.Rounded.Code),
                            Triple("actions", "Actions", Icons.Rounded.PlayArrow),
                            Triple("settings", "设置", Icons.Rounded.Settings)
                        ).forEach { (route, label, image) ->
                            NavigationBarItem(selected = model.screen == route, onClick = { model.navigate(route) },
                                icon = { Icon(image, label) }, label = { Text(label) })
                        }
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (model.account == null) LoginPage(model, onOpenUrl)
                else when (model.screen) {
                    "home" -> RemoteList(model, false)
                    "local" -> LocalList(model)
                    "actions" -> RemoteList(model, true)
                    "settings" -> SettingsPage(model, onChooseDirectory, onGrantStorage)
                    "detail" -> DetailPage(model, onExportArtifact, onOpenUrl)
                }
            }
        }
    }
}

@Composable
private fun Headline(title: String, sub: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (sub != null) {
            Spacer(Modifier.height(5.dp))
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginPage(vm: RainViewModel, openUrl: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf(vm.store.oauthClientId) }
    var mode by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.Start) {
        Spacer(Modifier.height(42.dp))
        Icon(Icons.Rounded.AccountTree, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(18.dp))
        Text("RainGit", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("仓库、代码与 Actions，一处掌控", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = mode == 0, onClick = { mode = 0 }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) {
                Text("GitHub Token")
            }
            SegmentedButton(selected = mode == 1, onClick = { mode = 1 }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) {
                Text("授权码登录")
            }
        }
        Spacer(Modifier.height(20.dp))
        if (mode == 0) {
            OutlinedTextField(token, { token = it }, label = { Text("Personal Access Token") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Classic Token 建议 repo + workflow 权限；Fine-grained Token 按需授予 Contents 和 Actions。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp))
            Button(onClick = { vm.loginWithToken(token) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy && token.isNotBlank()) {
                Text("验证并登录")
            }
        } else {
            OutlinedTextField(clientId, { clientId = it }, label = { Text("OAuth App Client ID") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("首次需要创建自己的 GitHub OAuth App 并启用 Device Flow。账号密码、2FA 或 Passkey 在 GitHub 网页上完成，App 不采集密码。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp))
            val dev = vm.device
            if (dev == null) {
                Button(onClick = { vm.startOAuth(clientId) }, enabled = !vm.busy && clientId.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("获取授权码")
                }
            } else {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("在浏览器输入授权码", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer { Text(dev.userCode, style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Monospace) }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { openUrl(dev.url) }) { Icon(Icons.Rounded.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("打开 GitHub 验证") }
                        Text("授权完成后自动登录", style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = vm::cancelOAuth) { Text("取消授权") }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        TextButton(onClick = { openUrl("https://github.com/settings/tokens") }) {
            Text("前往 GitHub 创建 Token")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RemoteList(vm: RainViewModel, actions: Boolean) {
    var search by remember { mutableStateOf("") }
    val list = remember(vm.repos, search) { vm.repos.filter { it.fullName.contains(search, ignoreCase = true) } }
    Column(Modifier.fillMaxSize()) {
        Headline(if (actions) "Actions 控制台" else "我的仓库", if (actions) "选择一个仓库，查看工作流与运行记录" else "${vm.repos.size} 个仓库 · 登录后自动同步")
        OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            placeholder = { Text("搜索仓库") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.fullName }) { repo ->
                ElevatedCard(onClick = { vm.openRemote(repo, if (actions) 2 else 0) }, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (actions) Icons.Rounded.PlayArrow else Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(repo.fullName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (repo.privateRepo) Icon(Icons.Rounded.Lock, "私有仓库", Modifier.size(18.dp))
                        }
                        if (repo.description.isNotBlank()) {
                            Spacer(Modifier.height(9.dp))
                            Text(repo.description, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("${repo.defaultBranch}    ·    ${repo.updatedAt.take(10)}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (list.isEmpty()) item { Text("暂无仓库。检查 Token 权限或下拉同步。", Modifier.padding(22.dp)) }
        }
    }
}

@Composable
private fun LocalList(vm: RainViewModel) {
    Column {
        Headline("本地仓库", "工作目录：${vm.store.root().path}")
        LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.localDirs, key = { it.absolutePath }) { dir ->
                ElevatedCard(onClick = { vm.openLocal(dir) }, Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderOpen, null)
                        Spacer(Modifier.width(12.dp))
                        Text(dir.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
            if (vm.localDirs.isEmpty()) item { Text("本地目录暂无 Git 仓库。可以从远程仓库中 Clone。", Modifier.padding(18.dp)) }
        }
    }
}

@Composable
private fun SettingsPage(vm: RainViewModel, pickDir: () -> Unit, grant: () -> Unit) {
    var author by remember { mutableStateOf(vm.store.author) }
    var email by remember { mutableStateOf(vm.store.email) }
    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxSize().padding(horizontal = 20.dp)) {
        Headline("偏好设置", "本地 Git 作者身份与文件存储")
        OutlinedTextField(author, { author = it }, label = { Text("Git 作者名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(email, { email = it }, label = { Text("提交邮箱") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { vm.saveAuthor(author, email) }) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(6.dp)); Text("保存作者信息") }
        Spacer(Modifier.height(24.dp))
        Text("工作目录", style = MaterialTheme.typography.titleMedium)
        Text(vm.store.root().path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = grant) { Icon(Icons.Rounded.Security, null); Spacer(Modifier.width(6.dp)); Text("授权访问公共目录") }
        OutlinedButton(onClick = pickDir) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("选择主存储文件夹") }
        TextButton(onClick = vm::usePrivateRoot) { Text("恢复应用专属目录") }
        Text("Android 公共目录的完整 Git 读写需要“所有文件访问权限”。不授权时仍可使用应用专属目录。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = vm::logout) { Icon(Icons.Rounded.Logout, null); Spacer(Modifier.width(6.dp)); Text("退出 GitHub") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DetailPage(vm: RainViewModel, export: (Artifact, Boolean) -> Unit, openUrl: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        val dir = vm.selectedDir
        if (dir == null && vm.selectedRepo != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("该仓库尚未克隆", Modifier.weight(1f))
                    Button(onClick = vm::clone, enabled = !vm.busy) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(4.dp)); Text("Clone") }
                }
            }
        }
        TabRow(selectedTabIndex = vm.detailTab) {
            listOf("文件", "变更", "Actions").forEachIndexed { i, label ->
                Tab(selected = vm.detailTab == i, onClick = { vm.selectDetailTab(i) }, text = { Text(label) })
            }
        }
        when (vm.detailTab) {
            0 -> CodeTab(vm)
            1 -> ChangesTab(vm)
            2 -> ActionsTab(vm, export, openUrl)
        }
    }
    vm.editorPath?.let { path ->
        AlertDialog(
            onDismissRequest = vm::closeEditor,
            title = { Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                OutlinedTextField(vm.editorText, vm::editText, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 450.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            },
            confirmButton = { TextButton(onClick = vm::saveEditor) { Text("保存") } },
            dismissButton = { TextButton(onClick = vm::closeEditor) { Text("取消") } }
        )
    }
    vm.diffText?.let { diff ->
        BigTextDialog("Git Diff", diff, vm::closeDiff)
    }
    vm.logText?.let { logs ->
        BigTextDialog("Actions 日志", logs, vm::closeLog)
    }
}

@Composable
private fun CodeTab(vm: RainViewModel) {
    val dir = vm.selectedDir
    var query by remember(dir) { mutableStateOf("") }
    if (dir == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Clone 仓库后可浏览与编辑代码") }
        return
    }
    Column {
        val branch = vm.snapshot?.branch ?: "…"
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccountTree, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(branch, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${vm.files.size} 文件", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            placeholder = { Text("搜索文件路径") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        val filtered = remember(vm.files, query) { vm.files.filter { it.contains(query, ignoreCase = true) } }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            items(filtered, key = { it }) { path ->
                ListItem(headlineContent = { Text(path, maxLines = 2, style = MaterialTheme.typography.bodyMedium) },
                    leadingContent = { Icon(Icons.Rounded.Description, null, Modifier.size(19.dp)) },
                    modifier = Modifier.clickable { vm.openEditor(path) })
                HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ChangesTab(vm: RainViewModel) {
    val status = vm.snapshot
    if (vm.selectedDir == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("先 Clone 仓库，才能进行 Git 操作") }
        return
    }
    var commit by remember(vm.selectedDir) { mutableStateOf("") }
    var branch by remember(vm.selectedDir) { mutableStateOf("") }
    var confirmSwitch by remember { mutableStateOf<String?>(null) }
    val changes = status?.let { (it.changed + it.untracked + it.deleted + it.conflicts).distinct().sorted() }.orEmpty()
    Column {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactAction("Fetch", Icons.Rounded.Sync, Modifier.weight(1f)) { vm.gitOperation("fetch") }
                    CompactAction("Pull", Icons.Rounded.South, Modifier.weight(1f)) { vm.gitOperation("pull") }
                    CompactAction("Push", Icons.Rounded.North, Modifier.weight(1f)) { vm.gitOperation("push") }
                }
            }
            item {
                Text("暂存区 · ${status?.staged?.size ?: 0}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.gitOperation("stage", ".") }) { Text("暂存全部") }
                    TextButton(onClick = { vm.showDiff(true) }) { Text("查看 Staged Diff") }
                }
            }
            items(status?.staged.orEmpty()) { path ->
                ChangeRow(path, "Staged", "取消暂存") { vm.gitOperation("unstage", path) }
            }
            item {
                OutlinedTextField(commit, { commit = it }, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.gitOperation("commit", commit) }, enabled = commit.isNotBlank() && !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("提交暂存的修改")
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text("未暂存变更 · ${changes.size}", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { vm.showDiff(false) }) { Text("查看 Working Diff") }
            }
            items(changes) { path -> ChangeRow(path, "Modified", "暂存") { vm.gitOperation("stage", path) } }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text("分支 · ${status?.branch ?: "..."}", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(branch, { branch = it }, label = { Text("新分支名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { vm.gitOperation("branch", branch) }, enabled = branch.isNotBlank()) { Text("创建分支") }
            }
            items(status?.branches.orEmpty()) { name ->
                ListItem(headlineContent = { Text(name) }, trailingContent = {
                    TextButton(onClick = { confirmSwitch = name }) { Text("切换") }
                })
            }
            item {
                HorizontalDivider()
                Text("Stash", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.gitOperation("stash") }) { Text("Stash Save") }
                    OutlinedButton(onClick = { vm.gitOperation("apply") }) { Text("Stash Apply") }
                }
            }
            item { Text("近期提交", style = MaterialTheme.typography.titleMedium) }
            items(status?.history.orEmpty()) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
    confirmSwitch?.let { name ->
        AlertDialog(onDismissRequest = { confirmSwitch = null }, title = { Text("切换分支") },
            text = { Text("切换到 $name？请先提交或暂存当前变更。") },
            confirmButton = { TextButton(onClick = { vm.gitOperation("checkout", name); confirmSwitch = null }) { Text("切换") } },
            dismissButton = { TextButton(onClick = { confirmSwitch = null }) { Text("取消") } })
    }
}

@Composable
private fun CompactAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, click: () -> Unit) {
    FilledTonalButton(onClick = click, modifier = modifier, contentPadding = PaddingValues(horizontal = 7.dp)) {
        Icon(icon, null, Modifier.size(17.dp)); Spacer(Modifier.width(3.dp)); Text(label)
    }
}

@Composable
private fun ChangeRow(path: String, state: String, action: String, click: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(path, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            TextButton(onClick = click) { Text(action) }
        }
    }
}

@Composable
private fun ActionsTab(vm: RainViewModel, export: (Artifact, Boolean) -> Unit, openUrl: (String) -> Unit) {
    if (vm.selectedRepo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Actions 仅支持已关联的 GitHub 仓库") }
        return
    }
    val run = vm.chosenRun
    if (run != null) {
        RunDetail(vm, run, export, openUrl)
    } else {
        var chosen by remember(vm.selectedRepo) { mutableStateOf<Workflow?>(null) }
        var expand by remember { mutableStateOf(false) }
        var ref by remember(vm.selectedRepo) { mutableStateOf(vm.selectedRepo?.defaultBranch.orEmpty()) }
        var inputs by remember(vm.selectedRepo) { mutableStateOf("") }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { Text("运行工作流", style = MaterialTheme.typography.titleLarge) }
            item {
                Box {
                    OutlinedButton(onClick = { expand = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(chosen?.name ?: "选择 Workflow", maxLines = 1)
                        Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expand, onDismissRequest = { expand = false }) {
                        vm.workflows.forEach { w ->
                            DropdownMenuItem(text = { Text(w.name) }, onClick = { chosen = w; expand = false })
                        }
                    }
                }
            }
            item { OutlinedTextField(ref, { ref = it }, label = { Text("Git ref / 分支") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(inputs, { inputs = it }, label = { Text("Workflow inputs (JSON，可留空)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4) }
            item {
                Button(onClick = { chosen?.let { vm.dispatch(it, ref, inputs) } }, enabled = chosen != null && !vm.busy,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Dispatch 工作流")
                }
            }
            item { HorizontalDivider(); Spacer(Modifier.height(7.dp)); Text("运行记录 · ${vm.runs.size}", style = MaterialTheme.typography.titleLarge) }
            items(vm.runs, key = { it.id }) { r ->
                ElevatedCard(onClick = { vm.chooseRun(r) }, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Terminal, null, tint = when (r.conclusion) {
                                "success" -> MaterialTheme.colorScheme.primary
                                "failure" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.secondary
                            })
                            Spacer(Modifier.width(9.dp))
                            Text(r.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("#${r.number}", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${r.branch}  ·  ${r.status}  ${r.conclusion}  ·  ${r.created.take(16).replace('T', ' ')}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (vm.runs.isEmpty()) item { Text("暂无运行记录。工作流需要位于 .github/workflows，并定义合适的触发条件。") }
        }
    }
}

@Composable
private fun RunDetail(vm: RainViewModel, run: Run, export: (Artifact, Boolean) -> Unit, openUrl: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = vm::backRun) { Icon(Icons.Rounded.ArrowBack, null); Spacer(Modifier.width(5.dp)); Text("运行列表") }
            Text(run.name, style = MaterialTheme.typography.headlineSmall)
            Text("#${run.number} · ${run.branch} · ${run.status} · ${run.conclusion}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                CompactAction("日志", Icons.Rounded.Description, Modifier.weight(1f), vm::readLogs)
                CompactAction("重跑", Icons.Rounded.Refresh, Modifier.weight(1f)) { vm.controlRun("rerun") }
                CompactAction("取消", Icons.Rounded.Close, Modifier.weight(1f)) { vm.controlRun("cancel") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.controlRun("rerun-failed-jobs") }) { Text("只重跑失败 Jobs") }
                TextButton(onClick = { openUrl(run.url) }) { Text("浏览器查看") }
            }
        }
        item { Text("Jobs · ${vm.jobs.size}", style = MaterialTheme.typography.titleLarge) }
        items(vm.jobs, key = { it.id }) { job ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("${job.name} · ${job.conclusion.ifBlank { job.status }}", fontWeight = FontWeight.SemiBold)
                    job.steps.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (s.conclusion == "success") Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                null, Modifier.size(16.dp), tint = if (s.conclusion == "failure") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(s.conclusion, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item { Text("构建产物 · ${vm.artifacts.size}", style = MaterialTheme.typography.titleLarge) }
        items(vm.artifacts, key = { it.id }) { item ->
            ListItem(headlineContent = { Text(item.name) },
                supportingContent = { Text("${item.size / 1024} KB${if (item.expired) " · 已过期" else ""}") },
                leadingContent = { Icon(Icons.Rounded.Inventory2, null) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { export(item, false) }, enabled = !item.expired) {
                            Icon(Icons.Rounded.Archive, "下载 ZIP")
                        }
                        IconButton(onClick = { export(item, true) }, enabled = !item.expired) {
                            Icon(Icons.Rounded.Android, "提取 APK")
                        }
                    }
                })
        }
    }
}

@Composable
private fun BigTextDialog(title: String, body: String, close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(onDismissRequest = close,
        title = { Text(title) },
        text = {
            SelectionContainer {
                Text(body, modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(title, body))
            }) { Text("复制") }
        },
        dismissButton = { TextButton(onClick = close) { Text("关闭") } }
    )
}

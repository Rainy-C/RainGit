package me.rain.git.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun ChangesScreen(vm: RainViewModel) {
    if (vm.selectedDir == null) { EmptyPanel(Icons.Rounded.AccountTree, "尚无本地仓库", "克隆后才能暂存、提交与推送"); return }
    val snap = vm.snapshot
    val staged = snap?.staged.orEmpty()
    val conflicts = snap?.conflicts.orEmpty()
    val changes = (snap?.changed.orEmpty() + snap?.untracked.orEmpty() + snap?.deleted.orEmpty() + conflicts).distinct().sorted()
    var commitText by remember(vm.selectedDir) { mutableStateOf("") }
    var more by remember(vm.selectedDir) { mutableStateOf(false) }
    var branchMenu by remember(vm.selectedDir) { mutableStateOf(false) }
    var newBranch by remember(vm.selectedDir) { mutableStateOf("") }
    var switchTo by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccountTree, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Text(snap?.branch ?: "读取仓库中", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StateLabel(if (conflicts.isNotEmpty()) "${conflicts.size} 个冲突" else if (changes.isEmpty() && staged.isEmpty()) "无变更" else "${(changes + staged).distinct().size} 处修改",
                            if (conflicts.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.gitOperation("pull") }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.South, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("拉取")
                        }
                        Button(onClick = { vm.gitOperation("push") }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.North, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("推送")
                        }
                        IconButton(onClick = { vm.gitOperation("fetch") }, enabled = !vm.busy) { Icon(Icons.Rounded.Sync, "Fetch") }
                    }
                }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    SectionHeading("快速提交", "${staged.size} 个文件已暂存")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(commitText, { commitText = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("这次修改做了什么？") }, singleLine = true,
                        label = { Text("Commit message") })
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.gitOperation("commit", commitText) }, enabled = !vm.busy && commitText.isNotBlank() && staged.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("提交 ${staged.size} 个已暂存文件")
                    }
                    if (staged.isEmpty()) Text("先暂存文件，才可提交。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionHeading("待暂存", "${changes.size} 个文件") {
                TextButton(onClick = { vm.gitOperation("stage", ".") }, enabled = !vm.busy && changes.isNotEmpty()) { Text("全部暂存") }
            }
        }
        items(changes, key = { "modified-$it" }) { path ->
            val label = when { path in conflicts -> "冲突"; path in snap?.untracked.orEmpty() -> "新增"; path in snap?.deleted.orEmpty() -> "删除"; else -> "修改" }
            FileChangeTile(path, label, "暂存", { vm.gitOperation("stage", path) }, !vm.busy)
        }
        if (changes.isEmpty()) item { EmptyPanel(Icons.Rounded.CheckCircle, "没有未暂存的修改", "工作区文件已处理") }
        item {
            SectionHeading("已暂存", "${staged.size} 个文件") {
                TextButton(onClick = { vm.showDiff(true) }, enabled = staged.isNotEmpty() && !vm.busy) { Text("查看 Diff") }
            }
        }
        items(staged, key = { "staged-$it" }) { path -> FileChangeTile(path, "已暂存", "撤销暂存", { vm.gitOperation("unstage", path) }, !vm.busy) }
        item {
            TextButton(onClick = { vm.showDiff(false) }, enabled = !vm.busy) { Icon(Icons.Rounded.Difference, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("查看工作区 Diff") }
        }
        item {
            HorizontalDivider()
            ListItem(headlineContent = { Text("分支与高级操作") }, supportingContent = { Text("切换分支、创建分支、Stash、提交历史") },
                leadingContent = { Icon(Icons.Rounded.Tune, null) },
                trailingContent = { Icon(if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
                modifier = Modifier.fillMaxWidth())
            FilledTonalButton(onClick = { more = !more }, modifier = Modifier.fillMaxWidth()) { Text(if (more) "收起高级操作" else "展开高级操作") }
        }
        if (more) {
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        SectionHeading("分支管理", "当前：${snap?.branch.orEmpty()}")
                        Box {
                            OutlinedButton(onClick = { branchMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("切换分支", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                                Icon(Icons.Rounded.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                                snap?.branches.orEmpty().forEach { name ->
                                    DropdownMenuItem(text = { Text(name) }, onClick = { branchMenu = false; if (name != snap?.branch) switchTo = name },
                                        leadingIcon = if (name == snap?.branch) {{ Icon(Icons.Rounded.Check, null) }} else null)
                                }
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        OutlinedTextField(newBranch, { newBranch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("新分支名称") }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.gitOperation("branch", newBranch) }, enabled = !vm.busy && newBranch.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("创建分支") }
                    }
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        SectionHeading("临时保存", "使用 Git Stash 暂存工作区")
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedButton(onClick = { vm.gitOperation("stash") }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Stash 保存") }
                            OutlinedButton(onClick = { vm.gitOperation("apply") }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Stash 恢复") }
                        }
                    }
                }
            }
            item { SectionHeading("最近提交") }
            items(snap?.history.orEmpty()) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
    switchTo?.let { name ->
        AlertDialog(onDismissRequest = { switchTo = null }, title = { Text("切换到 $name？") },
            text = { Text("请确保工作区已提交或保存。切换失败时将保留当前分支。") },
            confirmButton = { TextButton(onClick = { vm.gitOperation("checkout", name); switchTo = null }) { Text("确认切换") } },
            dismissButton = { TextButton(onClick = { switchTo = null }) { Text("取消") } })
    }
}

@Composable
private fun FileChangeTile(path: String, state: String, action: String, click: () -> Unit, enabled: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 7.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(state, color = if (state == "冲突") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = click, enabled = enabled) { Text(action) }
        }
    }
}

@Composable
internal fun TextViewer(title: String, body: String, close: () -> Unit) {
    val context = LocalContext.current
    var search by remember(body) { mutableStateOf("") }
    val allLines = remember(body) { body.lineSequence().toList() }
    val matching = remember(allLines, search) {
        if (search.isBlank()) allLines.take(2500)
        else allLines.filter { it.contains(search, ignoreCase = true) }.take(2500)
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.91f),
            shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText(title, body))
                    }) { Icon(Icons.Rounded.ContentCopy, "复制完整内容") }
                    IconButton(onClick = close) { Icon(Icons.Rounded.Close, "关闭") }
                }
                OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("搜索日志 / Diff") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (search.isNotBlank()) {{ IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "清除") } }} else null)
                if (title == "构建日志") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { search = "error" }, label = { Text("错误") })
                        AssistChip(onClick = { search = "failed" }, label = { Text("失败") })
                        AssistChip(onClick = { search = "exception" }, label = { Text("异常") })
                    }
                }
                Text("${matching.size} 行${if (matching.size == 2500) " · 仅显示前 2500 行，可搜索或复制全文" else ""}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    Text(matching.joinToString("\n"), modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

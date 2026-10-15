package me.rain.git.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChangesScreen(vm: RainViewModel) {
    if (vm.selectedDir == null) {
        EmptyPanel(Icons.Rounded.AccountTree, "先克隆仓库", "仓库保存到手机后才能编辑和发布")
        return
    }
    val snap = vm.snapshot
    val staged = snap?.staged.orEmpty()
    val conflicts = snap?.conflicts.orEmpty()
    val unstaged = (snap?.changed.orEmpty() + snap?.untracked.orEmpty() + snap?.deleted.orEmpty()).distinct().sorted()
    val changedFiles = (staged + unstaged + conflicts).distinct().sorted()
    var commitText by remember(vm.selectedDir) { mutableStateOf("") }
    var showFiles by remember(vm.selectedDir) { mutableStateOf(true) }
    var showAdvanced by remember(vm.selectedDir) { mutableStateOf(false) }
    var branchMenu by remember(vm.selectedDir) { mutableStateOf(false) }
    var newBranch by remember(vm.selectedDir) { mutableStateOf("") }
    var switchTo by remember { mutableStateOf<String?>(null) }
    var discardPath by remember { mutableStateOf<String?>(null) }
    var tagText by remember(vm.selectedDir) { mutableStateOf("") }
    val hasChanges = changedFiles.isNotEmpty()
    val pending = snap?.ahead ?: 0
    val behind = snap?.behind ?: 0
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
                shadowElevation = 9.dp,
                shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                    if (hasChanges) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudUpload, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("发布到 GitHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${changedFiles.size} 个文件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = commitText, onValueChange = { commitText = it },
                            placeholder = { Text("例如：更新 README 文档") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Rounded.EditNote, null) },
                            shape = RoundedCornerShape(15.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.publishChanges(commitText) },
                            enabled = !vm.busy && commitText.isNotBlank() && conflicts.isEmpty(),
                            modifier = Modifier.fillMaxWidth().height(49.dp),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("暂存全部 · 提交 · 推送")
                        }
                        Text(
                            if (conflicts.isNotEmpty()) "存在冲突文件，发布已暂停"
                            else "将提交所有 ${changedFiles.size} 个修改，请先确认没有敏感文件",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (conflicts.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else if (pending > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$pending 次提交等待推送", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Button(onClick = vm::pushExisting, enabled = !vm.busy) {
                                Icon(Icons.Rounded.North, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp)); Text("推送")
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Text("没有待发布的修改", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.gitOperation("pull") }, enabled = !vm.busy) { Text("拉取更新") }
                        }
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(21.dp)) {
                    Column(Modifier.fillMaxWidth().padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AccountTree, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Text(snap?.branch ?: "正在读取...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            StateLabel(if (conflicts.isNotEmpty()) "${conflicts.size} 个冲突" else "${changedFiles.size} 处变更",
                                if (conflicts.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(
                            if (snap == null) "正在扫描仓库..."
                            else if (snap.ahead == null || snap.behind == null) "未设置上游分支，推送时会由 Git 检查远端"
                            else "待推送 ${snap.ahead} 次提交 · 待拉取 ${snap.behind} 次提交",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (behind > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text("远端有新提交，建议先拉取并解决冲突后再发布", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = { vm.gitOperation("pull") }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.South, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("拉取")
                            }
                            OutlinedButton(onClick = { vm.gitOperation("fetch") }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Sync, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("更新状态")
                            }
                        }
                    }
                }
            }
            item {
                Surface(onClick = { showFiles = !showFiles }, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FactCheck, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("变更文件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${unstaged.size} 个待暂存 · ${staged.size} 个已暂存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (showFiles) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                }
            }
            if (showFiles) {
                item {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.gitOperation("stage", ".") }, enabled = !vm.busy && unstaged.isNotEmpty()) { Text("全部暂存") }
                        TextButton(onClick = { vm.gitOperation("unstage-all") }, enabled = !vm.busy && staged.isNotEmpty()) { Text("取消暂存") }
                        TextButton(onClick = { vm.showDiff(false) }, enabled = !vm.busy) { Text("查看修改") }
                        TextButton(onClick = { vm.showDiff(true) }, enabled = !vm.busy && staged.isNotEmpty()) { Text("查看已暂存") }
                    }
                }
                items(conflicts.filterNot { it in unstaged || it in staged }, key = { "conflict-$it" }) { path ->
                    FileChangeTile(path, "冲突", "需手动解决", {}, false)
                }
                items(unstaged, key = { "unstaged-$it" }) { path ->
                    Column {
                        FileChangeTile(path, if (path in conflicts) "冲突" else "未暂存", "暂存", { vm.gitOperation("stage", path) }, !vm.busy)
                        if (path !in conflicts && path !in snap?.untracked.orEmpty()) {
                            TextButton(onClick = { discardPath = path }, enabled = !vm.busy) {
                                Icon(Icons.Rounded.Undo, null, Modifier.size(16.dp)); Text("撤销此文件未暂存修改")
                            }
                        }
                    }
                }
                items(staged, key = { "staged-$it" }) { path ->
                    FileChangeTile(path, "已暂存", "撤销暂存", { vm.gitOperation("unstage", path) }, !vm.busy)
                }
                if (!hasChanges) item { EmptyPanel(Icons.Rounded.CheckCircle, "工作区干净", "可以切换到文件页面修改代码") }
            }
            item {
                Surface(onClick = { showAdvanced = !showAdvanced }, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Git 高级操作", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("分支、Tag、Stash 与提交历史", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                }
            }
            if (showAdvanced) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.gitOperation("commit", commitText) }, enabled = staged.isNotEmpty() && commitText.isNotBlank() && !vm.busy, modifier = Modifier.weight(1f)) { Text("仅提交已暂存") }
                        OutlinedButton(onClick = vm::pushExisting, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("仅推送") }
                    }
                }
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            SectionHeading("分支", "当前：${snap?.branch.orEmpty()}")
                            Spacer(Modifier.height(9.dp))
                            Box {
                                OutlinedButton(onClick = { branchMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("切换分支", modifier = Modifier.weight(1f)); Icon(Icons.Rounded.ArrowDropDown, null)
                                }
                                DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                                    snap?.branches.orEmpty().forEach { branch ->
                                        DropdownMenuItem(text = { Text(branch) }, onClick = { branchMenu = false; if (branch != snap?.branch) switchTo = branch })
                                    }
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                            OutlinedTextField(newBranch, { newBranch = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("新分支名") })
                            TextButton(onClick = { vm.gitOperation("branch", newBranch); newBranch = "" }, enabled = newBranch.isNotBlank() && !vm.busy) { Text("创建分支") }
                        }
                    }
                }
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            SectionHeading("版本标签", "为当前提交创建 Tag")
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(tagText, { tagText = it }, label = { Text("标签名，例如 v1.2.0") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { vm.gitOperation("tag", tagText); tagText = "" },
                                    enabled = !vm.busy && tagText.isNotBlank(), modifier = Modifier.weight(1f)) { Text("创建 Tag") }
                                OutlinedButton(onClick = { vm.gitOperation("push-tags") }, enabled = !vm.busy,
                                    modifier = Modifier.weight(1f)) { Text("推送标签") }
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.gitOperation("stash") }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("保存 Stash") }
                        OutlinedButton(onClick = { vm.gitOperation("apply") }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("恢复 Stash") }
                    }
                }
                item { ExtraGitTools(vm) }
                item { SectionHeading("最近提交", "点击查看完整说明和差异") }
                items(snap?.history.orEmpty()) { line ->
                    Surface(onClick = { vm.showCommit(line.substringBefore(' ')) }, enabled = !vm.busy,
                        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(line.substringAfter("  "), style = MaterialTheme.typography.bodyMedium)
                            Text(line.take(8), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    discardPath?.let { path ->
        AlertDialog(onDismissRequest = { discardPath = null },
            title = { Text("撤销修改？") },
            text = { Text("将用已暂存版本恢复 $path 的工作区内容。此操作无法撤销，不会清除已经暂存的版本。") },
            confirmButton = { TextButton(onClick = { vm.gitOperation("discard", path); discardPath = null }) { Text("确认撤销", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { discardPath = null }) { Text("取消") } })
    }
    switchTo?.let { branch ->
        AlertDialog(
            onDismissRequest = { switchTo = null }, title = { Text("切换到 $branch？") },
            text = { Text("请确保工作区中的修改已经保存") },
            confirmButton = { TextButton(onClick = { vm.gitOperation("checkout", branch); switchTo = null }) { Text("切换") } },
            dismissButton = { TextButton(onClick = { switchTo = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun FileChangeTile(path: String, state: String, action: String, click: () -> Unit, enabled: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shape = RoundedCornerShape(14.dp)) {
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

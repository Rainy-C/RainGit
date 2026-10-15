package me.rain.git.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ChangesScreen(vm: RainViewModel) {
    val dir = vm.selectedDir
    if (dir == null) {
        EmptyPanel(Icons.Rounded.AccountTree, "先克隆仓库", "克隆后即可提交和推送修改")
        return
    }
    val snap = vm.snapshot
    val staged = snap?.staged.orEmpty()
    val conflicts = snap?.conflicts.orEmpty()
    val unstaged = remember(snap) { (snap?.changed.orEmpty() + snap?.untracked.orEmpty() + snap?.deleted.orEmpty()).distinct() }
    val changedFiles = remember(snap) { (staged + unstaged + conflicts).distinct().sorted() }
    var commitText by rememberSaveable(dir.path) { mutableStateOf("") }
    var showTools by rememberSaveable(dir.path) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var discardPath by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val writable = !vm.busy && !vm.downloading && snap != null
    val hasChanges = changedFiles.isNotEmpty()
    val pending = snap?.ahead
    val behind = snap?.behind ?: 0

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(snap?.branch ?: "读取仓库…", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(when {
                    snap == null -> "正在读取状态"
                    conflicts.isNotEmpty() -> "${conflicts.size} 个冲突待解决"
                    behind > 0 -> "待拉取 $behind · 待推送 ${pending ?: 0}"
                    pending == null -> "上游未设置"
                    pending > 0 -> "$pending 次提交待推送"
                    else -> "${changedFiles.size} 个变更"
                }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { vm.gitOperation("pull") }, enabled = writable) { Text("拉取") }
            IconButton(onClick = { keyboard?.hide(); showTools = true }, enabled = !vm.busy) {
                Icon(Icons.Rounded.Tune, "Git 工具")
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = !vm.busy) { Icon(Icons.Rounded.MoreVert, "更多操作") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("获取远端状态") }, onClick = { menu = false; vm.gitOperation("fetch") }, enabled = writable)
                    DropdownMenuItem(text = { Text("查看未暂存差异") }, onClick = { menu = false; vm.showDiff(false) }, enabled = unstaged.isNotEmpty())
                    DropdownMenuItem(text = { Text("查看已暂存差异") }, onClick = { menu = false; vm.showDiff(true) }, enabled = staged.isNotEmpty())
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("全部暂存") }, onClick = { menu = false; vm.gitOperation("stage", ".") }, enabled = writable && unstaged.isNotEmpty())
                    DropdownMenuItem(text = { Text("取消全部暂存") }, onClick = { menu = false; vm.gitOperation("unstage-all") }, enabled = writable && staged.isNotEmpty())
                    DropdownMenuItem(text = { Text("仅提交已暂存") }, onClick = {
                        menu = false; keyboard?.hide(); vm.gitOperation("commit", commitText)
                    }, enabled = writable && staged.isNotEmpty() && commitText.isNotBlank() && conflicts.isEmpty())
                    DropdownMenuItem(text = { Text("推送已有提交") }, onClick = { menu = false; vm.pushExisting() }, enabled = writable && !snap?.history.isNullOrEmpty())
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasChanges) item {
                Text("变更 ${changedFiles.size} · 已暂存 ${staged.size}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(changedFiles, key = { it }) { path ->
                val conflict = path in conflicts
                val deleted = path in snap?.deleted.orEmpty() ||
                    (path in snap?.removed.orEmpty() && path !in snap?.untracked.orEmpty())
                val status = when {
                    conflict -> "冲突"
                    deleted -> "删除"
                    path in snap?.untracked.orEmpty() || path in snap?.added.orEmpty() -> "新增"
                    else -> "修改"
                }
                var fileMenu by remember { mutableStateOf(false) }
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.animateItem()) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (conflict) Icons.Rounded.ErrorOutline else if (deleted) Icons.Rounded.DeleteOutline else Icons.Rounded.Description,
                            null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).clickable(enabled = !vm.busy && !deleted) { vm.openEditor(path) }
                            .padding(horizontal = 12.dp, vertical = 14.dp)) {
                            Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if ('/' in path) Text(path.substringBeforeLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(status + when {
                                path in staged && path in unstaged -> " · 部分暂存"
                                path in staged -> " · 已暂存"
                                else -> ""
                            }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { fileMenu = true }, enabled = !vm.busy) { Icon(Icons.Rounded.MoreVert, "$path 的操作") }
                            DropdownMenu(expanded = fileMenu, onDismissRequest = { fileMenu = false }) {
                                DropdownMenuItem(text = { Text("编辑文件") }, onClick = { fileMenu = false; vm.openEditor(path) }, enabled = !deleted)
                                DropdownMenuItem(text = { Text(if (conflict) "标记已解决" else "暂存") }, onClick = {
                                    fileMenu = false; vm.gitOperation("stage", path)
                                }, enabled = writable && (path in unstaged || conflict))
                                DropdownMenuItem(text = { Text("取消暂存") }, onClick = { fileMenu = false; vm.gitOperation("unstage", path) }, enabled = writable && path in staged)
                                if (!conflict && path in unstaged && path !in snap?.untracked.orEmpty()) {
                                    DropdownMenuItem(text = { Text("撤销未暂存修改") }, onClick = { fileMenu = false; discardPath = path }, enabled = writable)
                                }
                            }
                        }
                    }
                }
            }
            if (!hasChanges && snap != null) item {
                EmptyPanel(Icons.Rounded.CheckCircleOutline, "工作区干净",
                    if (pending != null && pending > 0) "本地提交尚未推送" else "在代码页编辑文件后，修改会显示在这里")
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(visible = hasChanges, enter = expandVertically(tween(180)) + fadeIn(), exit = shrinkVertically(tween(180)) + fadeOut()) {
                    OutlinedTextField(commitText, { commitText = it }, enabled = !vm.busy, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("这次修改了什么？") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { keyboard?.hide() }))
                }
                if (hasChanges || (!snap?.history.isNullOrEmpty() && pending != 0)) {
                    Button(onClick = {
                        keyboard?.hide()
                        if (hasChanges) {
                            val submitted = commitText
                            vm.publishChanges(submitted) { if (commitText == submitted) commitText = "" }
                        } else vm.pushExisting()
                    }, enabled = writable && conflicts.isEmpty() && (!hasChanges || commitText.isNotBlank()),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(when {
                            vm.busy -> "处理中…"
                            conflicts.isNotEmpty() -> "请先解决冲突"
                            hasChanges -> "发布 ${changedFiles.size} 个文件"
                            pending != null && pending > 0 -> "推送 $pending 次提交"
                            else -> "推送已有提交"
                        })
                    }
                    if (hasChanges) Text("发布会暂存全部变更、提交并推送", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showTools) GitToolsPanel(vm) { showTools = false }
    discardPath?.let { path ->
        AlertDialog(onDismissRequest = { discardPath = null }, title = { Text("撤销修改？") },
            text = { Text("用已暂存版本恢复 $path，未暂存内容将丢失。") },
            confirmButton = { TextButton(onClick = { vm.gitOperation("discard", path); discardPath = null }, enabled = writable) { Text("撤销修改") } },
            dismissButton = { TextButton(onClick = { discardPath = null }) { Text("取消") } })
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
    FullScreenPanel(close) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
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

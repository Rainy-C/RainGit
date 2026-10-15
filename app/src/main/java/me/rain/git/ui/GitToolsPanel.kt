package me.rain.git.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun GitToolsPanel(vm: RainViewModel, close: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var input by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    val snap = vm.snapshot
    val enabled = !vm.busy && !vm.downloading && snap != null
    FullScreenPanel(close) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(Icons.Rounded.Close, "关闭 Git 工具") }
                Text("Git 工具", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Box {
                    IconButton(onClick = { menu = true }, enabled = enabled) { Icon(Icons.Rounded.MoreVert, "仓库操作") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("设置远端 origin") }, onClick = { menu = false; input = "remote" })
                        DropdownMenuItem(text = { Text("中止合并") }, onClick = { menu = false; confirm = "abort-merge" to "" })
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp)) { if (vm.busy) LinearProgressIndicator(Modifier.fillMaxSize()) }
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("分支", "标签", "Stash", "历史").forEachIndexed { i, name ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) }, enabled = !vm.busy)
                }
            }
            // Git operations complete behind this panel; expose feedback here as well.
            vm.message?.let { message ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(16.dp, 8.dp), shape = RoundedCornerShape(12.dp)) {
                    Text(message, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            AnimatedContent(targetState = tab, modifier = Modifier.weight(1f), label = "git-tools",
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }) { page ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (page) {
                        0 -> {
                            item { SectionHeading("当前 · ${snap?.branch.orEmpty()}") {
                                TextButton(onClick = { input = "branch" }, enabled = enabled) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("新建") }
                            } }
                            items(snap?.branches.orEmpty(), key = { it }) { branch ->
                                var expanded by remember { mutableStateOf(false) }
                                val current = branch == snap?.branch
                                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (current) Icons.Rounded.Check else Icons.Rounded.AccountTree, null, Modifier.size(18.dp))
                                        Text(branch, Modifier.weight(1f).padding(12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                        Box {
                                            IconButton(onClick = { expanded = true }, enabled = enabled && !current) { Icon(Icons.Rounded.MoreVert, "$branch 的操作") }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                DropdownMenuItem(text = { Text("切换到此分支") }, onClick = { expanded = false; confirm = "checkout" to branch })
                                                DropdownMenuItem(text = { Text("合并到当前分支") }, onClick = { expanded = false; confirm = "merge" to branch })
                                                DropdownMenuItem(text = { Text("删除本地分支") }, onClick = { expanded = false; confirm = "delete-branch" to branch })
                                            }
                                        }
                                    }
                                }
                            }
                            if (snap?.branches.isNullOrEmpty()) item { EmptyPanel(Icons.Rounded.AccountTree, "暂无分支", "首次提交后显示当前分支") }
                        }
                        1 -> {
                            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { input = "tag" }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("新建标签") }
                                OutlinedButton(onClick = { confirm = "push-tags" to "" }, enabled = enabled && !snap?.tags.isNullOrEmpty(), modifier = Modifier.weight(1f)) { Text("推送标签") }
                            } }
                            items(snap?.tags.orEmpty(), key = { it }) { tag ->
                                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Label, null, Modifier.size(18.dp)); Spacer(Modifier.width(12.dp))
                                        Text(tag, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (snap?.tags.isNullOrEmpty()) item { EmptyPanel(Icons.Rounded.Label, "暂无标签", "为当前提交标记版本") }
                        }
                        2 -> {
                            item { SectionHeading("暂存备份") {
                                TextButton(onClick = { vm.gitOperation("stash") }, enabled = enabled) { Text("保存修改") }
                            } }
                            items(snap?.stashes.orEmpty(), key = { it.id }) { stash ->
                                var expanded by remember { mutableStateOf(false) }
                                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                                            Text(stash.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                            Text(stash.id.take(8), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box {
                                            IconButton(onClick = { expanded = true }, enabled = enabled) { Icon(Icons.Rounded.MoreVert, "备份操作") }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                DropdownMenuItem(text = { Text("恢复并保留备份") }, onClick = { expanded = false; vm.gitOperation("restore-stash", stash.id) })
                                                DropdownMenuItem(text = { Text("删除备份") }, onClick = { expanded = false; confirm = "drop-stash" to stash.id })
                                            }
                                        }
                                    }
                                }
                            }
                            if (snap?.stashes.isNullOrEmpty()) item { EmptyPanel(Icons.Rounded.Inventory2, "暂无备份", "临时保存修改，稍后继续") }
                        }
                        else -> {
                            items(snap?.history.orEmpty(), key = { it.substringBefore(' ') }) { line ->
                                Surface(onClick = { vm.showCommit(line.substringBefore(' ')) }, enabled = enabled,
                                    shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(line.substringAfter("  "), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(5.dp))
                                        Text(line.take(8), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (snap?.history.isNullOrEmpty()) item { EmptyPanel(Icons.Rounded.History, "暂无提交", "首次提交后显示历史记录") }
                        }
                    }
                }
            }
        }
        input?.let { operation ->
            TextInputDialog(when (operation) { "branch" -> "新建分支"; "tag" -> "新建标签"; else -> "设置 origin" },
                when (operation) { "branch" -> "分支名"; "tag" -> "标签名，如 v1.3.0"; else -> "GitHub HTTPS 仓库地址" },
                onDismiss = { input = null }, onConfirm = { value ->
                    input = null
                    if (operation == "remote") confirm = operation to value else vm.gitOperation(operation, value)
                })
        }
        confirm?.let { (operation, value) ->
            val title = when (operation) {
                "checkout" -> "切换分支？"
                "merge" -> "合并分支？"
                "delete-branch" -> "删除本地分支？"
                "abort-merge" -> "中止合并？"
                "drop-stash" -> "删除备份？"
                "push-tags" -> "推送本地标签？"
                else -> "更改远端？"
            }
            AlertDialog(onDismissRequest = { confirm = null }, title = { Text(title) }, text = { Text(when (operation) {
                "checkout" -> "切换到 $value。请先提交修改或保存 Stash。"
                "merge" -> "将 $value 合并到 ${snap?.branch.orEmpty()}。"
                "delete-branch" -> "删除 $value，仅允许删除已合并的本地分支。"
                "abort-merge" -> "未提交的合并结果和修改将被丢弃，无法撤销。"
                "drop-stash" -> "此备份将永久删除。"
                "push-tags" -> "将本地标签推送到远端仓库。"
                else -> "后续拉取和推送将使用 $value。"
            }) }, confirmButton = { TextButton(onClick = { vm.gitOperation(operation, value); confirm = null }, enabled = enabled) { Text("确认") } },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } })
        }
    }
}

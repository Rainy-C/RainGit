package me.rain.git.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private enum class LogKind { ERROR, WARNING, STEP, NORMAL }
private data class LogRow(val index: Int, val content: String, val kind: LogKind)

private fun parseLog(text: String): List<LogRow> = text.lineSequence().mapIndexed { index, source ->
    val line = source.take(1200)
    val lower = line.lowercase()
    val kind = when {
        lower.contains("error:") || lower.contains("##[error]") ||
            lower.contains("build failed") || lower.contains("exception:") ||
            lower.contains("failed with") || lower.matches(Regex(".*> task .* failed.*")) -> LogKind.ERROR
        lower.contains("warning:") || lower.contains("##[warning]") || lower.contains("deprecated") -> LogKind.WARNING
        line.startsWith("── ") || lower.contains("> task :") ||
            lower.contains("build successful") || lower.contains("##[group]") -> LogKind.STEP
        else -> LogKind.NORMAL
    }
    LogRow(index, line, kind)
}.take(18000).toList()

@Composable
internal fun BuildLogViewer(raw: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val lines = remember(raw) { parseLog(raw) }
    var search by remember(raw) { mutableStateOf("") }
    var filter by remember(raw) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val errors = remember(lines) { lines.count { it.kind == LogKind.ERROR } }
    val warnings = remember(lines) { lines.count { it.kind == LogKind.WARNING } }
    val shown = remember(lines, search, filter) {
        lines.filter {
            (search.isBlank() || it.content.contains(search, ignoreCase = true)) &&
                (filter == 0 || (filter == 1 && it.kind == LogKind.ERROR) ||
                    (filter == 2 && it.kind == LogKind.WARNING) ||
                    (filter == 3 && it.kind == LogKind.STEP))
        }.take(6000)
    }
    val firstProblem = shown.indexOfFirst { it.kind == LogKind.ERROR }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.98f).fillMaxHeight(.94f),
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("构建日志", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("RainGit Build", raw))
                    }) { Icon(Icons.Rounded.ContentCopy, "复制全部日志") }
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭") }
                }
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${lines.size} 行", fontWeight = FontWeight.SemiBold)
                            Text("已解析日志", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("$errors 错误", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            Text("高优先级", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("$warnings 警告", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                            Text("建议关注", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(15.dp), placeholder = { Text("搜索错误、任务或类名") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (search.isNotEmpty()) {{ IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "清空") } }} else null)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("全部", "错误", "警告", "步骤").forEachIndexed { i, name ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(name) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("显示 ${shown.size} 行${if (lines.size == 18000) " · 日志已截断" else ""}",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (firstProblem >= 0) TextButton(onClick = { scope.launch { listState.animateScrollToItem(firstProblem) } }) {
                        Text("定位首个错误"); Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(16.dp))
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize(), state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)) {
                    itemsIndexed(shown, key = { _, row -> row.index }) { _, line ->
                        val color = when (line.kind) {
                            LogKind.ERROR -> MaterialTheme.colorScheme.error
                            LogKind.WARNING -> MaterialTheme.colorScheme.tertiary
                            LogKind.STEP -> MaterialTheme.colorScheme.primary
                            LogKind.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(color = if (line.kind == LogKind.NORMAL) Color.Transparent
                            else color.copy(alpha = .075f), shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.Top) {
                                Text("${line.index + 1}", Modifier.width(42.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer {
                                    Text(line.content, Modifier.weight(1f), maxLines = 10,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace,
                                            fontWeight = if (line.kind == LogKind.ERROR) FontWeight.SemiBold else FontWeight.Normal),
                                        color = if (line.kind == LogKind.NORMAL) MaterialTheme.colorScheme.onSurface else color)
                                }
                            }
                        }
                    }
                    if (shown.isEmpty()) item {
                        EmptyPanel(Icons.Rounded.SearchOff, "没有匹配的日志", "试试其他关键字或筛选项")
                    }
                }
            }
        }
    }
}

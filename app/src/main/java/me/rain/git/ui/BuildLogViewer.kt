package me.rain.git.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.DragInteraction
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

private enum class LogKind { ERROR, WARNING, STEP, NORMAL }
private data class LogRow(val index: Int, val content: String, val kind: LogKind)

private val ansiEscape = Regex("\u001B\\[[0-9;]*[A-Za-z]")

private fun parseLog(text: String): List<LogRow> {
    val rows = java.util.ArrayDeque<LogRow>(18000)
    text.lineSequence().forEachIndexed { index, source ->
        val line = source.replace(ansiEscape, "").take(1200)
        val lower = line.lowercase()
        val kind = when {
            lower.startsWith("e: ") || lower.contains(" e: ") || lower.contains("error:") || lower.contains("##[error]") ||
                lower.contains("build failed") || lower.contains("exception:") || lower.contains("failed with") ||
                (lower.contains("> task ") && lower.contains(" failed")) -> LogKind.ERROR
            lower.contains("warning:") || lower.contains("##[warning]") || lower.contains("deprecated") -> LogKind.WARNING
            line.startsWith("── ") || lower.contains("> task :") || lower.contains("build successful") || lower.contains("##[group]") -> LogKind.STEP
            else -> LogKind.NORMAL
        }
        if (rows.size == 18000) rows.removeFirst()
        rows.addLast(LogRow(index, line, kind))
    }
    return rows.toList()
}

@Composable
internal fun BuildLogViewer(raw: String, onClose: () -> Unit, status: String = "", loading: Boolean = false,
    refresh: () -> Unit = {}, openWeb: () -> Unit = {}) {
    val context = LocalContext.current
    val lines = remember(raw) { parseLog(raw) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
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
        }.takeLast(6000)
    }
    var followTail by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followTail = false
        }
    }
    LaunchedEffect(raw, filter, search, followTail) {
        if (followTail && search.isBlank() && shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex)
    }
    val firstProblem = shown.indexOfFirst { it.kind == LogKind.ERROR }
    FullScreenPanel(onClose) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("构建日志", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("RainGit Build", raw.takeLast(200_000)))
                    }) { Icon(Icons.Rounded.ContentCopy, "复制日志（最多末尾 20 万字符）") }
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = refresh, enabled = !loading) { Icon(Icons.Rounded.Refresh, "刷新日志") }
                    IconButton(onClick = openWeb) { Icon(Icons.Rounded.OpenInNew, "网页实时日志") }
                }
                Box(Modifier.fillMaxWidth().height(3.dp)) { if (loading) LinearProgressIndicator(Modifier.fillMaxSize()) }
                Spacer(Modifier.height(8.dp))
                Text("${lines.size} 行 · $errors 错误 · $warnings 警告",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(15.dp), placeholder = { Text("搜索错误、任务或类名") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (search.isNotEmpty()) {{ IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "清空") } }} else null)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("全部", "错误", "警告", "步骤").forEachIndexed { i, name ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(name) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("显示 ${shown.size} 行${if (lines.size == 18000) " · 显示末尾日志" else ""}",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconToggleButton(checked = followTail, onCheckedChange = { followTail = it }) {
                        Icon(Icons.Rounded.VerticalAlignBottom, "跟随最新日志")
                    }
                    if (firstProblem >= 0) IconButton(onClick = { followTail = false; scope.launch { listState.animateScrollToItem(firstProblem) } }) {
                        Icon(Icons.Rounded.ErrorOutline, "定位首个错误")
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

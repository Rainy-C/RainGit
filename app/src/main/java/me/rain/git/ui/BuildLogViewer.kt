package me.rain.git.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LogViewFilter { SUMMARY, ALL, ERRORS, WARNINGS }

@Composable
internal fun BuildLogViewer(
    raw: String,
    onClose: () -> Unit,
    status: String = "",
    loading: Boolean = false,
    refresh: () -> Unit = {},
    openWeb: () -> Unit = {}
) {
    val context = LocalContext.current
    val document by produceState(BuildLogDocument(emptyList()), raw) {
        value = withContext(Dispatchers.Default) { parseBuildLog(raw) }
    }
    var filter by remember { mutableStateOf(LogViewFilter.SUMMARY) }
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var followTail by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visible by produceState(emptyList<BuildLogRow>(), document, filter, search) {
        value = withContext(Dispatchers.Default) {
            val seenWarnings = HashSet<String>()
            document.rows.filter { row ->
                val passes = when (filter) {
                    LogViewFilter.SUMMARY -> row.kind == LogKind.ERROR || row.kind == LogKind.WARNING ||
                        (row.kind == LogKind.STEP && !row.detail)
                    LogViewFilter.ALL -> true
                    LogViewFilter.ERRORS -> row.kind == LogKind.ERROR
                    LogViewFilter.WARNINGS -> row.kind == LogKind.WARNING
                }
                passes && (search.isBlank() || listOf(row.content, row.location, row.section, row.explanation)
                    .any { it.contains(search, ignoreCase = true) }) &&
                    (filter != LogViewFilter.SUMMARY || row.kind != LogKind.WARNING ||
                        seenWarnings.add(row.content.trim().lowercase()))
            }
        }
    }

    fun copy(text: String) {
        val limited = text.takeLast(250_000)
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("RainGit 日志", limited))
        Toast.makeText(context, if (text.length > limited.length) "已复制最后 25 万字符" else "已复制", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(filter, search) { followTail = false; listState.scrollToItem(0) }
    LaunchedEffect(visible.size, followTail) {
        if (followTail && visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex + if (document.omitted > 0) 1 else 0)
    }

    FullScreenPanel(onClose) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭日志") }
                Text("构建日志", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) search = "" }) {
                    Icon(Icons.Rounded.Search, "搜索")
                }
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("刷新") }, enabled = !loading, onClick = { more = false; refresh() })
                        DropdownMenuItem(text = { Text(if (showTime) "隐藏时间" else "显示时间") }, onClick = { more = false; showTime = !showTime })
                        DropdownMenuItem(text = { Text("复制当前内容") }, onClick = {
                            more = false
                            scope.launch {
                                copy(withContext(Dispatchers.Default) { visible.joinToString("\n") { it.original } })
                            }
                        })
                        DropdownMenuItem(text = { Text("复制完整原文") }, onClick = { more = false; copy(raw) })
                        DropdownMenuItem(text = { Text("GitHub 原始日志") }, onClick = { more = false; openWeb() })
                    }
                }
            }
            Text(status.ifBlank { if (loading) "正在同步日志" else "已同步" },
                Modifier.padding(start = 18.dp, end = 18.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WaveLoadingBar(loading, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${document.rows.size} 行", style = MaterialTheme.typography.labelMedium)
                Text("${document.errors} 错误", style = MaterialTheme.typography.labelMedium,
                    color = if (document.errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${document.warnings} 警告", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showSearch) {
                OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("搜索错误、文件名、任务") }, singleLine = true,
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "清除") } })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(LogViewFilter.SUMMARY to "重点", LogViewFilter.ALL to "完整输出",
                    LogViewFilter.ERRORS to "错误", LogViewFilter.WARNINGS to "警告").forEach { (type, title) ->
                    FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(title) })
                }
            }
            if (document.firstError != null && filter != LogViewFilter.WARNINGS) {
                TextButton(onClick = {
                    filter = LogViewFilter.ERRORS
                    search = ""
                    followTail = false
                }, modifier = Modifier.padding(start = 9.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("查看首个错误", style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(vertical = 9.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (document.omitted > 0) item {
                    Text("更早的 ${document.omitted} 行已折叠，请在 GitHub 查看原文。",
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                items(visible, key = { it.index }, contentType = { it.kind }) { row ->
                    LogLine(row, showTime, ::copy)
                }
                if (visible.isEmpty()) item {
                    EmptyPanel(Icons.Rounded.Terminal, if (loading) "正在等待输出" else "没有匹配记录",
                        if (filter == LogViewFilter.SUMMARY) "点击「完整输出」查看所有命令与步骤" else "尝试其他筛选条件")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("显示 ${visible.size} 行", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { followTail = !followTail }) {
                    Icon(if (followTail) Icons.Rounded.Pause else Icons.Rounded.VerticalAlignBottom, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (followTail) "停止跟随" else "跟随最新")
                }
            }
        }
    }
}

@Composable
private fun LogLine(row: BuildLogRow, showTime: Boolean, copy: (String) -> Unit) {
    var expanded by remember(row.index) { mutableStateOf(false) }
    val critical = row.kind == LogKind.ERROR
    val warning = row.kind == LogKind.WARNING
    val step = row.kind == LogKind.STEP
    val colors = MaterialTheme.colorScheme
    val surface = when {
        critical -> colors.errorContainer.copy(alpha = .55f)
        warning -> colors.surfaceContainerHigh
        step -> colors.surfaceContainerLow
        else -> colors.background
    }
    Surface(color = surface, shape = RoundedCornerShape(if (critical || warning) 10.dp else 5.dp)) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }
            .padding(horizontal = 11.dp, vertical = if (critical || warning) 10.dp else if (step) 9.dp else 5.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(when {
                    critical -> Icons.Rounded.ErrorOutline
                    warning -> Icons.Rounded.WarningAmber
                    step -> Icons.Rounded.ChevronRight
                    else -> Icons.Rounded.SubdirectoryArrowRight
                }, null, Modifier.padding(top = 2.dp).size(15.dp),
                    tint = if (critical) colors.error else colors.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (critical || warning) {
                            Text(if (critical) "错误" else "警告", style = MaterialTheme.typography.labelSmall,
                                color = if (critical) colors.error else colors.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (row.location.isNotBlank()) {
                            Text(row.location, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                        } else Spacer(Modifier.weight(1f))
                        if (showTime && row.time.isNotEmpty()) Text(row.time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    Text(row.content, style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = if (step) FontFamily.Default else FontFamily.Monospace,
                        fontSize = if (step) 14.sp else 12.sp,
                        lineHeight = if (step) 21.sp else 18.sp,
                        fontWeight = if (step || critical) FontWeight.Medium else FontWeight.Normal),
                        maxLines = if (expanded || critical) Int.MAX_VALUE else if (warning) 5 else if (step) 2 else 3,
                        overflow = TextOverflow.Ellipsis)
                    if (row.explanation.isNotBlank()) Text(row.explanation, Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(7.dp))
                HorizontalDivider(color = colors.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.section.ifBlank { "原始行" }, Modifier.weight(1f), maxLines = 1,
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    IconButton(onClick = { copy(row.original) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.ContentCopy, "复制原文", Modifier.size(17.dp))
                    }
                }
                SelectionContainer {
                    Text(cleanBuildLogText(row.original), style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

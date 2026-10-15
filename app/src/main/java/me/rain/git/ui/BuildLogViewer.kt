package me.rain.git.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LogFilter { SUMMARY, ALL, ERROR, WARNING }

@Composable
internal fun BuildLogViewer(raw: String, onClose: () -> Unit, status: String = "", loading: Boolean = false,
    refresh: () -> Unit = {}, openWeb: () -> Unit = {}) {
    val context = LocalContext.current
    val document by produceState(BuildLogDocument(emptyList()), raw) {
        value = withContext(Dispatchers.Default) { parseBuildLog(raw) }
    }
    var search by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(LogFilter.SUMMARY) }
    var showTime by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var followTail by remember { mutableStateOf(false) }
    val shown by produceState(emptyList<BuildLogRow>(), document, search, filter) {
        value = withContext(Dispatchers.Default) {
            document.rows.filter { row ->
                val matches = search.isBlank() || listOf(row.content, row.location, row.section, row.explanation)
                    .any { it.contains(search, ignoreCase = true) }
                matches && when (filter) {
                    LogFilter.SUMMARY -> row.kind == LogKind.ERROR || row.kind == LogKind.WARNING ||
                        (row.kind == LogKind.STEP && !row.detail)
                    LogFilter.ALL -> true
                    LogFilter.ERROR -> row.kind == LogKind.ERROR
                    LogFilter.WARNING -> row.kind == LogKind.WARNING
                }
            }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("RainGit 日志", text.takeLast(200_000)))
        Toast.makeText(context, if (text.length > 200_000) "已复制末尾 20 万字符" else "已复制", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) followTail = false }
    }
    LaunchedEffect(shown, followTail) {
        if (followTail && search.isBlank() && shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex + if (document.omitted > 0) 1 else 0)
    }
    FullScreenPanel(onClose) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭日志") }
                Text("任务日志", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { searching = !searching; if (!searching) search = "" }) {
                    Icon(Icons.Rounded.Search, "搜索日志")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "更多操作") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("刷新日志") }, enabled = !loading, onClick = { menu = false; refresh() })
                        DropdownMenuItem(text = { Text(if (showTime) "隐藏时间" else "显示时间") }, onClick = { showTime = !showTime; menu = false })
                        DropdownMenuItem(text = { Text("复制当前内容") }, onClick = {
                            menu = false
                            scope.launch {
                                val text = withContext(Dispatchers.Default) { shown.joinToString("\n") {
                                    listOf(it.location, it.content, it.explanation).filter(String::isNotBlank).joinToString(" · ")
                                } }
                                copy(text)
                            }
                        })
                        DropdownMenuItem(text = { Text("复制原始日志") }, onClick = { menu = false; copy(raw) })
                        DropdownMenuItem(text = { Text("在 GitHub 查看") }, onClick = { menu = false; openWeb() })
                    }
                }
            }
            Text(status.ifBlank { if (loading) "正在获取日志…" else "关键步骤与问题摘要" },
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().height(2.dp)) { if (loading) LinearProgressIndicator(Modifier.fillMaxSize()) }
            AnimatedVisibility(searching) {
                OutlinedTextField(search, { search = it; followTail = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true, shape = RoundedCornerShape(12.dp), placeholder = { Text("搜索内容或文件名") },
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "清空搜索") } })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(LogFilter.SUMMARY to "摘要", LogFilter.ALL to "全部", LogFilter.ERROR to "错误 ${document.errors}",
                    LogFilter.WARNING to "警告 ${document.warnings}").forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = {
                        filter = value; followTail = false
                        scope.launch { listState.scrollToItem(0) }
                    }, label = { Text(label) })
                }
            }
            if (document.firstError != null && filter != LogFilter.WARNING) {
                TextButton(onClick = {
                    val target = shown.indexOfFirst { it.index == document.firstError?.index }
                    followTail = false
                    if (target >= 0) scope.launch { listState.animateScrollToItem(target + if (document.omitted > 0) 1 else 0) }
                    else { search = ""; filter = LogFilter.ERROR }
                }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("定位错误${document.firstError?.location?.takeIf(String::isNotEmpty)?.let { " · $it" }.orEmpty()}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (document.omitted > 0) item {
                    Text("较早的 ${document.omitted} 行未展示，可在 GitHub 查看完整日志。", style = MaterialTheme.typography.labelSmall)
                }
                items(shown, key = { it.index }, contentType = { it.kind }) { row ->
                    LogEntry(row, showTime, ::copy)
                }
                if (shown.isEmpty()) item {
                    EmptyPanel(Icons.Rounded.Terminal, if (loading) "等待日志" else "暂无匹配内容",
                        if (filter == LogFilter.SUMMARY) "切换「全部」查看完整输出" else "试试其他关键字或筛选项")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${shown.size} 行 · 点击展开原文", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { followTail = !followTail }) {
                    Icon(if (followTail) Icons.Rounded.Pause else Icons.Rounded.VerticalAlignBottom, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (followTail) "停止跟随" else "跟随最新")
                }
            }
        }
    }
}

@Composable
private fun LogEntry(row: BuildLogRow, showTime: Boolean, copy: (String) -> Unit) {
    var expanded by remember(row.index, row.original) { mutableStateOf(false) }
    val issue = row.kind == LogKind.ERROR || row.kind == LogKind.WARNING
    val colors = MaterialTheme.colorScheme
    Surface(color = when {
        issue -> colors.surfaceContainerHigh
        row.kind == LogKind.STEP -> colors.surfaceContainerLow
        else -> colors.surface
    }, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().animateContentSize(tween(160)).clickable { expanded = !expanded }.padding(12.dp)) {
            if (issue || row.location.isNotEmpty() || (showTime && row.time.isNotEmpty())) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (issue) {
                        Icon(if (row.kind == LogKind.ERROR) Icons.Rounded.ErrorOutline else Icons.Rounded.WarningAmber,
                            null, Modifier.size(16.dp), tint = colors.onSurface)
                        Text(if (row.kind == LogKind.ERROR) "错误" else "警告", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    if (row.location.isNotEmpty()) Text(row.location, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace)
                    if (showTime && row.time.isNotEmpty()) Text(row.time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(row.content, style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (row.explanation.isNotEmpty() || row.kind == LogKind.STEP) FontFamily.Default else FontFamily.Monospace,
                fontSize = 14.sp, lineHeight = 22.sp,
                fontWeight = if (issue || row.kind == LogKind.STEP) FontWeight.Medium else FontWeight.Normal),
                color = colors.onSurface, maxLines = if (issue || expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
            if (row.explanation.isNotEmpty()) Text(row.explanation, Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = colors.onSurfaceVariant)
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = colors.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.section.ifBlank { "原始日志" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { copy(row.original) }) { Icon(Icons.Rounded.ContentCopy, "复制这一行", Modifier.size(18.dp)) }
                }
                SelectionContainer {
                    Text(cleanBuildLogText(row.original), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                        color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

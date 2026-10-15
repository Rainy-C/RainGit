package me.rain.git.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import me.rain.git.BuildConfig
import me.rain.git.R
import java.io.File

@Composable
internal fun TextInputDialog(title: String, label: String, description: String = "", initial: String = "",
    onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun DownloadedFileCard(path: String) {
    val context = LocalContext.current
    var error by remember(path) { mutableStateOf<String?>(null) }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("下载完成", fontWeight = FontWeight.SemiBold)
            Text(path, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = {
                    runCatching {
                        val file = File(path)
                        check(file.isFile) { "文件已被移动或删除" }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val mime = if (path.endsWith(".apk", true)) "application/vnd.android.package-archive" else "application/zip"
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }.onFailure { error = it.message ?: "没有可打开此文件的应用" }
                }) { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("打开文件") }
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("下载路径", path))
                }) { Text("复制路径") }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private data class OpenSource(val name: String, val license: String, val url: String)

@Composable
internal fun AboutDialog(openUrl: (String) -> Unit, close: () -> Unit) {
    val projects = remember { listOf(
        OpenSource("AndroidX · Compose · Material 3", "Apache License 2.0", "https://android.googlesource.com/platform/frameworks/support/"),
        OpenSource("Kotlin", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
        OpenSource("Kotlin Coroutines", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSource("Eclipse JGit", "Eclipse Distribution License 1.0 (BSD)", "https://github.com/eclipse-jgit/jgit"),
        OpenSource("OkHttp · Okio", "Apache License 2.0", "https://github.com/square/okhttp"),
        OpenSource("SLF4J", "MIT License", "https://github.com/qos-ch/slf4j"),
        OpenSource("Desugar JDK Libraries", "GPLv2 + Classpath Exception 等上游许可", "https://github.com/google/desugar_jdk_libs")
    ) }
    FullScreenPanel(close) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("关于", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = close) { Icon(Icons.Rounded.Close, "关闭") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(R.drawable.ic_rain_mark), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("RainGit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    item {
                        SectionHeading("作者", "Rainy-C")
                        TextButton(onClick = { openUrl("https://github.com/Rainy-C") }) { Text("GitHub · Rainy-C"); Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp)) }
                        FilledTonalButton(onClick = { openUrl("https://github.com/Rainy-C/RainGit") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Code, null); Spacer(Modifier.width(8.dp)); Text("RainGit 开源仓库")
                        }
                    }
                    item { HorizontalDivider(); SectionHeading("开源致谢", "点击项目查看源码与许可") }
                    items(projects) { project ->
                        Surface(onClick = { openUrl(project.url) }, shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(project.name, fontWeight = FontWeight.Medium)
                                Text(project.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditorPanel(vm: RainViewModel, path: String) {
    var dirty by rememberSaveable(path) { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val requestClose: () -> Unit = { if (dirty) confirmClose = true else vm.closeEditor() }
    FullScreenPanel(requestClose) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = requestClose, enabled = !vm.busy) { Icon(Icons.Rounded.Close, "关闭编辑器") }
                Text(path.substringAfterLast('/'), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = vm::saveEditor, enabled = dirty && !vm.busy) { Text(if (vm.busy) "保存中…" else "保存") }
            }
            if ('/' in path) Text(path.substringBeforeLast('/'), Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(vm.editorText, { vm.editText(it); dirty = true }, enabled = !vm.busy,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false))
        }
        if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("放弃未保存内容？") },
            confirmButton = { TextButton(onClick = vm::closeEditor, enabled = !vm.busy) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("继续编辑") } })
    }
}

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
internal fun ExtraGitTools(vm: RainViewModel) {
    var input by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    val snapshot = vm.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("分支与远端")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { input = "merge" }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("合并分支") }
            OutlinedButton(onClick = { input = "delete-branch" }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("删除分支") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { input = "remote" }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("设置 origin") }
            OutlinedButton(onClick = { confirm = "abort-merge" to "" }, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("中止合并") }
        }
        SectionHeading("Stash 备份", "恢复后保留备份，需要时再删除")
        snapshot?.stashes.orEmpty().forEachIndexed { index, stash ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("stash@{$index}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(stash.name, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = { vm.gitOperation("restore-stash", stash.id) }, enabled = !vm.busy) { Text("恢复") }
                        TextButton(onClick = { confirm = "drop-stash" to stash.id }, enabled = !vm.busy) { Text("删除备份") }
                    }
                }
            }
        }
        if (snapshot?.stashes.isNullOrEmpty()) Text("暂无 Stash", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!snapshot?.tags.isNullOrEmpty()) Text("本地标签：${snapshot?.tags.orEmpty().joinToString(" · ")}",
            style = MaterialTheme.typography.bodySmall)
    }
    input?.let { operation ->
        val title = when (operation) { "merge" -> "合并到当前分支"; "delete-branch" -> "删除本地分支"; else -> "设置远端 origin" }
        val hint = if (operation == "remote") "https://github.com/用户/仓库.git" else "分支名称"
        val description = when (operation) {
            "merge" -> "将目标分支合并到 ${snapshot?.branch.orEmpty()}。请先提交修改或保存 Stash。"
            "delete-branch" -> "仅删除本地已合并分支；不会强制删除，也不会删除 GitHub 分支。"
            else -> "此地址将用于后续拉取和推送。仅支持 GitHub HTTPS 地址。"
        }
        TextInputDialog(title, hint, description, onDismiss = { input = null }) {
            confirm = operation to it; input = null
        }
    }
    confirm?.let { (operation, value) ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("确认 Git 操作") },
            text = { Text(when (operation) {
                "abort-merge" -> "将重置到当前 HEAD，丢弃本次合并结果及合并期间的未提交修改。此操作无法撤销。"
                "drop-stash" -> "永久删除选中的 Stash 备份。此操作无法撤销。"
                "merge" -> "将 $value 合并到 ${snapshot?.branch.orEmpty()}。"
                "delete-branch" -> "删除本地分支 $value。"
                else -> "把 origin 改为 $value，后续推送将使用此地址。"
            }) },
            confirmButton = { TextButton(onClick = { vm.gitOperation(operation, value); confirm = null }, enabled = !vm.busy) { Text("确认") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } })
    }
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
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.88f), shape = RoundedCornerShape(28.dp)) {
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

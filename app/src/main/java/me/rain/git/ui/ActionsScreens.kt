package me.rain.git.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rain.git.github.Artifact
import me.rain.git.github.Run
import me.rain.git.github.Workflow

@Composable
internal fun ActionsScreen(vm: RainViewModel, export: (Artifact, Boolean) -> Unit, openUrl: (String) -> Unit) {
    if (vm.selectedRepo == null) { EmptyPanel(Icons.Rounded.Terminal, "仅支持 GitHub 仓库", "请选择关联 GitHub 的仓库"); return }
    val chosen = vm.chosenRun
    if (chosen != null) {
        RunScreen(vm, chosen, export, openUrl)
    } else {
        var advancedWorkflow by remember(vm.selectedRepo) { mutableStateOf<Workflow?>(null) }
        var filter by remember { mutableIntStateOf(0) }
        val visibleRuns = vm.runs.filter { run ->
            when (filter) {
                1 -> run.conclusion == "failure" || run.conclusion == "timed_out"
                2 -> run.status != "completed"
                3 -> run.conclusion == "success"
                else -> true
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionHeading("工作流", "默认分支一键运行，参数按需设置") {
                IconButton(onClick = vm::refreshActions, enabled = !vm.busy) { Icon(Icons.Rounded.Refresh, "刷新构建") }
            } }
            items(vm.workflows, key = { "workflow-${it.id}" }) { workflow ->
                val active = workflow.state == "active"
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayCircleOutline, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(workflow.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (active) "默认分支 · ${vm.selectedRepo?.defaultBranch.orEmpty()}" else "已禁用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { advancedWorkflow = workflow }, enabled = active && !vm.busy) { Icon(Icons.Rounded.Tune, "参数") }
                        Button(onClick = { vm.dispatch(workflow, vm.selectedRepo?.defaultBranch.orEmpty(), "") }, enabled = active && !vm.busy,
                            contentPadding = PaddingValues(horizontal = 12.dp)) { Text("运行") }
                    }
                }
            }
            if (vm.workflows.isEmpty()) item {
                EmptyPanel(Icons.Rounded.Terminal, "还没有发现工作流", "检查 .github/workflows 是否存在，或刷新列表")
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeading("最近构建", "最新 ${vm.runs.size} 次运行")
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("全部", "失败", "进行中", "成功").forEachIndexed { index, title ->
                        FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(title) })
                    }
                }
            }
            items(visibleRuns, key = { "run-${it.id}" }) { run ->
                RunTile(run, onClick = { vm.chooseRun(run) }, onLogs = { vm.openRunLogs(run) }, enabled = !vm.busy)
            }
            if (visibleRuns.isEmpty()) item { EmptyPanel(Icons.Rounded.History, "暂无匹配的构建", "可以切换状态筛选或刷新列表") }
        }
        advancedWorkflow?.let { w ->
            var ref by remember(w.id) { mutableStateOf(vm.selectedRepo?.defaultBranch.orEmpty()) }
            var inputs by remember(w.id) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { advancedWorkflow = null },
                title = { Text("运行 · ${w.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(ref, { ref = it }, label = { Text("分支或 Git ref") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(inputs, { inputs = it }, label = { Text("Inputs JSON（可留空）") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                        Text("工作流需支持 workflow_dispatch。需要输入参数的工作流请填写 JSON。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.dispatch(w, ref, inputs); advancedWorkflow = null }, enabled = ref.isNotBlank() && !vm.busy) { Text("开始运行") } },
                dismissButton = { TextButton(onClick = { advancedWorkflow = null }) { Text("取消") } }
            )
        }
    }
}

@Composable
private fun RunTile(run: Run, onClick: () -> Unit, onLogs: () -> Unit, enabled: Boolean) {
    val look = runAppearance(run.status, run.conclusion)
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(look.icon, null, Modifier.size(23.dp), tint = look.color)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(run.name.ifBlank { "工作流" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("#${run.number}  ·  ${run.branch}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StateLabel(look.name, look.color)
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(run.created.take(16).replace('T', ' '), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onLogs, enabled = enabled && run.status == "completed") {
                    Icon(Icons.Rounded.Description, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("日志")
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RunScreen(vm: RainViewModel, run: Run, export: (Artifact, Boolean) -> Unit, openUrl: (String) -> Unit) {
    val look = runAppearance(run.status, run.conclusion)
    var expanded by remember(run.id) { mutableStateOf<Set<Long>>(emptySet()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = vm::backRun) { Icon(Icons.Rounded.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("返回构建记录") }
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(look.icon, null, Modifier.size(30.dp), tint = look.color)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(run.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("#${run.number}  ·  ${run.branch}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StateLabel(look.name, look.color)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::readLogs, enabled = !vm.busy && run.status == "completed", modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Description, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("查看日志")
                        }
                        FilledTonalButton(onClick = { vm.controlRun("rerun") }, enabled = !vm.busy && run.status == "completed", modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("重新运行")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.controlRun("rerun-failed-jobs") }, enabled = !vm.busy && run.conclusion == "failure") { Text("只重跑失败任务") }
                        Spacer(Modifier.weight(1f))
                        if (run.status != "completed") TextButton(onClick = { vm.controlRun("cancel") }, enabled = !vm.busy) { Text("取消运行") }
                        IconButton(onClick = { openUrl(run.url) }) { Icon(Icons.Rounded.OpenInNew, "浏览器查看") }
                    }
                }
            }
        }
        item { SectionHeading("任务步骤", "${vm.jobs.size} 个 Jobs · 点击可展开详细 Steps") }
        items(vm.jobs, key = { "job-${it.id}" }) { job ->
            val state = runAppearance(job.status, job.conclusion)
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { expanded = if (job.id in expanded) expanded - job.id else expanded + job.id }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(state.icon, null, Modifier.size(20.dp), tint = state.color)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(state.name, style = MaterialTheme.typography.labelSmall, color = state.color)
                        }
                        Icon(if (job.id in expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    if (job.id in expanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        job.steps.forEach { step ->
                            val stepLook = runAppearance(step.status, step.conclusion)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(stepLook.icon, null, Modifier.size(16.dp), tint = stepLook.color)
                                Spacer(Modifier.width(10.dp))
                                Text(step.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(stepLook.name, style = MaterialTheme.typography.labelSmall, color = stepLook.color)
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeading("构建产物", "${vm.artifacts.size} 个 Artifacts · 优先提取 APK") }
        items(vm.artifacts, key = { "artifact-${it.id}" }) { artifact ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artifact.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            Text("${artifact.size / 1024} KB${if (artifact.expired) " · 已过期" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(onClick = { export(artifact, true) }, modifier = Modifier.weight(1f), enabled = !vm.busy && !artifact.expired) {
                            Icon(Icons.Rounded.Android, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("下载 APK")
                        }
                        OutlinedButton(onClick = { export(artifact, false) }, modifier = Modifier.weight(1f), enabled = !vm.busy && !artifact.expired) { Text("保存 ZIP") }
                    }
                }
            }
        }
        if (vm.artifacts.isEmpty()) item { EmptyPanel(Icons.Rounded.Archive, "暂无构建产物", "工作流成功上传 Artifact 后将显示在这里") }
    }
}

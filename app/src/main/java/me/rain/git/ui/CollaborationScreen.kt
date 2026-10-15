package me.rain.git.ui

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

@Composable
internal fun CollaborationScreen(vm: RainViewModel, openUrl: (String) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("协作", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Pull Requests 与 Issues", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = vm::refreshCollaboration, enabled = !vm.busy) { Icon(Icons.Rounded.Refresh, "刷新") }
            FilledTonalButton(onClick = { showCreate = true }, enabled = vm.collaborationFilter == 1 && !vm.busy) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("Issue")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vm.collaborationFilter == 0, onClick = { vm.setCollaborationFilter(0) }, label = { Text("PR") })
            FilterChip(selected = vm.collaborationFilter == 1, onClick = { vm.setCollaborationFilter(1) }, label = { Text("Issues") })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(vm.collaboration, key = { "collab-${vm.collaborationFilter}-${it.number}" }) { item ->
                Surface(onClick = { openUrl(item.url) }, shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (vm.collaborationFilter == 0) Icons.Rounded.CallMerge else Icons.Rounded.Adjust,
                            null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text("#${item.number} · @${item.author}${if (item.draft) " · Draft" else ""}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
            if (vm.collaboration.isEmpty()) item { EmptyPanel(Icons.Rounded.AllInbox, "暂无开放中的条目", "下拉筛选或点击刷新") }
        }
    }
    if (showCreate) AlertDialog(onDismissRequest = { showCreate = false },
        title = { Text("新建 Issue") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text("描述（可选）") },
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7)
        } },
        confirmButton = { TextButton(onClick = { vm.newIssue(title, body); showCreate = false; title = ""; body = "" },
            enabled = title.isNotBlank() && !vm.busy) { Text("创建") } },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } })
}

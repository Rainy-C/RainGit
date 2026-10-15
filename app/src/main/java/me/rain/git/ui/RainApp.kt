package me.rain.git.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rain.git.github.Artifact

private val forest = Color(0xFF375A48)
private val amber = Color(0xFF946039)
private val lightColors = lightColorScheme(
    primary = forest, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8DD), onPrimaryContainer = Color(0xFF244634),
    secondary = amber, secondaryContainer = Color(0xFFF4E4D7), onSecondaryContainer = Color(0xFF56361F),
    background = Color(0xFFF7F8F4), onBackground = Color(0xFF222B25),
    surface = Color(0xFFFEFEFB), onSurface = Color(0xFF222B25),
    surfaceVariant = Color(0xFFE9EEE7), onSurfaceVariant = Color(0xFF59675D),
    outlineVariant = Color(0xFFDCE4DA), outline = Color(0xFF7F9084),
    surfaceContainer = Color(0xFFF2F4EF), surfaceContainerLow = Color(0xFFFAFCF8),
    surfaceContainerHigh = Color(0xFFEEF1EB), surfaceContainerHighest = Color(0xFFE9EEE7)
)
private val darkColors = darkColorScheme(
    primary = Color(0xFFB3D1BA), onPrimary = Color(0xFF183725),
    primaryContainer = Color(0xFF2D4D37), onPrimaryContainer = Color(0xFFD1E7D6),
    secondaryContainer = Color(0xFF533B2A), onSecondaryContainer = Color(0xFFFFDAC0),
    secondary = Color(0xFFE3B28A), background = Color(0xFF111713),
    onBackground = Color(0xFFE3EAE2), surface = Color(0xFF1A221C),
    onSurface = Color(0xFFE3EAE2), surfaceVariant = Color(0xFF28342C),
    onSurfaceVariant = Color(0xFFB3C2B6), outlineVariant = Color(0xFF344438),
    outline = Color(0xFF7C8E7E), surfaceContainer = Color(0xFF1F2821),
    surfaceContainerLow = Color(0xFF19211B), surfaceContainerHigh = Color(0xFF29352C),
    surfaceContainerHighest = Color(0xFF303D32)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RainApp(
    model: RainViewModel,
    onChooseDirectory: () -> Unit,
    onGrantStorage: () -> Unit,
    onExportArtifact: (Artifact, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColors else lightColors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(17.dp), large = RoundedCornerShape(23.dp), extraLarge = RoundedCornerShape(28.dp))
    ) {
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(model.message) {
            val message = model.message ?: return@LaunchedEffect
            model.clearMessage()
            snack.showSnackbar(message, withDismissAction = true)
        }
        val loggedIn = model.account != null
        BackHandler(enabled = loggedIn && model.screen != "home") {
            if (model.screen == "detail") {
                if (model.detailTab == 2 && model.chosenRun != null) model.backRun()
                else model.closeDetail()
            } else model.navigate("home")
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                if (loggedIn) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        navigationIcon = {
                            if (model.screen == "detail") IconButton(onClick = {
                                if (model.detailTab == 2 && model.chosenRun != null) model.backRun()
                                else model.closeDetail()
                            }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回仓库")
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    if (model.screen == "detail") model.selectedRepo?.name ?: model.selectedDir?.name ?: "本地仓库"
                                    else when (model.screen) { "home" -> "RainGit"; "local" -> "本地仓库"; "actions" -> "构建中心"; else -> "设置" },
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (model.screen == "detail") {
                                    Text(model.selectedRepo?.owner ?: "Local Git", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (model.screen == "home") {
                                    Text("@${model.account?.login.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        actions = {
                            if (model.screen != "settings") {
                                IconButton(onClick = {
                                    when (model.screen) {
                                        "detail" -> if (model.detailTab == 2) model.refreshActions() else model.refreshLocal()
                                        "local" -> model.reloadLocal()
                                        else -> model.refreshRepos()
                                    }
                                }, enabled = !model.busy) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新") }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (loggedIn && model.screen != "detail") {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        listOf(
                            Triple("home", "仓库", Icons.Rounded.Folder),
                            Triple("local", "本地", Icons.Rounded.Code),
                            Triple("actions", "构建", Icons.Rounded.PlayArrow),
                            Triple("settings", "设置", Icons.Rounded.Settings)
                        ).forEach { (page, label, icon) ->
                            NavigationBarItem(
                                selected = model.screen == page, onClick = { model.navigate(page) },
                                icon = { Icon(icon, contentDescription = null) }, label = { Text(label) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!loggedIn) LoginScreen(model, onOpenUrl)
                else when (model.screen) {
                    "home" -> RemoteScreen(model, actionsOnly = false)
                    "local" -> LocalScreen(model)
                    "actions" -> RemoteScreen(model, actionsOnly = true)
                    "settings" -> SettingsScreen(model, onChooseDirectory, onGrantStorage)
                    "detail" -> RepositoryScreen(model, onExportArtifact, onOpenUrl)
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(vm: RainViewModel, openUrl: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf(vm.store.oauthClientId) }
    var mode by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AccountTree, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(22.dp))
        Text("代码，随时掌控。", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Git 仓库与 Actions，都在 RainGit。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(26.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Token 登录") }, leadingIcon = if (mode == 0) {{ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }} else null)
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("浏览器授权") }, leadingIcon = if (mode == 1) {{ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }} else null)
                }
                Spacer(Modifier.height(12.dp))
                if (mode == 0) {
                    OutlinedTextField(
                        token, { token = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(), label = { Text("GitHub Personal Access Token") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Classic Token 建议 repo、workflow 权限；细粒度 Token 按需授权 Contents 与 Actions。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.loginWithToken(token) }, enabled = !vm.busy && token.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("连接 GitHub") }
                } else {
                    OutlinedTextField(clientId, { clientId = it }, modifier = Modifier.fillMaxWidth(), label = { Text("OAuth App Client ID") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Text("在 GitHub 页面验证账号与 2FA，RainGit 不获取密码。需启用 OAuth Device Flow。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    val device = vm.device
                    if (device == null) {
                        Button(onClick = { vm.startOAuth(clientId) }, enabled = !vm.busy && clientId.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("获取授权码") }
                    } else {
                        SelectionContainer { Text(device.userCode, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace) }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { openUrl(device.url) }) { Icon(Icons.Rounded.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("打开 GitHub 验证") }
                        TextButton(onClick = vm::cancelOAuth) { Text("取消授权") }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { openUrl("https://github.com/settings/tokens") }) {
            Text("创建 GitHub Token"); Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun SectionHeading(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun EmptyPanel(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 45.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StateLabel(label: String, tone: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = tone.copy(alpha = 0.11f), shape = CircleShape) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = tone, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

internal data class RunAppearance(val name: String, val color: Color, val icon: ImageVector)

@Composable
internal fun runAppearance(status: String, conclusion: String): RunAppearance {
    return when {
        conclusion == "success" -> RunAppearance("成功", MaterialTheme.colorScheme.primary, Icons.Rounded.CheckCircle)
        conclusion == "failure" || conclusion == "timed_out" -> RunAppearance("失败", MaterialTheme.colorScheme.error, Icons.Rounded.Error)
        conclusion == "cancelled" -> RunAppearance("已取消", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Cancel)
        status == "in_progress" -> RunAppearance("运行中", MaterialTheme.colorScheme.secondary, Icons.Rounded.Autorenew)
        status == "queued" || status == "waiting" || status == "pending" -> RunAppearance("排队中", MaterialTheme.colorScheme.secondary, Icons.Rounded.Schedule)
        else -> RunAppearance(conclusion.ifBlank { status.ifBlank { "未知" } }, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Info)
    }
}

package me.rain.git.ui

import androidx.compose.foundation.background
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import android.os.Build
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
    onOpenUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (model.themeMode) { 1 -> false; 2 -> true; else -> systemDark }
    val dynamic = model.dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkColors
        else -> lightColors
    }
    MaterialTheme(
        colorScheme = scheme.copy(background = scheme.surfaceContainerLowest, surface = scheme.surfaceContainerLow),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(17.dp), large = RoundedCornerShape(23.dp), extraLarge = RoundedCornerShape(28.dp))
    ) {
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(model.message) {
            val message = model.message ?: return@LaunchedEffect
            snack.showSnackbar(message, withDismissAction = true)
            if (model.message == message) model.clearMessage()
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
                                        "detail" -> when (model.detailTab) { 2 -> model.refreshActions(); 3 -> model.refreshCollaboration(); else -> model.refreshLocal() }
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
                else AnimatedContent(
                    targetState = model.screen,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "screen"
                ) { page ->
                    when (page) {
                        "home" -> RemoteScreen(model, actionsOnly = false)
                        "local" -> LocalScreen(model)
                        "actions" -> RemoteScreen(model, actionsOnly = true)
                        "settings" -> SettingsScreen(model, onChooseDirectory, onGrantStorage, onOpenUrl)
                        "detail" -> RepositoryScreen(model, onOpenUrl)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(vm: RainViewModel, openUrl: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Icon(androidx.compose.ui.res.painterResource(me.rain.git.R.drawable.ic_rain_mark), null,
                Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text("RainGit", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("代码随行，每一滴灵感都有迹可循。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("登录 GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(token, { token = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        visualTransformation = if (showToken) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            if (token.isNotBlank() && !vm.busy) vm.loginWithToken(token)
                        }),
                        label = { Text("Personal Access Token") },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        trailingIcon = { IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (showToken) "隐藏 Token" else "显示 Token")
                        } })
                    Button(onClick = { vm.loginWithToken(token) }, enabled = !vm.busy && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (vm.busy) "正在连接…" else "登录")
                    }
                    TextButton(onClick = { openUrl("https://github.com/settings/tokens") }) {
                        Text("创建 GitHub Token"); Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
            }
            Text("凭证仅加密保存在本机。访问私有仓库与构建需要相应的仓库及 Actions 权限。",
                style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        conclusion == "skipped" -> RunAppearance("已跳过", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.SkipNext)
        conclusion == "neutral" -> RunAppearance("已完成", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.CheckCircle)
        conclusion == "cancelled" -> RunAppearance("已取消", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Cancel)
        status == "in_progress" -> RunAppearance("运行中", MaterialTheme.colorScheme.secondary, Icons.Rounded.Autorenew)
        status == "queued" || status == "waiting" || status == "pending" -> RunAppearance("排队中", MaterialTheme.colorScheme.secondary, Icons.Rounded.Schedule)
        else -> RunAppearance(conclusion.ifBlank { status.ifBlank { "未知" } }, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Info)
    }
}

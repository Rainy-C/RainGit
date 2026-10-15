package me.rain.git.ui

import java.util.ArrayDeque

internal enum class LogKind { ERROR, WARNING, STEP, NORMAL }
internal data class BuildLogRow(
    val index: Int,
    val content: String,
    val original: String,
    val kind: LogKind,
    val time: String,
    val section: String,
    val location: String = "",
    val explanation: String = "",
    val detail: Boolean = false
)
internal data class BuildLogDocument(val rows: List<BuildLogRow>, val omitted: Int = 0) {
    val errors = rows.count { it.kind == LogKind.ERROR }
    val warnings = rows.count { it.kind == LogKind.WARNING }
    val firstError = rows.firstOrNull { it.kind == LogKind.ERROR && it.location.isNotEmpty() }
        ?: rows.firstOrNull { it.kind == LogKind.ERROR }
}

private val ansiCsi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
private val ansiOsc = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")
private val controls = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]")
private val timestamp = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})\\s*")
private val runnerMarker = Regex("^##\\[([^]]+)]")
private val workflowMarker = Regex("^::(warning|error|notice)(?:\\s+[^\\r\\n]*?)?::", RegexOption.IGNORE_CASE)
private val compilerDiagnostic = Regex("^([ew]):\\s+(?:file://)?(.+?):(\\d+):(\\d+)\\s+(.+)$")
private val infrastructure = Regex("^(Runner Image|Runner Image Provisioner|Operating System|GITHUB_TOKEN Permissions|Getting Git version info|Setting up auth|Restore Gradle|Caching Gradle|Stopping Gradle|Installed distributions|Provision Gradle|All Gradle Wrapper|Disabling automatic garbage collection|Run actions/checkout|Run actions/setup-java|Run gradle/actions/setup-gradle|Initializing the repository|Fetching the repository|Determining the checkout info|Checking out the ref|Post job cleanup|Run set -euo pipefail)", RegexOption.IGNORE_CASE)
private val importantStep = Regex("(── .*\\.txt|BUILD SUCCESSFUL|BUILD FAILED|compileReleaseKotlin|assembleRelease|packageRelease|signRelease|:app:.* FAILED|Verify Release signature|Upload signed Release APK)", RegexOption.IGNORE_CASE)

internal fun cleanBuildLogText(text: String): String =
    text.replace(ansiOsc, "").replace(ansiCsi, "").replace(controls, "")

internal fun parseBuildLog(raw: String): BuildLogDocument {
    val rows = ArrayDeque<BuildLogRow>()
    var omitted = 0
    var section = ""
    var noisyGroup = false
    var failureContext = false
    raw.lineSequence().forEachIndexed { index, source ->
        val original = source.trimStart('\uFEFF')
        var line = cleanBuildLogText(original)
        val stamp = timestamp.find(line)?.value.orEmpty()
        val time = if (stamp.isNotEmpty()) stamp.trim().let {
            it.substring(11, 19) + if (it.endsWith('Z')) " UTC" else it.takeLast(6)
        } else ""
        if (stamp.isNotEmpty()) line = line.substring(stamp.length)
        val indented = line.startsWith("  ")
        line = line.trimEnd()
        if (line.isBlank()) return@forEachIndexed
        var marker = ""
        runnerMarker.find(line)?.let {
            marker = it.groupValues[1].lowercase()
            line = line.substring(it.value.length).trimStart()
        }
        if (marker == "endgroup") {
            section = ""; noisyGroup = false; failureContext = false
            return@forEachIndexed
        }
        workflowMarker.find(line)?.let {
            marker = it.groupValues[1].lowercase()
            line = line.substring(it.value.length).replace("%0A", "\n", true)
                .replace("%0D", "\r", true).replace("%25", "%", true)
        }
        val group = marker == "group" || line.startsWith("── ")
        if (group) {
            section = if (marker == "group") line else line.removePrefix("── ").removeSuffix(" ──")
            noisyGroup = infrastructure.containsMatchIn(section)
            failureContext = false
        }
        val echoedScript = original.contains("\u001B[36;1m")
        val diagnostic = if (echoedScript) null else compilerDiagnostic.matchEntire(line.trimStart())
        val lower = line.trimStart().lowercase()
        if (lower.startsWith("* try:") || lower.startsWith("* get more help")) failureContext = false
        if (lower.startsWith("* what went wrong:")) failureContext = true
        val kind = when {
            echoedScript -> LogKind.NORMAL
            marker == "error" || diagnostic?.groupValues?.get(1) == "e" -> LogKind.ERROR
            marker == "warning" || diagnostic?.groupValues?.get(1) == "w" -> LogKind.WARNING
            lower.startsWith("e: ") || lower.startsWith("error:") || lower.startsWith("failure:") ||
                lower.startsWith("execution failed") || lower.startsWith("caused by:") ||
                lower.startsWith("exception in thread") || lower.startsWith("build failed") ||
                (lower.startsWith("> task ") && lower.endsWith(" failed")) || failureContext -> LogKind.ERROR
            lower.startsWith("w: ") || lower.startsWith("warning:") -> LogKind.WARNING
            group || lower.startsWith("> task ") || lower.startsWith("build successful") ||
                line.startsWith("[长日志仅保留") -> LogKind.STEP
            else -> LogKind.NORMAL
        }
        var content = line.removePrefix("[command]")
        if (line.startsWith("── ")) content = "任务 · " + line.removePrefix("── ").removeSuffix(" ──").removeSuffix(".txt")
        var location = ""
        var explanation = ""
        diagnostic?.let {
            location = it.groupValues[2].substringAfterLast('/') + ":${it.groupValues[3]}:${it.groupValues[4]}"
            content = it.groupValues[5]
        }
        if (kind == LogKind.WARNING && line.startsWith("Temporary signing key:")) {
            content = "当前 APK 使用临时签名"
            explanation = "下次构建会生成不同密钥，无法直接覆盖安装。配置固定签名密钥后才能持续升级。"
        }
        val detail = echoedScript || (noisyGroup && kind != LogKind.ERROR && kind != LogKind.WARNING) ||
            (kind == LogKind.STEP && !importantStep.containsMatchIn(line)) ||
            line.startsWith("[command]") || indented ||
            (lower.startsWith("> task ") && kind != LogKind.ERROR) ||
            lower.startsWith("cache ") || lower.startsWith("received ") || lower.startsWith("post job cleanup")
        rows.addLast(BuildLogRow(index, content, original, kind, time, section, location, explanation, detail))
        if (rows.size > 18000) { rows.removeFirst(); omitted++ }
    }
    return BuildLogDocument(rows.toList(), omitted)
}

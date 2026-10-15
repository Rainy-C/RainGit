package me.rain.git.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Lightweight, dependency-free token colouring for the mobile editor.
 * It NEVER changes the text itself, so cursor/selection offsets remain identical.
 * Syntax analysis is capped at the first 120k characters to protect typing latency.
 */
@Composable
internal fun rememberCodeSyntax(path: String): VisualTransformation {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return remember(path, dark) { CodeSyntax(path, dark) }
}

private enum class SyntaxFamily { C_STYLE, HASH, SQL, MARKUP, JSON, CSS, MARKDOWN, PLAIN }

private class CodeSyntax(path: String, dark: Boolean) : VisualTransformation {
    private val ext = path.substringAfterLast('.', "").lowercase()
    private val family = when (ext) {
        "kt", "kts", "java", "js", "jsx", "ts", "tsx", "dart", "swift", "rs", "go", "c", "h", "cc", "cpp", "hpp", "cs", "php", "scala", "sc", "groovy", "gradle", "m", "mm", "zig", "proto", "lua" -> SyntaxFamily.C_STYLE
        "py", "rb", "r", "sh", "bash", "zsh", "fish", "ps1", "toml", "yaml", "yml", "ini", "conf", "properties", "dockerfile", "makefile" -> SyntaxFamily.HASH
        "sql", "psql" -> SyntaxFamily.SQL
        "xml", "html", "htm", "svg", "vue", "svelte" -> SyntaxFamily.MARKUP
        "json", "jsonc", "json5" -> SyntaxFamily.JSON
        "css", "scss", "less" -> SyntaxFamily.CSS
        "md", "markdown", "mdx" -> SyntaxFamily.MARKDOWN
        else -> SyntaxFamily.PLAIN
    }
    private val keyword = if (dark) Color(0xFFBFA5F7) else Color(0xFF7145A6)
    private val string = if (dark) Color(0xFF9DD49C) else Color(0xFF347346)
    private val number = if (dark) Color(0xFFF2BF81) else Color(0xFFA15D23)
    private val comment = if (dark) Color(0xFF8D9A90) else Color(0xFF718075)
    private val special = if (dark) Color(0xFF89BFE9) else Color(0xFF2C729B)
    private val keywords: Set<String> = when (family) {
        SyntaxFamily.SQL -> sqlWords
        SyntaxFamily.HASH -> scriptWords
        SyntaxFamily.JSON -> setOf("true", "false", "null")
        else -> codeWords
    }
    private val tokens: Regex = when (family) {
        SyntaxFamily.MARKDOWN -> markdownTokens
        SyntaxFamily.MARKUP -> markupTokens
        SyntaxFamily.CSS -> cssTokens
        SyntaxFamily.JSON -> jsonTokens
        SyntaxFamily.HASH -> hashTokens
        SyntaxFamily.SQL -> sqlTokens
        SyntaxFamily.C_STYLE -> cTokens
        SyntaxFamily.PLAIN -> plainTokens
    }

    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        if (family == SyntaxFamily.PLAIN || text.text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val source = text.text
        val limit = source.length.coerceAtMost(120_000)
        val styled = buildAnnotatedString {
            append(text)
            tokens.findAll(source.substring(0, limit)).forEach { m ->
                val item = m.value
                val color = when {
                    item.startsWith("//") || item.startsWith("/*") || item.startsWith("#") && family == SyntaxFamily.HASH ||
                        item.startsWith("--") && family == SyntaxFamily.SQL || item.startsWith("<!--") -> comment
                    item.startsWith('"') || item.startsWith('\'') || item.startsWith('`') -> string
                    item.firstOrNull()?.isDigit() == true -> number
                    item.startsWith('@') || item.startsWith('<') || item.startsWith('$') -> special
                    family == SyntaxFamily.MARKDOWN -> if (item.startsWith('#')) special else keyword
                    family == SyntaxFamily.CSS && (item.endsWith(':') || item.startsWith('#') || item.startsWith('.')) -> special
                    item.lowercase() in keywords || item in keywords -> keyword
                    else -> null
                }
                if (color != null) addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private val codeWords = ("as async await abstract actual break case catch class const continue constructor " +
    "data default defer do else enum export extends external false final finally for fun function " +
    "get if import in inline interface internal is let mut namespace new null object open operator " +
    "override package private protected public return sealed static struct super suspend switch " +
    "this throw throws trait true try type typealias typeof use using val var void when where while " +
    "yield impl fn pub self Self match loop move ref unsafe crate mod dyn def lambda " +
    "int long double float char boolean bool string String Int Long Boolean Float Double " +
    "nil None Some Ok Err from require include define template typename virtual volatile " +
    "constexpr auto nullptr noexcept signed unsigned ifdef endif elif then end local").split(' ').toSet()
private val scriptWords = ("and as assert async await break case class continue def del do done elif else esac " +
    "except export false fi for from function if import in is lambda let local match nil None " +
    "not or pass print raise readonly return self set source then true try until unset while with yield " +
    "True False NULL null library data frame function next switch repeat private public param begin end").split(' ').toSet()
private val sqlWords = ("select from where join left right inner outer on group by having order limit offset " +
    "insert into values update set delete create table index drop alter add primary key foreign " +
    "references unique null not and or as distinct union all case when then else end " +
    "count sum min max avg cast exists like ilike between in asc desc returning with recursive").split(' ').toSet()

private val cTokens = Regex("""(?s)/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|@[A-Za-z_]\w*|\b\d+(?:\.\d+)?\b|\b[A-Za-z_]\w*\b""")
private val hashTokens = Regex("""(?m)#[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\$[A-Za-z_]\w*|\b\d+(?:\.\d+)?\b|\b[A-Za-z_]\w*\b""")
private val sqlTokens = Regex("""(?s)/\*.*?\*/|--[^\n]*|"(?:\\.|[^"\\])*"|'(?:''|[^'])*'|\b\d+(?:\.\d+)?\b|\b[A-Za-z_]\w*\b""", RegexOption.IGNORE_CASE)
private val jsonTokens = Regex("""\x22(?:\\.|[^\x22\\])*\x22|\b(?:true|false|null)\b|\b\d+(?:\.\d+)?\b""")
private val markupTokens = Regex("""(?s)<!--.*?-->|</?[A-Za-z][A-Za-z0-9:_-]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|@[A-Za-z_]\w*""")
private val cssTokens = Regex("""(?s)/\*.*?\*/|#[A-Za-z0-9_-]+|\.[A-Za-z_-][\w-]*|\b[A-Za-z_-][\w-]*:|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b\d+(?:\.\d+)?(?:px|rem|em|%|vh|vw)?\b""")
private val markdownTokens = Regex("""(?m)^#{1,6}[^\n]*|\*\*[^*\n]+\*\*|`[^`\n]+`|\[[^\]\n]+\]\([^\)\n]+\)""")
private val plainTokens = Regex("(?!)")

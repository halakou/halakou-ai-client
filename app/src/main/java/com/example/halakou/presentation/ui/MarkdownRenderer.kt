package com.example.halakou.presentation.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.CodeBorder
import com.example.ui.theme.CodeTextStyle
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.regex.Pattern

// GitHub Dark Syntax Highlighting Palette
private val SyntaxKeyword = Color(0xFFFF7B72)      // Red / Coral for keywords
private val SyntaxString = Color(0xFFA5D6FF)       // Soft Sky Blue for strings
private val SyntaxComment = Color(0xFF8B949E)      // Muted Gray for comments
private val SyntaxNumber = Color(0xFFFFA657)       // Orange for numbers
private val SyntaxFunction = Color(0xFFD2A8FF)     // Purple for function calls
private val SyntaxType = Color(0xFF7EE787)         // Emerald for types/classes

/**
 * Ultra-premium GitHub-style Markdown and Code Renderer for halakou.
 * Renders headers, lists, blockquotes, tables, inline code, and IDE-grade code blocks.
 */
@Composable
fun HalakouMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = TextPrimary
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 22.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = block.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = if (block.level == 1) AccentCyan else textColor,
                        lineHeight = (fontSize.value * 1.35).sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                is MdBlock.CodeBlock -> {
                    GitHubIdeCodeBlock(language = block.language, code = block.code)
                }
                is MdBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(26.dp)
                                .background(AccentCyan, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                color = TextSecondary
                            )
                        )
                    }
                }
                is MdBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = if (block.isOrdered) "${block.index}. " else "• ",
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        MarkdownInlineText(text = block.text, textColor = textColor)
                    }
                }
                is MdBlock.Paragraph -> {
                    MarkdownInlineText(text = block.text, textColor = textColor)
                }
                is MdBlock.Table -> {
                    TableCard(headers = block.headers, rows = block.rows)
                }
            }
        }
    }
}

/**
 * GitHub IDE Code Block with macOS window controls, language badge,
 * syntax token highlighting, line numbers gutter, and haptic copy button.
 */
@Composable
fun GitHubIdeCodeBlock(language: String, code: String) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val highlightedCode = remember(code, language) {
        highlightSyntax(code, language)
    }

    val lines = remember(code) { code.lines() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CodeBackground)
            .border(1.dp, CodeBorder, RoundedCornerShape(10.dp))
    ) {
        Column {
            // IDE Window Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceElevated)
                    .border(width = 0.5.dp, color = DarkBorder, shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // macOS window dots & language badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = language.ifBlank { "code" }.lowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentCyan
                        )
                    )
                }

                // Copy Code Button with Haptic feedback
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCopied) AccentEmerald.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isCopied = true
                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) AccentEmerald else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Copied!" else "Copy",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isCopied) AccentEmerald else TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Code Content with Line Numbers Gutter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp)
            ) {
                // Line numbers gutter
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp)
                        .border(width = 0.dp, color = Color.Transparent),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lines.size) {
                        Text(
                            text = "$i",
                            style = CodeTextStyle.copy(
                                color = TextTertiary.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                lineHeight = 19.sp
                            )
                        )
                    }
                }

                // Vertical gutter separator
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height((lines.size * 19).dp)
                        .background(DarkBorder.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Highlighted text
                Text(
                    text = highlightedCode,
                    style = CodeTextStyle.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp
                    ),
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}

/**
 * Token-level syntax highlighter imitating GitHub Dark palette.
 */
private fun highlightSyntax(code: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        append(code)

        val keywords = listOf(
            "fun", "val", "var", "class", "interface", "object", "import", "package",
            "return", "if", "else", "when", "for", "while", "try", "catch", "finally",
            "suspend", "override", "private", "public", "protected", "internal",
            "data", "sealed", "enum", "const", "def", "function", "let", "async", "await",
            "select", "from", "where", "true", "false", "null"
        )

        // 1. Highlight Strings ("..." or '...')
        val stringPattern = Pattern.compile("\".*?\"|'.*?'")
        val stringMatcher = stringPattern.matcher(code)
        while (stringMatcher.find()) {
            addStyle(
                SpanStyle(color = SyntaxString),
                stringMatcher.start(),
                stringMatcher.end()
            )
        }

        // 2. Highlight Keywords
        for (kw in keywords) {
            val kwPattern = Pattern.compile("\\b$kw\\b")
            val kwMatcher = kwPattern.matcher(code)
            while (kwMatcher.find()) {
                addStyle(
                    SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold),
                    kwMatcher.start(),
                    kwMatcher.end()
                )
            }
        }

        // 3. Highlight Numbers
        val numPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")
        val numMatcher = numPattern.matcher(code)
        while (numMatcher.find()) {
            addStyle(
                SpanStyle(color = SyntaxNumber),
                numMatcher.start(),
                numMatcher.end()
            )
        }

        // 4. Highlight Comments (// ... or # ...)
        val commentPattern = Pattern.compile("(//.*)|(#.*)")
        val commentMatcher = commentPattern.matcher(code)
        while (commentMatcher.find()) {
            addStyle(
                SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic),
                commentMatcher.start(),
                commentMatcher.end()
            )
        }
    }
}

@Composable
fun TableCard(headers: List<String>, rows: List<List<String>>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBackground)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .background(DarkSurfaceElevated, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                headers.forEach { h ->
                    Text(
                        text = h.trim(),
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .width(140.dp)
                            .padding(horizontal = 4.dp)
                    )
                }
            }
            rows.forEach { r ->
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                    r.forEach { cell ->
                        Text(
                            text = cell.trim(),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .width(140.dp)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownInlineText(text: String, textColor: Color) {
    val annotated = remember(text, textColor) {
        buildAnnotatedString {
            var i = 0
            val len = text.length
            while (i < len) {
                when {
                    // Inline code `...`
                    text[i] == '`' && text.indexOf('`', i + 1) != -1 -> {
                        val end = text.indexOf('`', i + 1)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = DarkSurfaceElevated,
                                color = AccentCyan
                            )
                        ) {
                            append(" " + text.substring(i + 1, end) + " ")
                        }
                        i = end + 1
                    }
                    // Bold **...**
                    text.startsWith("**", i) && text.indexOf("**", i + 2) != -1 -> {
                        val end = text.indexOf("**", i + 2)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    }
                    // Italic *...*
                    text[i] == '*' && text.indexOf('*', i + 1) != -1 && !text.startsWith("**", i) -> {
                        val end = text.indexOf('*', i + 1)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                    else -> {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        lineHeight = 22.sp
    )
}

sealed interface MdBlock {
    data class Header(val level: Int, val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class Blockquote(val text: String) : MdBlock
    data class ListItem(val text: String, val isOrdered: Boolean, val index: Int) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data class Paragraph(val text: String) : MdBlock
}

private fun parseMarkdownBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.lines()
    var idx = 0

    while (idx < lines.size) {
        val line = lines[idx]

        // Code block start
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            idx++
            while (idx < lines.size && !lines[idx].trimStart().startsWith("```")) {
                codeLines.add(lines[idx])
                idx++
            }
            blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            idx++
            continue
        }

        // Headers
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            val text = line.drop(level).trim()
            blocks.add(MdBlock.Header(level, text))
            idx++
            continue
        }

        // Blockquotes
        if (line.startsWith(">")) {
            val text = line.removePrefix(">").trim()
            blocks.add(MdBlock.Blockquote(text))
            idx++
            continue
        }

        // Unordered lists
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            val text = line.trimStart().drop(2)
            blocks.add(MdBlock.ListItem(text, false, 0))
            idx++
            continue
        }

        // Ordered lists
        val orderedMatch = Regex("^\\s*(\\d+)\\.\\s+(.*)").find(line)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            val text = orderedMatch.groupValues[2]
            blocks.add(MdBlock.ListItem(text, true, num))
            idx++
            continue
        }

        // Table check (| col | col |)
        if (line.trim().startsWith("|") && line.trim().endsWith("|") && idx + 1 < lines.size && lines[idx + 1].contains("---")) {
            val headers = line.split("|").filter { it.isNotBlank() }
            idx += 2 // skip header and divider
            val rows = mutableListOf<List<String>>()
            while (idx < lines.size && lines[idx].trim().startsWith("|")) {
                val rowCells = lines[idx].split("|").filter { it.isNotBlank() }
                rows.add(rowCells)
                idx++
            }
            blocks.add(MdBlock.Table(headers, rows))
            continue
        }

        // Default paragraph
        if (line.isNotBlank()) {
            blocks.add(MdBlock.Paragraph(line))
        }
        idx++
    }

    return blocks
}

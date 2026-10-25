package com.gemini.hw.nativeapp.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class ListItem(val indent: Int, val prefix: String, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class Image(val altText: String, val url: String) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isUser: Boolean = false,
    onOpenCanvas: ((code: String, lang: String) -> Unit)? = null
) {
    if (isUser || text.isBlank()) {
        Text(
            text = text,
            color = color,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = modifier
        )
        return
    }

    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val (fontSize, fontWeight) = when (block.level) {
                        1 -> 22.sp to FontWeight.Bold
                        2 -> 19.sp to FontWeight.Bold
                        3 -> 17.sp to FontWeight.SemiBold
                        else -> 15.sp to FontWeight.SemiBold
                    }
                    val annotated = remember(block.text) {
                        parseInlineMarkdown(block.text, primaryColor, codeBgColor, color)
                    }
                    if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = annotated,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = color,
                        lineHeight = (fontSize.value + 6).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                is MarkdownBlock.Paragraph -> {
                    val annotated = remember(block.text) {
                        parseInlineMarkdown(block.text, primaryColor, codeBgColor, color)
                    }
                    ClickableText(
                        text = annotated,
                        style = TextStyle(
                            color = color,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        onClick = { offset ->
                            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    try {
                                        uriHandler.openUri(annotation.item)
                                    } catch (_: Exception) {}
                                }
                        }
                    )
                    if (index < blocks.lastIndex) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                is MarkdownBlock.ListItem -> {
                    val annotated = remember(block.text) {
                        parseInlineMarkdown(block.text, primaryColor, codeBgColor, color)
                    }
                    val startPadding = (block.indent * 12).dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = startPadding, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.prefix,
                            color = primaryColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        ClickableText(
                            text = annotated,
                            style = TextStyle(
                                color = color,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            modifier = Modifier.weight(1f),
                            onClick = { offset ->
                                annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        try {
                                            uriHandler.openUri(annotation.item)
                                        } catch (_: Exception) {}
                                    }
                            }
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.language.ifBlank { "code" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (onOpenCanvas != null) {
                                        IconButton(
                                            onClick = { onOpenCanvas(block.code, block.language) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SpaceDashboard,
                                                contentDescription = "Открыть в Canvas",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(block.code))
                                            Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Копировать код",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                text = block.code,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = color,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    if (index < blocks.lastIndex) Spacer(modifier = Modifier.height(6.dp))
                }

                is MarkdownBlock.Blockquote -> {
                    val annotated = remember(block.text) {
                        parseInlineMarkdown(block.text, primaryColor, codeBgColor, color)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(primaryColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = annotated,
                            style = TextStyle(
                                color = color.copy(alpha = 0.85f),
                                fontStyle = FontStyle.Italic,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        )
                    }
                }

                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                is MarkdownBlock.Image -> {
                    var showFull by remember { mutableStateOf(false) }
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = block.url,
                        contentDescription = block.altText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showFull = true },
                        contentScale = ContentScale.Crop
                    )
                    if (showFull) {
                        Dialog(onDismissRequest = { showFull = false }) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp)),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    AsyncImage(
                                        model = block.url,
                                        contentDescription = block.altText,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = block.altText.ifBlank { "Сгенерированное изображение" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { showFull = false }) {
                                            Text("Закрыть")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (index < blocks.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = rawText.lines()
    var inCodeBlock = false
    var codeLang = ""
    val codeLines = mutableListOf<String>()

    var currentParagraph = StringBuilder()

    fun flushParagraph() {
        val p = currentParagraph.toString().trim()
        if (p.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(p))
        }
        currentParagraph = StringBuilder()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeLang, codeLines.joinToString("\n")))
                codeLines.clear()
                inCodeBlock = false
            } else {
                flushParagraph()
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            i++
            continue
        }

        if (inCodeBlock) {
            codeLines.add(line)
            i++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks.add(MarkdownBlock.HorizontalRule)
            i++
            continue
        }

        val imgMatch = Regex("""^!\[(.*?)\]\((https?://[^\)]+)\)$""").find(trimmed)
        if (imgMatch != null) {
            flushParagraph()
            val alt = imgMatch.groupValues[1]
            val url = imgMatch.groupValues[2]
            blocks.add(MarkdownBlock.Image(alt, url))
            i++
            continue
        }

        if (trimmed.startsWith("#")) {
            flushParagraph()
            val level = trimmed.takeWhile { it == '#' }.length
            val headerText = trimmed.substring(level).trim()
            blocks.add(MarkdownBlock.Header(level, headerText))
            i++
            continue
        }

        // Check for list item: "* ", "- ", "1. ", etc.
        val leadingSpaces = line.takeWhile { it == ' ' }.length
        val indent = leadingSpaces / 2
        val lineContent = line.trimStart()

        if (lineContent.startsWith("* ") || lineContent.startsWith("- ") || lineContent.startsWith("+ ")) {
            flushParagraph()
            val bullet = if (indent > 0) "◦" else "•"
            val itemText = lineContent.substring(2).trim()
            blocks.add(MarkdownBlock.ListItem(indent, bullet, itemText))
            i++
            continue
        }

        // Ordered list item: e.g. "1. " or "2. "
        val orderedMatch = Regex("^(\\d+)[.)]\\s+(.*)").find(lineContent)
        if (orderedMatch != null) {
            flushParagraph()
            val num = orderedMatch.groupValues[1]
            val itemText = orderedMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.ListItem(indent, "$num.", itemText))
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteText = trimmed.removePrefix(">").trim()
            blocks.add(MarkdownBlock.Blockquote(quoteText))
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
        } else {
            if (currentParagraph.isNotEmpty()) {
                currentParagraph.append("\n")
            }
            currentParagraph.append(trimmed)
        }

        i++
    }

    if (inCodeBlock && codeLines.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLang, codeLines.joinToString("\n")))
    }
    flushParagraph()

    return blocks
}

fun parseInlineMarkdown(
    text: String,
    primaryColor: Color,
    codeBgColor: Color,
    onSurfaceColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            // Inline code: `code`
            if (text[i] == '`' && !text.startsWith("```", i)) {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    val codeContent = text.substring(i + 1, end)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            fontSize = 13.sp
                        )
                    )
                    append(" $codeContent ")
                    pop()
                    i = end + 1
                    continue
                }
            }

            // Citations: [cite: 1] or [cite: 1, 2]
            if (text.startsWith("[cite:", i) || text.startsWith("[cite :", i)) {
                val end = text.indexOf(']', i)
                if (end > i) {
                    val citeContent = text.substring(i, end + 1)
                    val citeNumbers = citeContent.removePrefix("[cite:").removePrefix("[cite :").removeSuffix("]").trim()
                    pushStyle(
                        SpanStyle(
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            background = primaryColor.copy(alpha = 0.12f)
                        )
                    )
                    append(" [$citeNumbers] ")
                    pop()
                    i = end + 1
                    continue
                }
            }

            // Links: [text](url)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket > i && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 1) {
                        val linkText = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        pushStringAnnotation(tag = "URL", annotation = url)
                        pushStyle(
                            SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(linkText)
                        pop()
                        pop()
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // Bold-italic: ***text***
            if (text.startsWith("***", i)) {
                val end = text.indexOf("***", i + 3)
                if (end > i) {
                    val content = text.substring(i + 3, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    append(content)
                    pop()
                    i = end + 3
                    continue
                }
            }

            // Bold: **text** or __text__
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    val content = text.substring(i + 2, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(content)
                    pop()
                    i = end + 2
                    continue
                }
            } else if (text.startsWith("__", i)) {
                val end = text.indexOf("__", i + 2)
                if (end > i) {
                    val content = text.substring(i + 2, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(content)
                    pop()
                    i = end + 2
                    continue
                }
            }

            // Italic: *text* or _text_
            if (text[i] == '*' && (i == 0 || text[i - 1] != '*') && (i + 1 < len && text[i + 1] != '*')) {
                val end = text.indexOf('*', i + 1)
                if (end > i && (end + 1 >= len || text[end + 1] != '*')) {
                    val content = text.substring(i + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(content)
                    pop()
                    i = end + 1
                    continue
                }
            }

            // Strikethrough: ~~text~~
            if (text.startsWith("~~", i)) {
                val end = text.indexOf("~~", i + 2)
                if (end > i) {
                    val content = text.substring(i + 2, end)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(content)
                    pop()
                    i = end + 2
                    continue
                }
            }

            append(text[i])
            i++
        }
    }
}

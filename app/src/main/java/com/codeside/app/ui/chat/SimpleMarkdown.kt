package com.codeside.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun SimpleMarkdown(text: String) {
    val parts = splitCodeFences(text)
    Column {
        parts.forEach { part ->
            if (part.isCode) {
                Text(
                    text = part.content.trimEnd(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                )
            } else {
                part.content.lines().forEach { line ->
                    val trimmed = line.trimEnd()
                    if (trimmed.isBlank()) {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(vertical = 2.dp))
                        return@forEach
                    }
                    val isBullet = trimmed.startsWith("- ") || trimmed.startsWith("* ")
                    val style = when {
                        trimmed.startsWith("# ") -> MaterialTheme.typography.titleMedium
                        trimmed.startsWith("## ") -> MaterialTheme.typography.titleSmall
                        trimmed.startsWith("### ") -> MaterialTheme.typography.labelLarge
                        else -> MaterialTheme.typography.bodyMedium
                    }
                    val clean = trimmed
                        .removePrefix("### ")
                        .removePrefix("## ")
                        .removePrefix("# ")
                        .removePrefix("- ")
                        .removePrefix("* ")
                    Text(if (isBullet) "• $clean" else clean, style = style)
                }
            }
        }
    }
}

private data class MdPart(val content: String, val isCode: Boolean)

private fun splitCodeFences(text: String): List<MdPart> {
    val out = mutableListOf<MdPart>()
    val regex = Regex("```[\\w-]*\\n([\\s\\S]*?)```")
    var last = 0
    for (m in regex.findAll(text)) {
        if (m.range.first > last) out += MdPart(text.substring(last, m.range.first), false)
        out += MdPart(m.groupValues[1], true)
        last = m.range.last + 1
    }
    if (last < text.length) out += MdPart(text.substring(last), false)
    if (out.isEmpty()) out += MdPart(text, false)
    return out
}

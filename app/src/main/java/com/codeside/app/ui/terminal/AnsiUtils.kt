package com.codeside.app.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val ansiRegex = Regex("\u001B\\[[;?0-9]*[ -/]*[@-~]")

fun ansiToAnnotatedString(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var currentColor = defaultColor
        var bold = false
        var last = 0
        for (match in ansiRegex.findAll(text)) {
            if (match.range.first > last) {
                withStyle(SpanStyle(color = currentColor, fontWeight = if (bold) FontWeight.Bold else null)) {
                    append(text.substring(last, match.range.first))
                }
            }
            val code = match.value
            if (code.endsWith("m")) {
                val params = code.removePrefix("\u001B[").removeSuffix("m")
                    .split(';').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
                val effective = if (params.isEmpty()) listOf(0) else params
                for (p in effective) {
                    when (p) {
                        0 -> { currentColor = defaultColor; bold = false }
                        1 -> bold = true
                        22 -> bold = false
                        30 -> currentColor = Color(0xFF111827)
                        31 -> currentColor = Color(0xFFEF4444)
                        32 -> currentColor = Color(0xFF22C55E)
                        33 -> currentColor = Color(0xFFEAB308)
                        34 -> currentColor = Color(0xFF3B82F6)
                        35 -> currentColor = Color(0xFFA855F7)
                        36 -> currentColor = Color(0xFF06B6D4)
                        37 -> currentColor = Color(0xFFE5E7EB)
                        90 -> currentColor = Color(0xFF6B7280)
                        91 -> currentColor = Color(0xFFF87171)
                        92 -> currentColor = Color(0xFF4ADE80)
                        93 -> currentColor = Color(0xFFFACC15)
                        94 -> currentColor = Color(0xFF60A5FA)
                        95 -> currentColor = Color(0xFFC084FC)
                        96 -> currentColor = Color(0xFF22D3EE)
                        97 -> currentColor = Color(0xFFF9FAFB)
                    }
                }
            }
            last = match.range.last + 1
        }
        if (last < text.length) {
            withStyle(SpanStyle(color = currentColor, fontWeight = if (bold) FontWeight.Bold else null)) {
                append(text.substring(last))
            }
        }
    }
}

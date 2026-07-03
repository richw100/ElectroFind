package com.richwatson.electrofind.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

// Shared "!" stale-data warning styling for phone UI Text composables — bold and red so it
// reads as a clear warning rather than blending into the surrounding text's own style/color.
fun staleWarningPrefixed(name: String, isStale: Boolean): AnnotatedString = buildAnnotatedString {
    if (isStale) {
        withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) { append("! ") }
    }
    append(name)
}

package app.simplifymy.launcher.ui.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

fun zeros(text: String, highlight: Color, colon: Color): AnnotatedString = buildAnnotatedString {
    text.forEach { c ->
        when (c) {
            '0' -> withStyle(SpanStyle(color = highlight)) { append(c) }
            ':', '°' -> withStyle(SpanStyle(color = colon)) { append(c) }
            else -> append(c)
        }
    }
}

fun degrees(value: Int) = "$value°"

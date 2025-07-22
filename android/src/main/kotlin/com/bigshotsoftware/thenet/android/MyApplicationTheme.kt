package com.bigshotsoftware.thenet.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PURPLE_PRIMARY_DARK = 0xFFBB86FC
private const val TEAL_SECONDARY = 0xFF03DAC5
private const val PURPLE_TERTIARY = 0xFF3700B3
private const val PURPLE_PRIMARY_LIGHT = 0xFF6200EE
private const val BODY_FONT_SIZE = 16
private const val SMALL_CORNER_RADIUS = 4
private const val MEDIUM_CORNER_RADIUS = 4
private const val LARGE_CORNER_RADIUS = 0

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(PURPLE_PRIMARY_DARK),
            secondary = Color(TEAL_SECONDARY),
            tertiary = Color(PURPLE_TERTIARY)
        )
    } else {
        lightColorScheme(
            primary = Color(PURPLE_PRIMARY_LIGHT),
            secondary = Color(TEAL_SECONDARY),
            tertiary = Color(PURPLE_TERTIARY)
        )
    }
    val typography = Typography(
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = BODY_FONT_SIZE.sp
        )
    )
    val shapes = Shapes(
        small = RoundedCornerShape(SMALL_CORNER_RADIUS.dp),
        medium = RoundedCornerShape(MEDIUM_CORNER_RADIUS.dp),
        large = RoundedCornerShape(LARGE_CORNER_RADIUS.dp)
    )

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content
    )
}

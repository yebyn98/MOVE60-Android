package com.move60.workout.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.move60.workout.Move60Theme

/**
 * Single Material 3 theme for every Compose screen. Colors, type sizes and corner radii come
 * from design/tokens.json via Move60Theme, so Compose and the native Material Components
 * theme (res/values/styles.xml) render the same design system.
 */
@Composable
fun Move60ComposeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Move60ColorScheme,
        typography = Move60Typography,
        shapes = Move60Shapes,
        content = content
    )
}

private val Move60ColorScheme: ColorScheme
    get() {
        val c = Move60Theme.Colors
        return darkColorScheme(
            primary = Color(c.primary), onPrimary = Color(c.onPrimary),
            primaryContainer = Color(c.surfaceElevated), onPrimaryContainer = Color(c.textHigh),
            // `secondary` is the muted text color across the Compose screens.
            secondary = Color(c.textMedium), onSecondary = Color(c.onPrimary),
            secondaryContainer = Color(c.surfaceElevated), onSecondaryContainer = Color(c.textHigh),
            tertiary = Color(c.heart), onTertiary = Color(c.onPrimary),
            background = Color(c.background), onBackground = Color(c.textHigh),
            surface = Color(c.surface), onSurface = Color(c.textHigh),
            surfaceVariant = Color(c.surfaceElevated), onSurfaceVariant = Color(c.textMedium),
            surfaceContainerLowest = Color(c.background), surfaceContainerLow = Color(c.surface),
            surfaceContainer = Color(c.surface), surfaceContainerHigh = Color(c.surfaceElevated),
            surfaceContainerHighest = Color(c.surfaceElevated),
            outline = Color(c.outline), outlineVariant = Color(c.outline),
            error = Color(c.heart), onError = Color(c.onPrimary)
        )
    }

private fun style(size: Float, weight: FontWeight, line: Float) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

private val Move60Typography = Typography(
    displayLarge = style(Move60Theme.Type.display, FontWeight.Bold, 40f),
    displayMedium = style(Move60Theme.Type.display, FontWeight.Bold, 40f),
    displaySmall = style(Move60Theme.Type.metric, FontWeight.Bold, 36f),
    headlineLarge = style(Move60Theme.Type.display, FontWeight.Bold, 40f),
    headlineMedium = style(Move60Theme.Type.title, FontWeight.Bold, 32f),
    headlineSmall = style(Move60Theme.Type.title, FontWeight.Bold, 32f),
    titleLarge = style(Move60Theme.Type.heading, FontWeight.Bold, 28f),
    titleMedium = style(Move60Theme.Type.body, FontWeight.Bold, 24f),
    titleSmall = style(Move60Theme.Type.body, FontWeight.Medium, 24f),
    bodyLarge = style(Move60Theme.Type.body, FontWeight.Normal, 24f),
    bodyMedium = style(Move60Theme.Type.body, FontWeight.Normal, 24f),
    bodySmall = style(Move60Theme.Type.caption, FontWeight.Normal, 16f),
    labelLarge = style(Move60Theme.Type.body, FontWeight.Bold, 24f),
    labelMedium = style(Move60Theme.Type.caption, FontWeight.Medium, 16f),
    labelSmall = style(Move60Theme.Type.caption, FontWeight.Normal, 16f)
)

private val Move60Shapes = Shapes(
    extraSmall = RoundedCornerShape(Move60Theme.Radius.control.dp),
    small = RoundedCornerShape(Move60Theme.Radius.control.dp),
    medium = RoundedCornerShape(Move60Theme.Radius.card.dp),
    large = RoundedCornerShape(Move60Theme.Radius.card.dp),
    extraLarge = RoundedCornerShape(Move60Theme.Radius.card.dp)
)

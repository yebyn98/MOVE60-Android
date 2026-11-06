package com.move60.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.move60.workout.Move60Theme

/** Shared Material 3 card: token radius/outline/surface, 16dp padding, 12dp spacing (Figma card gap). */
@Composable
fun Move60Card(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/**
 * Shared Material 3 button. Filled when [primary], otherwise outlined on the elevated
 * surface. Always at least 48dp high with the token control radius.
 */
@Composable
fun Move60Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    minHeight: Int = 48,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val sized = modifier.heightIn(min = minHeight.dp)
    if (primary) {
        Button(
            onClick = onClick, modifier = sized, enabled = enabled,
            shape = MaterialTheme.shapes.small, contentPadding = contentPadding, content = content
        )
    } else {
        OutlinedButton(
            onClick = onClick, modifier = sized, enabled = enabled,
            shape = MaterialTheme.shapes.small, contentPadding = contentPadding,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            content = content
        )
    }
}

/**
 * Settings group: small section title above one rounded surface whose rows are separated by [Move60Divider].
 * Replaces one-card-per-setting so a screen reads as a few groups instead of many equal cards.
 */
@Composable
fun Move60Group(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Figma section_header: 12sp bold, muted text token, aligned with the card edge.
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = Color(Move60Theme.Colors.textMuted))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) { Column(Modifier.fillMaxWidth(), content = content) }
    }
}

@Composable
fun Move60Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

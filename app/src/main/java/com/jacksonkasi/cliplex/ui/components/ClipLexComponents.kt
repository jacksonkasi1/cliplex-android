package com.jacksonkasi.cliplex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes

enum class ClipLexButtonStyle {
    PRIMARY,
    SECONDARY,
    DANGER,
    WARM,
    GHOST,
}

/** A quiet product surface. Elevation is reserved for real hierarchy instead of every container. */
@Composable
fun ClipLexCard(
    modifier: Modifier = Modifier,
    containerColor: Color = ClipLexColors.SurfaceRaised,
    borderColor: Color = ClipLexColors.Border,
    depth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = ClipLexShapes.Card,
        color = containerColor,
        contentColor = ClipLexColors.Ink,
        border = if (borderColor == Color.Transparent) null else BorderStroke(1.dp, borderColor),
        shadowElevation = depth.coerceIn(0.dp, 4.dp),
    ) {
        Column(content = content)
    }
}

/** Primary actions use one clear accent and restrained physical feedback. */
@Composable
fun ClipLexActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ClipLexButtonStyle = ClipLexButtonStyle.PRIMARY,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        label = "cliplex-button-scale",
    )
    val palette = buttonPalette(style)
    val topColor by animateColorAsState(
        targetValue = if (enabled) palette.container else ClipLexColors.SurfaceMuted,
        label = "cliplex-button-color",
    )
    val contentColor = if (enabled) palette.content else ClipLexColors.InkFaint
    val borderColor = when {
        !enabled -> ClipLexColors.Border
        style == ClipLexButtonStyle.GHOST -> ClipLexColors.BorderStrong
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ClipLexShapes.Control)
            .background(topColor)
            .border(1.dp, borderColor, ClipLexShapes.Control)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Compact selector or status surface. It is intentionally less pill-like than the V1 component. */
@Composable
fun ClipLexPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    background: Color = ClipLexColors.Surface,
    contentColor: Color = ClipLexColors.Ink,
    borderColor: Color = ClipLexColors.Border,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Surface(
        modifier = modifier.then(clickModifier),
        shape = ClipLexShapes.Control,
        color = background,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ClipLexIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    background: Color = ClipLexColors.AccentSoft,
    contentColor: Color = ClipLexColors.Accent,
    size: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Box(
        modifier = modifier
            .size(size)
            .clip(ClipLexShapes.Small)
            .background(background)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun ClipLexProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = ClipLexColors.SurfaceMuted,
    progressColor: Color = ClipLexColors.Accent,
    height: Dp = 6.dp,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(ClipLexShapes.Pill)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safeProgress)
                .height(height)
                .clip(ClipLexShapes.Pill)
                .background(progressColor),
        )
    }
}

@Composable
fun ClipLexSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = ClipLexColors.Ink)
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = ClipLexColors.Accent,
                modifier = Modifier.clickable(onClick = onAction).padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun ClipLexBottomNav(
    items: List<Pair<ImageVector, String>>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ClipLexColors.Surface,
        contentColor = ClipLexColors.Ink,
        shadowElevation = 5.dp,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ClipLexColors.Border))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                items.forEachIndexed { index, (icon, label) ->
                    BottomNavItem(icon, label, index == selectedIndex) { onSelected(index) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(ClipLexShapes.Small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(3.dp)
                .clip(ClipLexShapes.Pill)
                .background(if (selected) ClipLexColors.Accent else Color.Transparent),
        )
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) ClipLexColors.Accent else ClipLexColors.InkFaint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ClipLexColors.AccentStrong else ClipLexColors.InkMuted,
            maxLines = 1,
        )
    }
}

private data class ButtonPalette(
    val container: Color,
    val content: Color,
)

private fun buttonPalette(style: ClipLexButtonStyle): ButtonPalette = when (style) {
    ClipLexButtonStyle.PRIMARY -> ButtonPalette(ClipLexColors.Accent, Color.White)
    ClipLexButtonStyle.SECONDARY -> ButtonPalette(ClipLexColors.NightSoft, Color.White)
    ClipLexButtonStyle.DANGER -> ButtonPalette(ClipLexColors.Coral, Color.White)
    ClipLexButtonStyle.WARM -> ButtonPalette(ClipLexColors.Warm, ClipLexColors.Ink)
    ClipLexButtonStyle.GHOST -> ButtonPalette(ClipLexColors.Surface, ClipLexColors.Ink)
}

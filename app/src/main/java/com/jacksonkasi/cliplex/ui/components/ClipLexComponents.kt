package com.jacksonkasi.cliplex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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

/** Elevated learning card with a subtle bottom edge rather than a generic Material shadow. */
@Composable
fun ClipLexCard(
    modifier: Modifier = Modifier,
    containerColor: Color = ClipLexColors.Surface,
    borderColor: Color = ClipLexColors.Border,
    depth: Dp = 3.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.padding(bottom = depth)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = depth)
                .background(borderColor, ClipLexShapes.Card),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor, ClipLexShapes.Card)
                .border(BorderStroke(1.dp, borderColor), ClipLexShapes.Card),
            content = content,
        )
    }
}

/** Tactile CTA with a visible pressed state, suitable for the app's main learning actions. */
@Composable
fun ClipLexActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ClipLexButtonStyle = ClipLexButtonStyle.PRIMARY,
    enabled: Boolean = true,
    height: Dp = 54.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedOffset by animateDpAsState(
        targetValue = if (pressed && enabled) 4.dp else 0.dp,
        label = "cliplex-button-depth",
    )
    val palette = buttonPalette(style)
    val topColor by animateColorAsState(
        targetValue = if (enabled) palette.container else ClipLexColors.SurfaceMuted,
        label = "cliplex-button-color",
    )
    val contentColor = if (enabled) palette.content else ClipLexColors.InkFaint
    val shadowColor = if (enabled) palette.depth else ClipLexColors.BorderStrong

    Box(modifier = modifier.height(height + 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 4.dp)
                .offset(y = 4.dp)
                .background(shadowColor, ClipLexShapes.Control),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 4.dp)
                .offset(y = pressedOffset)
                .clip(ClipLexShapes.Control)
                .background(topColor)
                .border(
                    width = if (style == ClipLexButtonStyle.GHOST) 1.dp else 0.dp,
                    color = if (style == ClipLexButtonStyle.GHOST) ClipLexColors.BorderStrong else Color.Transparent,
                    shape = ClipLexShapes.Control,
                )
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
                Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
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
}

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
        shape = ClipLexShapes.Pill,
        color = background,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    background: Color = ClipLexColors.GreenSoft,
    contentColor: Color = ClipLexColors.Green,
    size: Dp = 46.dp,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun ClipLexProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = ClipLexColors.SurfaceMuted,
    progressColor: Color = ClipLexColors.Green,
    height: Dp = 12.dp,
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
                color = ClipLexColors.Blue,
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
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, ClipLexColors.Border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEachIndexed { index, (icon, label) ->
                BottomNavItem(icon, label, index == selectedIndex) { onSelected(index) }
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
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .background(if (selected) ClipLexColors.GreenSoft else Color.Transparent, ClipLexShapes.Pill)
                .padding(horizontal = 15.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) ClipLexColors.Green else ClipLexColors.InkFaint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ClipLexColors.GreenDark else ClipLexColors.InkMuted,
            maxLines = 1,
        )
    }
}

private data class ButtonPalette(
    val container: Color,
    val content: Color,
    val depth: Color,
)

private fun buttonPalette(style: ClipLexButtonStyle): ButtonPalette = when (style) {
    ClipLexButtonStyle.PRIMARY -> ButtonPalette(ClipLexColors.Green, Color.White, ClipLexColors.GreenPressed)
    ClipLexButtonStyle.SECONDARY -> ButtonPalette(ClipLexColors.Blue, Color.White, ClipLexColors.BlueDark)
    ClipLexButtonStyle.DANGER -> ButtonPalette(ClipLexColors.Coral, Color.White, ClipLexColors.CoralDark)
    ClipLexButtonStyle.WARM -> ButtonPalette(ClipLexColors.Warm, Color.White, ClipLexColors.WarmDark)
    ClipLexButtonStyle.GHOST -> ButtonPalette(ClipLexColors.Surface, ClipLexColors.Ink, ClipLexColors.BorderStrong)
}

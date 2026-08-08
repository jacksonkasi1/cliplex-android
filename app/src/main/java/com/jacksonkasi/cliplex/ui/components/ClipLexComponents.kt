package com.jacksonkasi.cliplex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jacksonkasi.cliplex.ui.theme.ClipLexColors
import com.jacksonkasi.cliplex.ui.theme.ClipLexShapes

@Composable
fun ClipLexCard(
	modifier: Modifier = Modifier,
	containerColor: Color = ClipLexColors.Surface,
	content: @Composable ColumnScope.() -> Unit,
) {
	Column(
		modifier = modifier
			.shadow(14.dp, ClipLexShapes.Card, ambientColor = ClipLexColors.Shadow.copy(alpha = 0.08f), spotColor = ClipLexColors.Shadow.copy(alpha = 0.08f))
			.background(containerColor, ClipLexShapes.Card)
			.border(BorderStroke(1.dp, ClipLexColors.Border), ClipLexShapes.Card),
		content = content,
	)
}

@Composable
fun ClipLexPill(
	text: String,
	modifier: Modifier = Modifier,
	icon: ImageVector? = null,
	background: Color = ClipLexColors.Surface,
	contentColor: Color = ClipLexColors.Ink,
	onClick: (() -> Unit)? = null,
) {
	val clickable = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
	Surface(
		modifier = clickable,
		shape = CircleShape,
		color = background,
		contentColor = contentColor,
		border = BorderStroke(1.dp, ClipLexColors.Border),
		shadowElevation = 2.dp,
	) {
		Row(
			modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(17.dp)) }
			Text(text, style = MaterialTheme.typography.labelLarge)
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
	Row(
		modifier = modifier
			.fillMaxWidth()
			.background(ClipLexColors.Surface)
			.padding(horizontal = 8.dp, vertical = 7.dp),
		horizontalArrangement = Arrangement.SpaceAround,
	) {
		items.forEachIndexed { index, (icon, label) ->
			BottomNavItem(icon, label, index == selectedIndex) { onSelected(index) }
		}
	}
}

@Composable
private fun RowScope.BottomNavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
	Column(
		modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 3.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(3.dp),
	) {
		Box(
			modifier = Modifier
				.background(if (selected) ClipLexColors.GreenSoft else Color.Transparent, CircleShape)
				.padding(horizontal = 14.dp, vertical = 4.dp),
		) {
			Icon(icon, contentDescription = label, tint = if (selected) ClipLexColors.Green else Color(0xFF98A2B3), modifier = Modifier.size(21.dp))
		}
		Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) ClipLexColors.Green else Color(0xFF7C8799))
	}
}

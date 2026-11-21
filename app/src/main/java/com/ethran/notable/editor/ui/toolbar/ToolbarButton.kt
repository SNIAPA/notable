package com.ethran.notable.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.ethran.notable.ui.theme.Ink

/** Text style of toolbar items that show a number or a short label, such as "3/12". */
internal val ToolbarTextStyle = Ink.Type.label.copy(fontFeatureSettings = Ink.Type.TABULAR)

/**
 * One toolbar control: a 48dp square holding a 24dp glyph, or a 48dp tall text item with
 * [Ink.Space.m] inline padding when [text] is given. [isSelected] fills it with ink and draws
 * the content in paper; while pressed the pair inverts at once.
 *
 * [penColor] is shown as a 4dp bar under the glyph. A bar the same color as the button
 * background (a black pen when selected, a white pen when not) gets a 1dp outline in the
 * content color so it stays visible.
 */
@Composable
fun ToolbarButton(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelect: () -> Unit = {},
    iconId: Int? = null,
    vectorIcon: ImageVector? = null,
    imageVector: ImageVector? = null,
    text: String? = null,
    penColor: Color? = null,
    contentDescription: String = ""
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inked = isSelected != pressed
    val bg = if (inked) Ink.ink else Ink.paper
    val fg = if (inked) Ink.paper else Ink.ink
    val isText = text != null && iconId == null && vectorIcon == null && imageVector == null

    Box(
        modifier = modifier
            .then(
                if (isText) Modifier
                    .height(Ink.Size.target)
                    .widthIn(min = Ink.Size.target)
                else Modifier.size(Ink.Size.target)
            )
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onSelect,
            )
            .semantics {
                if (contentDescription.isNotEmpty()) this.contentDescription = contentDescription
                this.selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        val glyph = Modifier.size(Ink.Size.icon)
        when {
            iconId != null -> Icon(painterResource(iconId), null, tint = fg, modifier = glyph)
            vectorIcon != null -> Icon(vectorIcon, null, tint = fg, modifier = glyph)
            imageVector != null -> Icon(imageVector, null, tint = fg, modifier = glyph)
            text != null -> Text(
                text,
                style = ToolbarTextStyle,
                color = fg,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = Ink.Space.m),
            )
        }
        if (penColor != null && !isText) PenColorBar(penColor, background = bg, outline = fg)
    }
}

/** The pen color bar: icon wide, 4dp tall, [Ink.Space.xs] above the bottom edge. */
@Composable
private fun BoxScope.PenColorBar(color: Color, background: Color, outline: Color) {
    val bar = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = Ink.Space.xs)
        .size(width = Ink.Size.icon, height = Ink.Space.xs)
        .background(color)
    Box(
        if (color.toArgb() == background.toArgb()) bar.border(Ink.Size.hairline, outline)
        else bar
    )
}

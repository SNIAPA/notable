package com.ethran.notable.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/** Which edge of a control lines up with the region keyline: its glyph sits on it, not its padding. */
enum class Flush { None, Start, End }

private fun Modifier.flush(flush: Flush, padding: Dp): Modifier = when (flush) {
    Flush.None -> this
    Flush.Start -> offset(x = -padding)
    Flush.End -> offset(x = padding)
}

/**
 * Ink and paper for a control: [filled] controls are ink with paper content. While pressed the
 * pair inverts at once, which is the press feedback on e-ink.
 */
@Composable
private fun inkColors(filled: Boolean, pressed: Boolean): Pair<Color, Color> {
    val inked = filled != pressed
    return if (inked) Ink.ink to Ink.paper else Ink.paper to Ink.ink
}

/**
 * A labelled button. [primary] is the one main action of a screen (ink fill); the default is a
 * secondary action (ink border). [icon] sits before the label.
 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (bg, fg) = inkColors(primary, pressed)
    val content = if (enabled) fg else Ink.ink2
    Row(
        modifier = modifier
            .heightIn(min = Ink.Size.target)
            .background(bg)
            .border(Ink.Size.hairline, if (enabled) Ink.ink else Ink.rule)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                start = if (icon != null) Ink.Space.m else Ink.Space.l,
                end = Ink.Space.l,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ink.Space.s),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(Ink.Size.iconInline))
        }
        Text(text, style = Ink.Type.label, color = content, maxLines = 1)
    }
}

/**
 * A borderless glyph button with a 48dp target. [selected] fills it with ink. [flush] pulls the
 * glyph onto the region keyline while the target bleeds past it.
 */
@Composable
fun InkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    flush: Flush = Flush.None,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (bg, fg) = inkColors(selected, pressed)
    val inset = (Ink.Size.target - Ink.Size.icon) / 2
    Box(
        modifier = modifier
            .flush(flush, inset)
            .size(Ink.Size.target)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(Ink.Size.icon))
    }
}

/** A text action without a border, for secondary actions inside a row or section header. */
@Composable
fun InkTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    flush: Flush = Flush.None,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (bg, fg) = inkColors(false, pressed)
    Row(
        modifier = modifier
            .flush(flush, Ink.Space.s)
            .heightIn(min = Ink.Size.target)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Ink.Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ink.Space.s),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(Ink.Size.iconInline))
        }
        Text(text, style = Ink.Type.label, color = fg, maxLines = 1)
    }
}

/** A full-width hairline rule. */
@Composable
fun InkRule(modifier: Modifier = Modifier, color: Color = Ink.rule) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Ink.Size.hairline)
            .background(color)
    )
}

/**
 * The top bar of a screen: [leading] (back button and title) on the leading keyline, [actions] on
 * the trailing one, an ink rule underneath. Inline padding is the page margin, so its edges match
 * the content below.
 */
@Composable
fun InkTopBar(
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(Ink.paper)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Ink.Size.topBar)
                .padding(horizontal = Ink.Space.margin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                content = leading,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Ink.Space.s),
                content = actions,
            )
        }
        InkRule(color = Ink.ink)
    }
}

/** The title text of a top bar. */
@Composable
fun InkTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Ink.Type.title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * A section heading: the one uppercase label role. [trailing] holds the section's own action,
 * ending on the trailing keyline.
 */
@Composable
fun InkSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Ink.Size.target),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = Ink.Type.section,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** A labelled single-line text field: the label above, the value on a 2dp ink underline. */
@Composable
fun InkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Ink.Space.xs)) {
        Text(label, style = Ink.Type.meta)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Ink.Type.title,
            cursorBrush = SolidColor(Ink.ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Ink.Size.target),
            decorationBox = { inner ->
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Ink.Size.target - Ink.Size.hairline * 2)
                            .padding(bottom = Ink.Space.s),
                        contentAlignment = Alignment.BottomStart,
                    ) { inner() }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(Ink.Size.hairline * 2)
                            .background(Ink.ink)
                    )
                }
            },
        )
    }
}

/** Horizontal spacer of [width]. */
@Composable
fun RowScope.HSpace(width: Dp) = Box(Modifier.width(width))

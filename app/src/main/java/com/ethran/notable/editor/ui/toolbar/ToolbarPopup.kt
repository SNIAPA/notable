package com.ethran.notable.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.PopupProperties
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.ui.components.ScaledPopup
import com.ethran.notable.ui.theme.Ink

/** Whether the toolbar sits at the bottom edge, so its popovers open upwards. */
@Composable
internal fun toolbarAtBottom(): Boolean =
    GlobalAppSettings.current.toolbarPosition == AppSettings.Position.Bottom

/**
 * A popover anchored to a toolbar button. It opens on the canvas side of the toolbar, flush
 * with the toolbar's ink rule: below it when the toolbar is at the top, above it when the
 * toolbar is at the bottom. [alignEnd] lines its end edge up with the button's end edge;
 * otherwise it is centered on the button.
 *
 * The surface is paper with a 1dp ink border and square corners; [content] is laid out in a
 * column as wide as its widest child.
 */
@Composable
internal fun ToolbarPopup(
    onDismiss: () -> Unit,
    alignEnd: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val atBottom = toolbarAtBottom()
    val density = LocalDensity.current
    val distance = with(density) { (Ink.Size.target + Ink.Size.hairline).roundToPx() }
    val alignment = when {
        atBottom && alignEnd -> Alignment.BottomEnd
        atBottom -> Alignment.BottomCenter
        alignEnd -> Alignment.TopEnd
        else -> Alignment.TopCenter
    }
    ScaledPopup(
        alignment = alignment,
        offset = IntOffset(0, if (atBottom) -distance else distance),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ToolbarPopupSurface(content = content)
    }
}

/** The paper surface of a toolbar popover: 1dp ink border, square corners, no shadow. */
@Composable
internal fun ToolbarPopupSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .width(IntrinsicSize.Max)
            .background(Ink.paper)
            .border(Ink.Size.hairline, Ink.ink),
        content = content,
    )
}

/** A 48dp popover row with a body-text [label]; it inverts while pressed. */
@Composable
internal fun ToolbarMenuRow(
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Ink.Size.target)
            .background(if (pressed) Ink.ink else Ink.paper)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Ink.Space.l),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = Ink.Type.body,
            color = if (pressed) Ink.paper else Ink.ink,
            maxLines = 1,
        )
    }
}

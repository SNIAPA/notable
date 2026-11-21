package com.ethran.notable.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ethran.notable.R
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.editor.ToolbarAction
import com.ethran.notable.editor.ToolbarUiState
import com.ethran.notable.editor.state.Mode
import com.ethran.notable.editor.ui.toolbar.model.ActionElement
import com.ethran.notable.editor.ui.toolbar.model.CustomElement
import com.ethran.notable.editor.ui.toolbar.model.CustomKind
import com.ethran.notable.editor.ui.toolbar.model.DividerElement
import com.ethran.notable.editor.ui.toolbar.model.EraserSubmenuSpec
import com.ethran.notable.editor.ui.toolbar.model.IconRef
import com.ethran.notable.editor.ui.toolbar.model.ModeElement
import com.ethran.notable.editor.ui.toolbar.model.PenElement
import com.ethran.notable.editor.ui.toolbar.model.ShapeElement
import com.ethran.notable.editor.ui.toolbar.model.ToolbarElement
import com.ethran.notable.editor.utils.Eraser
import com.ethran.notable.ui.components.OnOffSwitch
import com.ethran.notable.ui.theme.Ink
import com.ethran.notable.ui.theme.InkRule

/**
 * The single generic renderer for toolbar elements: draws the button (via [ToolbarButton]),
 * handles selected state, and opens the element's declared submenu. Stateless except
 * transient popup-open state; all real mutation flows through [ToolbarAction].
 *
 * [onPickImage] exists because the image picker's activity-result launcher is Compose
 * infrastructure owned by ToolbarContent, not something a [ToolbarAction] can express.
 */
@Composable
fun ToolbarElementView(
    element: ToolbarElement,
    uiState: ToolbarUiState,
    onAction: (ToolbarAction) -> Unit,
    onPickImage: () -> Unit,
) {
    when (element) {
        is DividerElement -> ToolbarVerticalDivider()

        is PenElement -> PenElementView(element, uiState, onAction)

        is ShapeElement ->
            // One shape (LINE) for now: the picker submenu is stubbed to a plain toggle,
            // matching the old LineToolbarButton (click again deselects back to Draw).
            ToolbarButton(
                isSelected = element.isSelected(uiState),
                onSelect = {
                    onAction(
                        ToolbarAction.ChangeMode(
                            if (element.isSelected(uiState)) Mode.Draw else Mode.Line
                        )
                    )
                },
                iconId = (element.icon as? IconRef.Drawable)?.resId,
                vectorIcon = (element.icon as? IconRef.Vector)?.imageVector,
                contentDescription = element.contentDescription,
            )

        is ModeElement -> ModeElementView(element, uiState, onAction)

        is ActionElement ->
            ToolbarButton(
                isSelected = element.isSelected(uiState),
                onSelect = { onAction(element.action) },
                iconId = (element.icon as? IconRef.Drawable)?.resId,
                vectorIcon = (element.icon as? IconRef.Vector)?.imageVector,
                contentDescription = element.contentDescription,
            )

        is CustomElement -> when (element.kind) {
            CustomKind.IMAGE_PICKER ->
                ToolbarButton(
                    iconId = (element.icon as? IconRef.Drawable)?.resId,
                    vectorIcon = (element.icon as? IconRef.Vector)?.imageVector,
                    contentDescription = element.contentDescription,
                    onSelect = onPickImage,
                )

            CustomKind.PAGE_NAV ->
                ToolbarButton(
                    text = uiState.pageNumberInfo,
                    contentDescription = element.contentDescription,
                    onSelect = { onAction(ToolbarAction.NavigateToPages) },
                )

            CustomKind.MENU ->
                Box {
                    ToolbarButton(
                        isSelected = uiState.isMenuOpen,
                        onSelect = { onAction(ToolbarAction.ToggleMenu) },
                        iconId = (element.icon as? IconRef.Drawable)?.resId,
                        vectorIcon = (element.icon as? IconRef.Vector)?.imageVector,
                        contentDescription = element.contentDescription,
                    )
                    if (uiState.isMenuOpen) {
                        ToolbarMenu(
                            uiState = uiState,
                            onAction = onAction
                        )
                    }
                }
        }
    }
}

@Composable
private fun PenElementView(
    element: PenElement,
    uiState: ToolbarUiState,
    onAction: (ToolbarAction) -> Unit,
) {
    val isSelected = element.isSelected(uiState)
    val penSetting =
        uiState.penSettings[element.presetId] ?: element.setting.copy()

    Box {
        ToolbarButton(
            isSelected = isSelected,
            // The ViewModel decides from its current state: select the pen, or toggle its menu.
            onSelect = { onAction(ToolbarAction.PenButtonTapped(element.presetId)) },
            penColor = Color(penSetting.color),
            iconId = (element.icon as? IconRef.Drawable)?.resId,
            vectorIcon = (element.icon as? IconRef.Vector)?.imageVector,
            contentDescription = element.contentDescription,
        )

        if (uiState.penMenuPresetId == element.presetId) {
            StrokeMenu(
                value = penSetting,
                onChange = { onAction(ToolbarAction.ChangePenSetting(element.presetId, it)) },
                onClose = { onAction(ToolbarAction.ClosePenMenu) },
                sizeOptions = element.submenu.sizeOptions,
                colorOptions = element.submenu.colorOptions,
            )
        }
    }
}

@Composable
private fun ModeElementView(
    element: ModeElement,
    uiState: ToolbarUiState,
    onAction: (ToolbarAction) -> Unit,
) {
    val isSelected = element.isSelected(uiState)
    val submenu = element.submenu

    // The eraser button reflects the active eraser type, not its static spec icon.
    val iconId =
        if (submenu is EraserSubmenuSpec) eraserIcon(uiState.eraser)
        else (element.icon as? IconRef.Drawable)?.resId

    Box {
        ToolbarButton(
            isSelected = isSelected,
            onSelect = {
                if (isSelected && submenu is EraserSubmenuSpec)
                    onAction(ToolbarAction.ToggleEraserMenu(!uiState.isEraserMenuOpen))
                else if (!isSelected)
                    onAction(ToolbarAction.ChangeMode(element.mode))
            },
            iconId = iconId,
            vectorIcon = (element.icon as? IconRef.Vector)?.imageVector,
            contentDescription = element.contentDescription,
        )

        if (submenu is EraserSubmenuSpec && uiState.isEraserMenuOpen) {
            EraserSubmenu(
                spec = submenu,
                uiState = uiState,
                onAction = onAction,
            )
        }
    }
}

private fun eraserIcon(eraser: Eraser): Int =
    when (eraser) {
        Eraser.PEN -> R.drawable.eraser
        Eraser.SELECT -> R.drawable.eraser_select
    }

private fun eraserName(eraser: Eraser): Int =
    when (eraser) {
        Eraser.PEN -> R.string.toolbar_eraser_pen
        Eraser.SELECT -> R.string.toolbar_eraser_select
    }

/**
 * The eraser popover: the eraser types as a row of glyph buttons (the active one ink-filled),
 * then the global scribble-to-erase switch.
 */
@Composable
private fun EraserSubmenu(
    spec: EraserSubmenuSpec,
    uiState: ToolbarUiState,
    onAction: (ToolbarAction) -> Unit,
) {
    ToolbarPopup(onDismiss = { onAction(ToolbarAction.ToggleEraserMenu(false)) }) {
        // Buttons are inset by xs so their glyphs sit on the l keyline of the rows below.
        Row(Modifier.padding(horizontal = Ink.Space.xs)) {
            spec.erasers.forEach { eraser ->
                ToolbarButton(
                    iconId = eraserIcon(eraser),
                    isSelected = uiState.eraser == eraser,
                    contentDescription = stringResource(eraserName(eraser)),
                    onSelect = { onAction(ToolbarAction.ChangeEraser(eraser)) },
                )
            }
        }
        InkRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Ink.Size.target)
                .padding(horizontal = Ink.Space.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ink.Space.l),
        ) {
            Text(
                text = stringResource(R.string.toolbar_scribble_to_erase),
                style = Ink.Type.body,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            var isChecked by remember {
                mutableStateOf(GlobalAppSettings.current.scribbleToEraseEnabled)
            }
            OnOffSwitch(
                checked = isChecked,
                onCheckedChange = { checked ->
                    isChecked = checked
                    onAction(ToolbarAction.ToggleScribbleToErase(checked))
                }
            )
        }
    }
}

/** A 1dp rule between toolbar groups, inset by [Ink.Space.s] top and bottom. */
@Composable
internal fun ToolbarVerticalDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .padding(vertical = Ink.Space.s)
            .width(Ink.Size.hairline)
            .background(Ink.rule)
    )
}

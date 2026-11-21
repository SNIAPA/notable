package com.ethran.notable.editor.ui.toolbar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.editor.ToolbarAction
import com.ethran.notable.editor.ToolbarUiState
import com.ethran.notable.editor.state.Mode
import androidx.compose.ui.res.stringResource
import com.ethran.notable.R
import com.ethran.notable.editor.ui.toolbar.model.DividerElement
import com.ethran.notable.editor.ui.toolbar.model.IconRef
import com.ethran.notable.editor.ui.toolbar.model.ToolbarElement
import com.ethran.notable.editor.ui.toolbar.model.ToolbarElementId
import com.ethran.notable.editor.ui.toolbar.model.ToolbarElements
import com.ethran.notable.editor.ui.toolbar.model.ToolbarLayout
import com.ethran.notable.editor.ui.toolbar.model.ToolbarPen
import com.ethran.notable.editor.utils.Pen
import com.ethran.notable.ui.dialogs.BackgroundSelector
import com.ethran.notable.ui.theme.Ink
import com.ethran.notable.ui.theme.InkRule

/**
 * Spec-driven toolbar: iterates a [ToolbarLayout] and renders each element through
 * [ToolbarElementView]. The layout is data; adding a tool means adding a registry
 * entry, not editing this file.
 */
@Composable
fun ToolbarContent(
    uiState: ToolbarUiState,
    onAction: (ToolbarAction) -> Unit,
) {
    // Activity result launcher for picking images
    val pickMedia = rememberLauncherForActivityResult(contract = PickVisualMedia()) { uri ->
        uri?.let { onAction(ToolbarAction.ImagePicked(it)) }
    }

    if (uiState.isBackgroundSelectorModalOpen) {
        BackgroundSelector(
            initialPageBackgroundType = uiState.backgroundType,
            initialPageBackground = uiState.backgroundPath,
            initialPageNumberInPdf = uiState.backgroundPageNumber,
            notebookId = uiState.notebookId,
            pageNumberInBook = uiState.currentPageNumber,
            onChange = { type, path -> onAction(ToolbarAction.BackgroundChanged(type, path)) },
            onClose = { onAction(ToolbarAction.ToggleBackgroundSelector(false)) }
        )
    }

    if (!uiState.isToolbarOpen) {
        CollapsedToolbarButton(uiState, onAction)
        return
    }

    // Snapshot read: setting changes (toolbarPens, toolbarPosition, ...) recompose the toolbar.
    val settings = GlobalAppSettings.current
    // Persisted layouts must be sanitized: they may predate elements, duplicate ids,
    // or omit the mandatory MENU entry. DEFAULT is validated by construction (tested);
    // its entries for deleted presets are skipped by resolve() below.
    val layout = remember(settings.toolbarLayout, settings.toolbarPens) {
        settings.toolbarLayout?.validated(settings.toolbarPens) ?: ToolbarLayout.DEFAULT
    }

    /**
     * The visible elements of a zone, without dividers at either end or next to another divider,
     * so a hidden tool never leaves a divider around empty space.
     */
    fun visibleIn(names: List<String>): List<ToolbarElement> {
        val visible = names.mapNotNull { name ->
            ToolbarElements.resolve(name, settings.toolbarPens)?.takeIf { it.visibleWhen(uiState, settings) }
        }
        val kept = mutableListOf<ToolbarElement>()
        for (element in visible) {
            if (element is DividerElement && (kept.isEmpty() || kept.last() is DividerElement)) continue
            kept.add(element)
        }
        while (kept.lastOrNull() is DividerElement) kept.removeAt(kept.lastIndex)
        return kept
    }

    @Composable
    fun renderZone(elements: List<ToolbarElement>) {
        for (element in elements) {
            ToolbarElementView(
                element = element,
                uiState = uiState,
                onAction = onAction,
                onPickImage = {
                    pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                },
            )
        }
    }

    val atBottom = settings.toolbarPosition == AppSettings.Position.Bottom
    Column(Modifier.fillMaxWidth().background(Ink.paper)) {
        // The ink rule sits on the edge that faces the canvas.
        if (atBottom) InkRule(color = Ink.ink)
        Row(
            Modifier
                .height(Ink.Size.target)
                .fillMaxWidth()
        ) {
            // Structural: the toggle is always first, never part of the layout.
            ToolbarElementView(
                element = ToolbarElements.of(ToolbarElementId.TOGGLE),
                uiState = uiState,
                onAction = onAction,
                onPickImage = {},
            )
            ToolbarVerticalDivider()

            // Left zone: scrolls horizontally.
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
            ) {
                renderZone(visibleIn(layout.scrollable))
            }

            // Right zone: pinned, set off from the scrolling zone by one divider.
            val pinned = visibleIn(layout.pinned)
            if (pinned.isNotEmpty()) {
                ToolbarVerticalDivider()
                Row(Modifier.fillMaxHeight()) {
                    renderZone(pinned)
                }
            }
        }
        if (!atBottom) InkRule(color = Ink.ink)
    }
}

/**
 * The closed toolbar: one 48dp paper square in the toolbar's corner showing the current tool
 * glyph (and the pen color bar while drawing), with a 1dp ink frame on the two edges that
 * face the canvas. Tapping it opens the toolbar.
 */
@Composable
private fun CollapsedToolbarButton(
    uiState: ToolbarUiState,
    onAction: (ToolbarAction) -> Unit,
) {
    val icon = ToolbarElements.presentlyUsedToolIcon(
        uiState, GlobalAppSettings.current.toolbarPens
    )
    val atBottom = toolbarAtBottom()
    ToolbarButton(
        onSelect = { onAction(ToolbarAction.ToggleToolbar) },
        iconId = (icon as? IconRef.Drawable)?.resId,
        vectorIcon = (icon as? IconRef.Vector)?.imageVector,
        penColor = if (uiState.mode != Mode.Erase)
            uiState.penSettings[uiState.penPresetId]?.color?.let { Color(it) }
        else null,
        contentDescription = stringResource(R.string.toolbar_open),
        modifier = Modifier.drawWithContent {
            drawContent()
            val w = Ink.Size.hairline.toPx()
            val y = if (atBottom) w / 2 else size.height - w / 2
            drawLine(Ink.ink, Offset(0f, y), Offset(size.width, y), w)
            val x = size.width - w / 2
            drawLine(Ink.ink, Offset(x, 0f), Offset(x, size.height), w)
        },
    )
}

@Composable
@Preview(showBackground = true, widthDp = 1200)
fun ToolbarPreview() {
    val uiState = ToolbarUiState(
        isToolbarOpen = true,
        mode = Mode.Draw,
        pen = Pen.BALLPEN,
        penPresetId = ToolbarPen.DEFAULT_PENS.first().id,
        penSettings = ToolbarPen.defaultPenSettings,
        pageNumberInfo = "3/12",
        notebookId = "dummy_book"
    )

    ToolbarContent(
        uiState = uiState,
        onAction = {},
    )
}

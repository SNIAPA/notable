package com.ethran.notable.editor.ui.toolbar

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ethran.notable.R
import com.ethran.notable.editor.ToolbarAction
import com.ethran.notable.editor.ToolbarUiState
import com.ethran.notable.editor.state.Mode
import com.ethran.notable.editor.ui.toolbar.model.ToolbarPen
import com.ethran.notable.editor.utils.Pen
import com.ethran.notable.io.ExportFormat
import com.ethran.notable.ui.theme.InkRule

/**
 * The toolbar's page menu: navigation, page and book exports, and page-level actions, in a
 * popover whose end edge lines up with the menu button. Every row closes the menu after
 * sending its [ToolbarAction].
 */
@Composable
fun ToolbarMenu(
    uiState: ToolbarUiState,
    onAction: (ToolbarAction) -> Unit,
) {
    ToolbarPopup(
        onDismiss = { onAction(ToolbarAction.ToggleMenu) },
        alignEnd = true,
    ) {
        ToolbarMenuContent(uiState = uiState, onAction = onAction)
    }
}

@Composable
private fun ColumnScope.ToolbarMenuContent(
    uiState: ToolbarUiState,
    onAction: (ToolbarAction) -> Unit
) {
    // Home / Library
    ToolbarMenuRow(stringResource(R.string.home_view_name)) {
        onAction(ToolbarAction.NavigateToLibrary)
        onAction(ToolbarAction.ToggleMenu)
    }
    InkRule()

    // Page exports
    ToolbarMenuRow(stringResource(R.string.export_page_to, "PDF")) {
        onAction(ToolbarAction.ExportPage(ExportFormat.PDF))
        onAction(ToolbarAction.ToggleMenu)
    }
    ToolbarMenuRow(stringResource(R.string.export_page_to, "PNG")) {
        onAction(ToolbarAction.ExportPage(ExportFormat.PNG))
        onAction(ToolbarAction.ToggleMenu)
    }
    ToolbarMenuRow(stringResource(R.string.export_page_to, "JPEG")) {
        onAction(ToolbarAction.ExportPage(ExportFormat.JPEG))
        onAction(ToolbarAction.ToggleMenu)
    }
    ToolbarMenuRow(stringResource(R.string.export_page_to, "xopp")) {
        onAction(ToolbarAction.ExportPage(ExportFormat.XOPP))
        onAction(ToolbarAction.ToggleMenu)
    }
    ToolbarMenuRow(stringResource(R.string.copy_page_link)) {
        onAction(ToolbarAction.CopyPageLink)
        onAction(ToolbarAction.ToggleMenu)
    }
    InkRule()

    // Book exports
    if (uiState.notebookId != null) {
        ToolbarMenuRow(stringResource(R.string.export_book_to, "PDF")) {
            onAction(ToolbarAction.ExportBook(ExportFormat.PDF))
            onAction(ToolbarAction.ToggleMenu)
        }
        ToolbarMenuRow(stringResource(R.string.export_book_to, "PNG")) {
            onAction(ToolbarAction.ExportBook(ExportFormat.PNG))
            onAction(ToolbarAction.ToggleMenu)
        }
        ToolbarMenuRow(stringResource(R.string.export_book_to, "xopp")) {
            onAction(ToolbarAction.ExportBook(ExportFormat.XOPP))
            onAction(ToolbarAction.ToggleMenu)
        }
        ToolbarMenuRow(stringResource(R.string.copy_book_link)) {
            onAction(ToolbarAction.CopyBookLink)
            onAction(ToolbarAction.ToggleMenu)
        }
        InkRule()
    }

    ToolbarMenuRow(stringResource(R.string.clean_all_strokes)) {
        onAction(ToolbarAction.ClearAllStrokes)
        onAction(ToolbarAction.ToggleMenu)
    }
    InkRule()

    ToolbarMenuRow(stringResource(R.string.change_background)) {
        onAction(ToolbarAction.ToggleBackgroundSelector(true))
        onAction(ToolbarAction.ToggleMenu)
    }

    ToolbarMenuRow(stringResource(R.string.bug_report)) {
        onAction(ToolbarAction.NavigateToBugReport)
        onAction(ToolbarAction.ToggleMenu)
    }
}

@Composable
@Preview(showBackground = true)
fun ToolbarMenuPreview() {
    ToolbarPopupSurface {
        ToolbarMenuContent(
            uiState = ToolbarUiState(
                isMenuOpen = true,
                notebookId = "book1",
                mode = Mode.Draw,
                pen = Pen.BALLPEN,
                penSettings = ToolbarPen.defaultPenSettings
            ),
            onAction = {}
        )
    }
}

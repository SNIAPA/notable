package com.ethran.notable.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethran.notable.R
import com.ethran.notable.data.db.Folder
import com.ethran.notable.data.model.BackgroundType
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.dialogs.BackgroundSelectorContent
import com.ethran.notable.ui.dialogs.ShowFolderSelectionDialog
import com.ethran.notable.ui.theme.Flush
import com.ethran.notable.ui.theme.Ink
import com.ethran.notable.ui.theme.InkButton
import com.ethran.notable.ui.theme.InkIconButton
import com.ethran.notable.ui.theme.InkSectionHeader
import com.ethran.notable.ui.theme.InkTextButton
import com.ethran.notable.ui.theme.InkTextField
import com.ethran.notable.ui.theme.InkTitle
import com.ethran.notable.ui.theme.InkTopBar
import com.ethran.notable.ui.theme.InkaTheme
import com.ethran.notable.ui.viewmodels.NewNotebookState
import com.ethran.notable.ui.viewmodels.NewNotebookViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Folder

object NewNotebookDestination : NavigationDestination {
    override val route = "newNotebook"
    const val FOLDER_ID_ARG = "folderId"
    val routeWithArgs = "$route?$FOLDER_ID_ARG={$FOLDER_ID_ARG}"
    fun createRoute(folderId: String?): String =
        if (folderId != null) "$route?$FOLDER_ID_ARG=$folderId" else route
}

@Composable
fun NewNotebookView(
    onBack: () -> Unit,
    onCreated: (pageId: String, bookId: String) -> Unit,
    viewModel: NewNotebookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val breadcrumbFolders by viewModel.breadcrumbFolders.collectAsStateWithLifecycle()
    var showFolderPicker by remember { mutableStateOf(false) }

    val currentOnCreated by rememberUpdatedState(onCreated)
    LaunchedEffect(viewModel) {
        viewModel.created.collect { currentOnCreated(it.pageId, it.bookId) }
    }

    if (showFolderPicker) {
        ShowFolderSelectionDialog(
            title = stringResource(R.string.new_notebook_choose_folder_title),
            initialFolderId = state.folderId,
            folderContents = viewModel::folderContents,
            onCancel = { showFolderPicker = false },
            onConfirm = {
                viewModel.setFolder(it)
                showFolderPicker = false
            })
    }

    InkaTheme {
        NewNotebookContent(
            state = state,
            breadcrumbFolders = breadcrumbFolders,
            onBack = onBack,
            onCreate = viewModel::create,
            onTitleChange = viewModel::setTitle,
            onChangeFolder = { showFolderPicker = true },
            backgroundSelector = {
                BackgroundSelectorContent(
                    initialPageBackgroundType = state.backgroundType,
                    initialPageBackground = state.background,
                    isNotebookBgSelector = true,
                    onChange = viewModel::setBackground,
                    modifier = Modifier.fillMaxWidth()
                )
            },
        )
    }
}

/**
 * The new notebook form: a top bar with back, the screen title and the create action, then the
 * title field, the target folder and the page background. [backgroundSelector] spans the full
 * width and pads itself to the page margin (and Ink.Space.s above), so its content shares the
 * form's keylines.
 */
@Composable
private fun NewNotebookContent(
    state: NewNotebookState,
    breadcrumbFolders: List<Folder>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onTitleChange: (String) -> Unit,
    onChangeFolder: () -> Unit,
    backgroundSelector: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.paper)
    ) {
        InkTopBar(
            leading = {
                InkIconButton(
                    icon = FeatherIcons.ArrowLeft,
                    contentDescription = stringResource(R.string.new_notebook_back),
                    onClick = onBack,
                    flush = Flush.Start,
                    // The arrow's stroke starts 4dp into its box; this puts the stroke on the keyline.
                    modifier = Modifier.offset(x = -Ink.Space.xs),
                )
                InkTitle(stringResource(R.string.new_notebook_screen_title))
            },
            actions = {
                InkButton(
                    text = stringResource(R.string.new_notebook_create_notebook),
                    onClick = onCreate,
                    primary = true,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Ink.Space.xl)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Ink.Space.margin),
                verticalArrangement = Arrangement.spacedBy(Ink.Space.xl),
            ) {
                InkTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = stringResource(R.string.new_notebook_title_label),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                FolderField(folders = breadcrumbFolders, onChange = onChangeFolder)
            }

            Spacer(Modifier.height(Ink.Space.section))
            InkSectionHeader(
                text = stringResource(R.string.new_notebook_page_background),
                modifier = Modifier.padding(horizontal = Ink.Space.margin),
            )
            backgroundSelector()
        }
    }
}

/**
 * The notebook's folder as a labelled field: the path from the library root as plain text, and a
 * Change action on the trailing keyline.
 */
@Composable
private fun FolderField(folders: List<Folder>, onChange: () -> Unit) {
    val path = (listOf(stringResource(R.string.home_view_name)) + folders.map { it.title })
        .joinToString(" / ")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ink.Space.l),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Ink.Space.xs),
        ) {
            Text(stringResource(R.string.new_notebook_folder_label), style = Ink.Type.meta)
            Text(
                text = path,
                style = Ink.Type.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        InkTextButton(
            text = stringResource(R.string.new_notebook_change_folder),
            onClick = onChange,
            icon = FeatherIcons.Folder,
            flush = Flush.End,
        )
    }
}

@Composable
private fun NewNotebookPreview() {
    NewNotebookContent(
        state = NewNotebookState(
            title = "New notebook",
            folderId = "f1",
            backgroundType = BackgroundType.Native.key,
            background = "lined",
        ),
        breadcrumbFolders = listOf(Folder(title = "Studies")),
        onBack = {},
        onCreate = {},
        onTitleChange = {},
        onChangeFolder = {},
        backgroundSelector = {
            Box(
                Modifier
                    .padding(horizontal = Ink.Space.margin)
                    .fillMaxWidth()
                    .height(Ink.Size.card)
                    .border(Ink.Size.hairline, Ink.rule)
            )
        },
    )
}

@Preview(name = "Tablet", showBackground = true, widthDp = 800, heightDp = 1000)
@Preview(name = "Phone", showBackground = true, widthDp = 340, heightDp = 700)
@Composable
private fun NewNotebookPreviewTabletAndPhone() = NewNotebookPreview()

package com.ethran.notable.ui.views

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ethran.notable.R
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.Folder
import com.ethran.notable.data.db.Notebook
import com.ethran.notable.data.db.Page
import com.ethran.notable.editor.EditorDestination
import com.ethran.notable.editor.utils.autoEInkAnimationOnScroll
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.sync.SyncBadge
import com.ethran.notable.ui.LocalSnackContext
import com.ethran.notable.ui.SnackConf
import com.ethran.notable.ui.components.BreadCrumb
import com.ethran.notable.ui.components.NewPageButton
import com.ethran.notable.ui.components.NotebookCard
import com.ethran.notable.ui.components.PageThumb
import com.ethran.notable.ui.dialogs.ConflictResolutionDialog
import com.ethran.notable.ui.dialogs.EmptyBookWarningHandler
import com.ethran.notable.ui.dialogs.FolderConfigDialog
import com.ethran.notable.ui.dialogs.NotebookConfigDialog
import com.ethran.notable.ui.dialogs.PdfImportChoiceDialog
import com.ethran.notable.ui.theme.Flush
import com.ethran.notable.ui.theme.Ink
import com.ethran.notable.ui.theme.InkButton
import com.ethran.notable.ui.theme.InkIconButton
import com.ethran.notable.ui.theme.InkRule
import com.ethran.notable.ui.theme.InkSectionHeader
import com.ethran.notable.ui.theme.InkTextButton
import com.ethran.notable.ui.theme.InkTopBar
import com.ethran.notable.ui.viewmodels.LibraryUiState
import com.ethran.notable.ui.viewmodels.LibraryViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.FilePlus
import compose.icons.feathericons.Folder
import compose.icons.feathericons.FolderPlus
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Upload
import io.shipbook.shipbooksdk.ShipBook


object LibraryDestination : NavigationDestination {
    override val route = "library"
    const val FOLDER_ID_ARG = "folderId"
    val routeWithArgs = "$route?$FOLDER_ID_ARG={$FOLDER_ID_ARG}"
    fun createRoute(folderId: String? = null): String {
        return if (folderId != null) "$route?$FOLDER_ID_ARG=$folderId" else route
    }
}

private val log = ShipBook.getLogger("HomeView")

@Composable
fun Library(
    navController: NavController,
    folderId: String? = null,
    goToPage: (String) -> Unit = {},
    onCreateNewQuickPage: (String?) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(folderId) {
        viewModel.loadFolder(folderId)
    }

    LibraryContent(
        appRepository = viewModel.appRepository,
        uiState = uiState,
        onNavigateToFolder = { id -> navController.navigate(LibraryDestination.createRoute(id)) },
        onNavigateToSettings = { navController.navigate("settings") },
        onNavigateToEditor = { pageId, bookId ->
            navController.navigate(EditorDestination.createRoute(pageId, bookId))
        },
        goToPage = goToPage,
        onCreateNewQuickPage = { onCreateNewQuickPage(uiState.folderId) },
        onCreateNewFolder = viewModel::createNewFolder,
        onDeleteEmptyBook = viewModel::deleteEmptyBook,
        onCreateNewNotebook = {
            navController.navigate(NewNotebookDestination.createRoute(uiState.folderId))
        },
        onImportPdf = viewModel::onPdfFile,
        onImportXopp = viewModel::onXoppFile,
        onPreviewMissing = viewModel::onPreviewRequested

    )
}


/**
 * The library screen: a top bar with the folder trail as its title and the create, import and
 * settings actions, over one vertical scroll that holds the folders, quick pages and notebooks
 * sections. Every section shares the page margin as its inline padding, so headings and card
 * edges sit on the same keylines as the top bar.
 */
@Composable
fun LibraryContent(
    appRepository: AppRepository,
    uiState: LibraryUiState,
    onNavigateToFolder: (String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEditor: (String, String) -> Unit,
    goToPage: (String) -> Unit,
    onCreateNewQuickPage: () -> Unit,
    onCreateNewFolder: () -> Unit,
    onDeleteEmptyBook: (String) -> Unit,
    onCreateNewNotebook: () -> Unit,
    onImportPdf: (Uri, Boolean) -> Unit,
    onImportXopp: (Uri) -> Unit,
    onPreviewMissing: (String) -> Unit
) {
    val (emptyBooks, books) = uiState.books.partition { it.pageIds.isEmpty() }

    if (!uiState.isImporting) {
        emptyBooks.forEach { book ->
            key(book.id) {
                EmptyBookWarningHandler(
                    emptyBook = book,
                    onDelete = { onDeleteEmptyBook(book.id) },
                    onDismiss = { })
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.paper)
    ) {
        InkTopBar(
            leading = {
                BreadCrumb(
                    folders = uiState.breadcrumbFolders, onSelectFolderId = onNavigateToFolder
                )
            },
            actions = {
                ImportButton(onImportPdf = onImportPdf, onImportXopp = onImportXopp)
                InkButton(
                    text = stringResource(R.string.library_new_notebook),
                    onClick = onCreateNewNotebook,
                    primary = true,
                    icon = FeatherIcons.FilePlus
                )
                SettingsButton(
                    updateAvailable = !uiState.isLatestVersion,
                    onClick = onNavigateToSettings
                )
            }
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The column count GridCells.Adaptive(Ink.Size.card) would pick, computed here so card
            // items know whether they start a section's first row.
            val columns = maxOf(
                1,
                ((maxWidth - Ink.Space.margin * 2 + Ink.Space.gap) / (Ink.Size.card + Ink.Space.gap)).toInt()
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(Ink.Space.gap),
                contentPadding = PaddingValues(
                    start = Ink.Space.margin,
                    end = Ink.Space.margin,
                    bottom = Ink.Space.margin
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .autoEInkAnimationOnScroll()
            ) {
                folderSection(
                    appRepository = appRepository,
                    folders = uiState.folders,
                    perRow = maxOf(1, columns / 2),
                    onNavigateToFolder = onNavigateToFolder,
                    onCreateNewFolder = onCreateNewFolder
                )
                quickPageSection(
                    afterContent = uiState.folders.isNotEmpty(),
                    appRepository = appRepository,
                    pages = uiState.singlePages,
                    columns = columns,
                    goToPage = goToPage,
                    onCreateNewQuickPage = onCreateNewQuickPage,
                    onPreviewMissing = onPreviewMissing
                )
                notebookSection(
                    afterContent = uiState.singlePages.isNotEmpty(),
                    books = books,
                    inFolder = uiState.folderId != null,
                    columns = columns,
                    syncBadges = uiState.syncBadges,
                    onNavigateToEditor = onNavigateToEditor,
                    onCreateNewNotebook = onCreateNewNotebook,
                    onPreviewMissing = onPreviewMissing
                )
            }
        }
    }
}

/**
 * A full-width section heading with [Ink.Space.s] below it. Above it sits [Ink.Space.section]
 * when [afterContent] (the section before it shows items), else [Ink.Space.s], so empty sections
 * stack as compact heading rows.
 */
private fun LazyGridScope.sectionHeader(
    key: String,
    text: @Composable () -> String,
    afterContent: Boolean,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        InkSectionHeader(
            text = text(),
            modifier = Modifier.padding(
                top = if (afterContent) Ink.Space.section else Ink.Space.s,
                bottom = Ink.Space.s
            ),
            trailing = trailing
        )
    }
}

/** Card items after a section's first row keep [Ink.Space.xl] from the row above. */
private fun cardSpacing(index: Int, columns: Int): Modifier =
    if (index >= columns) Modifier.padding(top = Ink.Space.xl) else Modifier

/**
 * The folders section: its heading with the "New folder" action, then the folders as an index of
 * rows, [perRow] to a line. The heading stays when there are no folders so a folder can be made.
 */
private fun LazyGridScope.folderSection(
    appRepository: AppRepository,
    folders: List<Folder>,
    perRow: Int,
    onNavigateToFolder: (String) -> Unit,
    onCreateNewFolder: () -> Unit,
) {
    sectionHeader(
        key = "header:folders",
        text = { stringResource(R.string.library_folders) },
        afterContent = true
    ) {
        InkTextButton(
            text = stringResource(R.string.library_new_folder),
            onClick = onCreateNewFolder,
            icon = FeatherIcons.FolderPlus,
            flush = Flush.End
        )
    }
    folders.chunked(perRow).forEach { line ->
        item(key = "folders:${line.first().id}", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Ink.Space.gap)
            ) {
                line.forEach { folder ->
                    FolderRow(
                        appRepository = appRepository,
                        folder = folder,
                        onNavigateToFolder = onNavigateToFolder,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(perRow - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One folder in the folders index: the folder glyph and title on a 48dp row with a hairline
 * under it. Tap opens the folder, long-press opens its settings. The row inverts while pressed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    appRepository: AppRepository,
    folder: Folder,
    onNavigateToFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFolderSettingsOpen by remember { mutableStateOf(false) }
    if (isFolderSettingsOpen) FolderConfigDialog(
        appRepository.folderRepository,
        folderId = folder.id,
        onClose = {
            log.i("Closing Directory Dialog")
            isFolderSettingsOpen = false
        })

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fg = if (pressed) Ink.paper else Ink.ink
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Ink.Size.target)
                .background(if (pressed) Ink.ink else Ink.paper)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = { onNavigateToFolder(folder.id) },
                    onLongClick = { isFolderSettingsOpen = true }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ink.Space.m)
        ) {
            Icon(
                imageVector = FeatherIcons.Folder,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(Ink.Size.iconInline)
            )
            Text(
                text = folder.title,
                style = Ink.Type.label,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        InkRule()
    }
}

/** The quick pages section: its heading with the "New page" action, then the loose pages. */
private fun LazyGridScope.quickPageSection(
    afterContent: Boolean,
    appRepository: AppRepository,
    pages: List<Page>,
    columns: Int,
    goToPage: (String) -> Unit,
    onCreateNewQuickPage: () -> Unit,
    onPreviewMissing: (String) -> Unit,
) {
    sectionHeader(
        key = "header:pages",
        text = { stringResource(R.string.home_quick_pages) },
        afterContent = afterContent
    ) {
        NewPageButton(onClick = onCreateNewQuickPage)
    }
    itemsIndexed(pages.reversed(), key = { _, page -> "page:${page.id}" }) { index, page ->
        PageThumb(
            appRepository = appRepository,
            page = page,
            onSelectPage = goToPage,
            onPreviewMissing = onPreviewMissing,
            modifier = cardSpacing(index, columns)
        )
    }
}

/**
 * The notebooks section: its heading, then the notebooks newest first, or the empty state when
 * the location holds none. A conflicted notebook opens the conflict resolution dialog on tap,
 * the reachable entry point for its conflict glyph; every other notebook opens in the editor.
 */
private fun LazyGridScope.notebookSection(
    afterContent: Boolean,
    books: List<Notebook>,
    inFolder: Boolean,
    columns: Int,
    syncBadges: Map<String, SyncBadge>,
    onNavigateToEditor: (String, String) -> Unit,
    onCreateNewNotebook: () -> Unit,
    onPreviewMissing: (String) -> Unit,
) {
    sectionHeader(
        key = "header:notebooks",
        text = { stringResource(R.string.home_notebooks) },
        afterContent = afterContent
    )

    if (books.isEmpty()) {
        item(key = "notebooks:empty", span = { GridItemSpan(maxLineSpan) }) {
            LibraryEmptyState(inFolder = inFolder, onCreateNewNotebook = onCreateNewNotebook)
        }
        return
    }

    // Keyed so an open dialog stays with its notebook when one is added before it.
    itemsIndexed(books.reversed(), key = { _, book -> book.id }) { index, book ->
        var isSettingsOpen by remember { mutableStateOf(false) }
        var isConflictOpen by remember { mutableStateOf(false) }
        NotebookCard(
            modifier = cardSpacing(index, columns),
            bookId = book.id,
            title = book.title,
            pageIds = book.pageIds,
            openPageId = book.openPageId,
            syncBadge = syncBadges[book.id],
            onOpen = { bookId, pageId ->
                if (syncBadges[bookId] == SyncBadge.CONFLICT) isConflictOpen = true
                else onNavigateToEditor(pageId, bookId)
            },
            onOpenSettings = { isSettingsOpen = true },
            onPreviewMissing = onPreviewMissing
        )

        if (isSettingsOpen) {
            NotebookConfigDialog(bookId = book.id, onClose = { isSettingsOpen = false })
        }

        if (isConflictOpen) {
            ConflictResolutionDialog(
                bookId = book.id,
                title = book.title,
                onClose = { isConflictOpen = false })
        }
    }
}

/**
 * The notebooks section when there are none: what is empty, what goes here, and the action that
 * creates the first notebook.
 */
@Composable
private fun LibraryEmptyState(inFolder: Boolean, onCreateNewNotebook: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Ink.Space.s)) {
        Text(
            text = stringResource(
                if (inFolder) R.string.library_empty_folder_title else R.string.library_empty_title
            ),
            style = Ink.Type.label
        )
        Text(
            text = stringResource(R.string.library_empty_body),
            style = Ink.Type.body,
            color = Ink.ink2
        )
        InkButton(
            text = stringResource(R.string.library_new_notebook),
            onClick = onCreateNewNotebook,
            icon = FeatherIcons.FilePlus,
            modifier = Modifier.padding(top = Ink.Space.s)
        )
    }
}

/**
 * The settings action, flush with the trailing keyline. While an update is available it carries
 * the words "Update available" next to the glyph, since a badge dot does not read on e-ink.
 */
@Composable
private fun SettingsButton(updateAvailable: Boolean, onClick: () -> Unit) {
    if (updateAvailable) {
        InkTextButton(
            text = stringResource(R.string.library_update_available),
            onClick = onClick,
            icon = FeatherIcons.Settings,
            flush = Flush.End
        )
    } else {
        InkIconButton(
            icon = FeatherIcons.Settings,
            contentDescription = stringResource(R.string.settings_title),
            onClick = onClick,
            flush = Flush.End
        )
    }
}

/**
 * The import action: opens the system document picker for PDF and Xournal++ files. A PDF asks
 * whether to copy or observe the file before importing; any other file imports as Xournal++.
 */
@Composable
private fun ImportButton(
    onImportPdf: (Uri, Boolean) -> Unit,
    onImportXopp: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val snackState = LocalSnackContext.current
    var pdfToImport by remember { mutableStateOf<Uri?>(null) }

    pdfToImport?.let { uri ->
        PdfImportChoiceDialog(uri = uri, onCopy = {
            pdfToImport = null
            onImportPdf(it, /* copy= */ true)
        }, onObserve = {
            pdfToImport = null
            onImportPdf(it, /* copy= */ false)
        }, onDismiss = { pdfToImport = null })
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            log.w("OpenDocument: uri is null (user cancelled or provider returned null)")
            return@rememberLauncherForActivityResult
        }
        try {
            val mimeType = context.contentResolver.getType(uri)
            log.d("Selected file mimeType: $mimeType, uri: $uri")
            if (mimeType == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
                pdfToImport = uri
            } else {
                onImportXopp(uri)
            }
        } catch (e: Exception) {
            log.e("contentPicker failed: ${e.message}", e)
            snackState.showOrUpdateSnack(
                SnackConf(text = context.getString(R.string.library_import_failed, e.message))
            )
        }
    }

    InkButton(
        text = stringResource(R.string.library_import),
        onClick = {
            launcher.launch(
                arrayOf(
                    "application/x-xopp",
                    "application/gzip",
                    "application/octet-stream",
                    "application/pdf"
                )
            )
        },
        icon = FeatherIcons.Upload
    )
}

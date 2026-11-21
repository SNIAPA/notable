package com.ethran.notable.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ethran.notable.R
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.Page
import com.ethran.notable.editor.ui.PageMenu
import com.ethran.notable.editor.utils.autoEInkAnimationOnScroll
import com.ethran.notable.ui.theme.Flush
import com.ethran.notable.ui.theme.Ink
import com.ethran.notable.ui.theme.InkSectionHeader
import com.ethran.notable.ui.theme.InkTextButton
import compose.icons.FeatherIcons
import compose.icons.feathericons.FilePlus
import java.text.DateFormat

/**
 * A titled, horizontally scrolling row of page cards, newest first. [title] is set as a section
 * heading; with [showAddQuickPage] the heading carries a "New page" action. The page with
 * [currentPageId] has an ink frame. Tap selects a page, long-press opens its page menu.
 */
@Composable
fun ShowPagesRow(
    appRepository: AppRepository,
    pages: List<Page>?,
    currentPageId: String? = null,
    title: String? = "Quick Pages",
    onSelectPage: (String) -> Unit,
    showAddQuickPage: Boolean = false,
    onCreateNewQuickPage: () -> Unit = {},
    onPreviewMissing: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(Ink.Space.s)) {
        if (title != null || showAddQuickPage) {
            InkSectionHeader(text = title.orEmpty()) {
                if (showAddQuickPage) NewPageButton(onClick = onCreateNewQuickPage)
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Ink.Space.gap),
            modifier = Modifier
                .fillMaxWidth()
                .autoEInkAnimationOnScroll()
        ) {
            items(pages.orEmpty().reversed(), key = { it.id }) { page ->
                PageThumb(
                    appRepository = appRepository,
                    page = page,
                    selected = page.id == currentPageId,
                    onSelectPage = onSelectPage,
                    onPreviewMissing = onPreviewMissing,
                    modifier = Modifier.width(Ink.Size.card)
                )
            }
        }
    }
}

/** The "New page" action of a section heading, flush with the trailing keyline. */
@Composable
fun NewPageButton(onClick: () -> Unit) {
    InkTextButton(
        text = stringResource(R.string.library_new_page),
        icon = FeatherIcons.FilePlus,
        flush = Flush.End,
        onClick = onClick
    )
}

/**
 * A loose page as a library card: its preview, and under it the date it was last edited in
 * tabular figures. Tap selects the page, long-press opens its page menu.
 */
@Composable
fun PageThumb(
    appRepository: AppRepository,
    page: Page,
    onSelectPage: (String) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onPreviewMissing: (String) -> Unit = {},
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val date = remember(page.updatedAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(page.updatedAt)
    }
    Box(modifier) {
        LibraryCard(
            pageId = page.id,
            selected = selected,
            onClick = { onSelectPage(page.id) },
            onLongClick = { isMenuOpen = true },
            onPreviewMissing = onPreviewMissing,
        ) {
            Text(
                text = date,
                style = Ink.Type.meta.copy(fontFeatureSettings = Ink.Type.TABULAR),
                maxLines = 1
            )
        }
        if (isMenuOpen) PageMenu(
            appRepository = appRepository,
            pageId = page.id,
            canDelete = true,
            onClose = { isMenuOpen = false })
    }
}

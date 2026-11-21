package com.ethran.notable.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.ethran.notable.sync.SyncBadge
import com.ethran.notable.ui.theme.Ink
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.AlertTriangle
import compose.icons.feathericons.Clock
import compose.icons.feathericons.CloudOff
import compose.icons.feathericons.DownloadCloud
import compose.icons.feathericons.RefreshCw

/**
 * A notebook in the library grid: the preview of its first page framed by a hairline, and under
 * it the spine label: the title on up to two lines and the page count as a folio number on the
 * right. A sync glyph sits before the count only when the notebook is not in sync. Tap opens the
 * notebook at its open page, long-press opens its settings.
 */
@Composable
fun NotebookCard(
    modifier: Modifier = Modifier,
    bookId: String,
    title: String,
    pageIds: List<String>,
    openPageId: String?,
    syncBadge: SyncBadge? = null,
    onOpen: (bookId: String, pageId: String) -> Unit,
    onOpenSettings: (bookId: String) -> Unit,
    onPreviewMissing: (String) -> Unit = {},
) {
    LibraryCard(
        modifier = modifier,
        pageId = pageIds[0],
        onClick = { onOpen(bookId, openPageId ?: pageIds[0]) },
        onLongClick = { onOpenSettings(bookId) },
        onPreviewMissing = onPreviewMissing,
    ) {
        Text(
            text = title,
            style = Ink.Type.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
        )
        Row(
            modifier = Modifier.alignByBaseline(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ink.Space.xs)
        ) {
            syncBadge?.iconOrNull()?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = "Sync status: ${syncBadge.name}",
                    tint = if (syncBadge.needsAttention()) Ink.ink else Ink.ink2,
                    modifier = Modifier.size(Ink.Size.iconInline)
                )
            }
            Text(
                text = pageIds.size.toString(),
                style = Ink.Type.meta.copy(fontFeatureSettings = Ink.Type.TABULAR),
                maxLines = 1
            )
        }
    }
}

/**
 * A card of the library grid: a 3:4 page preview framed by a hairline, with [label] in a row
 * under it. The frame turns ink while pressed and when [selected]. Tap runs [onClick],
 * long-press runs [onLongClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryCard(
    pageId: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onPreviewMissing: (String) -> Unit = {},
    label: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        verticalArrangement = Arrangement.spacedBy(Ink.Space.s)
    ) {
        PagePreview(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .border(
                    if (selected) Ink.Size.hairline * 2 else Ink.Size.hairline,
                    if (pressed || selected) Ink.ink else Ink.rule
                ),
            pageId = pageId,
            onPreviewMissing = onPreviewMissing
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Ink.Space.s),
            content = label
        )
    }
}

/** Glyph for a sync state that differs from in sync, or null when the notebook is in sync. */
private fun SyncBadge.iconOrNull(): ImageVector? = when (this) {
    SyncBadge.SYNCED -> null
    SyncBadge.NOT_SYNCED -> FeatherIcons.CloudOff
    SyncBadge.SCHEDULED -> FeatherIcons.Clock
    SyncBadge.SYNCING -> FeatherIcons.RefreshCw
    SyncBadge.REMOTE_AHEAD -> FeatherIcons.DownloadCloud
    SyncBadge.CONFLICT -> FeatherIcons.AlertTriangle
    SyncBadge.ERROR -> FeatherIcons.AlertCircle
}

/** States the user has to act on, drawn in ink rather than secondary gray. */
private fun SyncBadge.needsAttention(): Boolean =
    this == SyncBadge.CONFLICT || this == SyncBadge.ERROR

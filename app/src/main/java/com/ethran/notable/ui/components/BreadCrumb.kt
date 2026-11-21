package com.ethran.notable.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.ethran.notable.R
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.Folder
import com.ethran.notable.data.db.Page
import com.ethran.notable.ui.theme.Ink
import com.ethran.notable.ui.theme.InkaTheme
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight

/**
 * The folder trail from the library root to the current folder. The last segment is the current
 * location, set in ink and not tappable; the root and parent folders are set in [Ink.ink2] and
 * navigate there on tap. [fontSize] picks the text role: 20 and above sets the trail in the title
 * style (a screen title), smaller values in the label style. Long trails ellipsize segment by
 * segment so the row never wraps.
 */
@Composable
fun BreadCrumb(
    modifier: Modifier = Modifier,
    folders: List<Folder> = emptyList(),
    fontSize: Int = 20,
    onSelectFolderId: (String?) -> Unit
) {
    val style = if (fontSize >= 20) Ink.Type.title else Ink.Type.label
    val segments = listOf<Pair<String?, String>>(null to stringResource(R.string.home_view_name)) +
            folders.map { it.id to it.title }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ink.Space.xs)
    ) {
        segments.forEachIndexed { index, (id, title) ->
            val isCurrent = index == segments.lastIndex
            if (index > 0) {
                Icon(
                    imageVector = FeatherIcons.ChevronRight,
                    contentDescription = null,
                    tint = Ink.ink2,
                    modifier = Modifier.size(Ink.Size.iconInline)
                )
            }
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = Ink.Size.target)
                    .then(
                        if (isCurrent) Modifier
                        else Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClick = { onSelectFolderId(id) })
                    )
            ) {
                Text(
                    text = title,
                    style = style,
                    color = if (isCurrent) Ink.ink else Ink.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// TODO: Move it!!! And check the usage of it!!
suspend fun getFolderList(appRepository: AppRepository, folderId: String?): List<Folder> {
    val folderList = mutableListOf<Folder>()
    var currentId = folderId
    while (currentId != null) {
        val folder = appRepository.folderRepository.get(currentId)
        if (folder != null) {
            folderList.add(folder)
            currentId = folder.parentFolderId
        } else {
            currentId = null
        }
    }
    folderList.reverse()
    return folderList
}

suspend fun getFolderList(appRepository: AppRepository, page: Page?): List<Folder> {
    val folderList = mutableListOf<Folder>()
    var currentFolderId = page?.parentFolderId
    while (currentFolderId != null) {
        val folder = appRepository.folderRepository.get(currentFolderId)
        if (folder != null) {
            folderList.add(folder)
            currentFolderId = folder.parentFolderId
        } else {
            currentFolderId = null
        }
    }
    folderList.reverse() // Parents first
    return folderList
}

@Preview(showBackground = true)
@Composable
fun BreadCrumbPreview() {
    InkaTheme {
        BreadCrumb(
            folders = listOf(
                Folder(id = "folder1", title = "Folder 1"),
                Folder(id = "folder2", title = "Folder 2"),
            ), fontSize = 20, onSelectFolderId = {})
    }
}

package com.ethran.notable.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ethran.notable.ui.theme.Ink
import com.ethran.notable.ui.theme.InkIconButton
import compose.icons.FeatherIcons
import compose.icons.feathericons.Copy
import compose.icons.feathericons.PlusCircle
import compose.icons.feathericons.Trash

/**
 * A page of a notebook in the pages grid: its preview framed by a hairline (ink and doubled when
 * it is the open page) and its page number under it, right-aligned in tabular figures. In
 * [isEditMode] a row of delete, duplicate and add-after actions sits under the number.
 */
@Composable
fun PageCard(
    pageId: String,
    pageIndex: Int,
    isOpen: Boolean,
    isEditMode: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onAddAfter: () -> Unit,
    modifier: Modifier = Modifier,
    touchModifier: Modifier = Modifier,
    isReorderDragging: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Ink.Space.s)) {
        PagePreview(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .border(
                    if (isOpen) Ink.Size.hairline * 2 else Ink.Size.hairline,
                    if (isOpen) Ink.ink else Ink.rule
                )
                .clickable(
                    enabled = isReorderDragging,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpen() }
                .then(touchModifier), pageId
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = (pageIndex + 1).toString(),
                style = (if (isOpen) Ink.Type.label else Ink.Type.meta)
                    .copy(fontFeatureSettings = Ink.Type.TABULAR),
                maxLines = 1
            )
        }

        if (isEditMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InkIconButton(
                    icon = FeatherIcons.Trash,
                    contentDescription = "Delete page",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f, fill = false)
                )
                InkIconButton(
                    icon = FeatherIcons.Copy,
                    contentDescription = "Duplicate page",
                    onClick = onDuplicate,
                    modifier = Modifier.weight(1f, fill = false)
                )
                InkIconButton(
                    icon = FeatherIcons.PlusCircle,
                    contentDescription = "Add page after",
                    onClick = onAddAfter,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

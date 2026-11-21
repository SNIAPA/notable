package com.ethran.notable.ui.dialogs

import androidx.compose.runtime.Composable
import com.ethran.notable.data.db.Notebook

@Composable
fun EmptyBookWarningHandler(
    emptyBook: Notebook, onDelete: (String) -> Unit, onDismiss: () -> Unit
) {
    val title = emptyBook.title
    val createdAt = emptyBook.createdAt
    ShowSimpleConfirmationDialog(
        title = "Delete empty notebook \"$title\"",
        message = "This notebook has no pages. It was created $createdAt.",
        confirmButtonText = "Delete notebook",
        cancelButtonText = "Keep notebook",
        onConfirm = {
            onDelete(emptyBook.id)
        },
        onCancel = { onDismiss() })
}
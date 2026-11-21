package com.ethran.notable.api

import com.ethran.notable.editor.PageView
import com.ethran.notable.editor.state.History

/**
 * The editor that is open, if any, for [StrokeApiServer]. [EditorView][com.ethran.notable.editor.EditorView]
 * attaches its page and history when it opens and detaches them when it closes.
 */
object EditorBridge {
    data class OpenEditor(val page: PageView, val history: History, val bookId: String?)

    @Volatile
    var open: OpenEditor? = null
        private set

    fun attach(page: PageView, history: History, bookId: String?) {
        open = OpenEditor(page, history, bookId)
    }

    /** Detaches [page], unless another editor has attached since. */
    fun detach(page: PageView) {
        if (open?.page === page) open = null
    }
}

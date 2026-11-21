package com.ethran.notable.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ethran.notable.R
import com.ethran.notable.data.db.PageMode
import com.ethran.notable.ui.theme.Ink
import com.ethran.notable.ui.theme.InkSegmented

/**
 * A notebook's page mode as a labelled field: infinite scroll or fixed pages, chosen in a
 * two-cell segmented control.
 */
@Composable
fun PageModeField(pageMode: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.page_mode_label)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Ink.Space.s)) {
        Text(label, style = Ink.Type.meta)
        InkSegmented(
            options = listOf(
                PageMode.INFINITE to stringResource(R.string.page_mode_infinite),
                PageMode.PAGED to stringResource(R.string.page_mode_paged),
            ),
            selected = pageMode,
            onSelect = onChange,
            contentDescription = label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

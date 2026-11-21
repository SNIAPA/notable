package com.ethran.notable.ui.theme

import androidx.compose.material.Typography

/** Material typography mapped onto the Ink type scale, for Material components still in use. */
val Typography = Typography(
    h5 = Ink.Type.title,
    h6 = Ink.Type.title,
    subtitle1 = Ink.Type.label,
    subtitle2 = Ink.Type.label,
    body1 = Ink.Type.body,
    body2 = Ink.Type.body,
    button = Ink.Type.label,
    caption = Ink.Type.meta,
    overline = Ink.Type.section,
)

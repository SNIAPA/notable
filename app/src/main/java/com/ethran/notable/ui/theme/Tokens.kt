package com.ethran.notable.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Design tokens for the app chrome, as documented in docs/DESIGN.md. Screens use these and the
 * primitives in Primitives.kt instead of raw colors, dp or sp values.
 */
object Ink {
    /** The screen's white: backgrounds and content drawn on ink. */
    val paper = Color(0xFFFFFFFF)

    /** Text, icons, control borders, and the fill of selected and primary controls. */
    val ink = Color(0xFF000000)

    /** Secondary text. */
    val ink2 = Color(0xFF4A4A4A)

    /** Hairline rules between regions and around previews. */
    val rule = Color(0xFF9A9A9A)

    object Space {
        val xs = 4.dp
        val s = 8.dp
        val m = 12.dp
        val l = 16.dp
        val xl = 24.dp
        val xxl = 32.dp
        val xxxl = 48.dp

        /** Page edge inset and inline padding of every region. */
        val margin = xl

        /** Gap between sibling items in a row or grid. */
        val gap = l

        /** Gap above a section heading. */
        val section = xxl
    }

    object Size {
        /** Minimum touch target and the size of every control. */
        val target = 48.dp
        val topBar = 64.dp
        val icon = 24.dp
        val iconInline = 20.dp
        val hairline = 1.dp

        /** Width of a notebook or page card in the library grid. */
        val card = 132.dp
    }

    object Type {
        private val sans = FontFamily.SansSerif

        val meta = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 18.sp, color = ink2)
        val body = TextStyle(fontFamily = sans, fontSize = 16.sp, lineHeight = 24.sp, color = ink)
        val label = body.copy(fontWeight = FontWeight.Medium)
        val section = TextStyle(
            fontFamily = sans,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.06.em,
            color = ink2,
        )
        val title = TextStyle(
            fontFamily = sans,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.01).em,
            color = ink,
        )

        /** Font feature for numbers that count or align. */
        const val TABULAR = "tnum"
    }
}

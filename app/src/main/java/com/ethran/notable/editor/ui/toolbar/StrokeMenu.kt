package com.ethran.notable.editor.ui.toolbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.editor.utils.PenSetting
import com.ethran.notable.ui.theme.Ink
import com.ethran.notable.ui.theme.InkRule
import kotlin.math.roundToInt

/**
 * The pen popover: a row of color swatches and a size picker, separated by a rule. The row
 * nearest the toolbar is the colors when the toolbar is at the top and the sizes when it is
 * at the bottom.
 */
@Composable
fun StrokeMenu(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    onClose: () -> Unit,
    sizeOptions: List<Pair<String, Float>>,
    colorOptions: List<Color>,
) {
    // The slider is as wide as five swatches, or the swatch row if that is wider.
    val pickerWidth = Ink.Size.target * colorOptions.size.coerceAtLeast(5)

    ToolbarPopup(onDismiss = onClose) {
        if (toolbarAtBottom()) {
            StrokeSizePicker(value, onChange, sizeOptions, pickerWidth)
            InkRule()
            ColorPicker(value, onChange, colorOptions, Modifier.align(Alignment.CenterHorizontally))
        } else {
            ColorPicker(value, onChange, colorOptions, Modifier.align(Alignment.CenterHorizontally))
            InkRule()
            StrokeSizePicker(value, onChange, sizeOptions, pickerWidth)
        }
    }
}

/**
 * The size picker: the continuous slider when that setting is on and the pen offers at least
 * two sizes, otherwise a row of 48dp size buttons with the current size ink-filled.
 */
@Composable
fun ColumnScope.StrokeSizePicker(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    sizeOptions: List<Pair<String, Float>>,
    widthOfPicker: Dp,
) {
    if (!GlobalAppSettings.current.continuousStrokeSlider || sizeOptions.size < 2) {
        ThicknessPicker(
            value,
            onChange,
            sizeOptions,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    } else {
        DiscreteThicknessSlider(
            value = value.strokeSize,
            onValueChange = { newSize ->
                onChange(PenSetting(strokeSize = newSize, color = value.color))
            },
            values = sizeOptions.map { it.second },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(widthOfPicker)
                .height(Ink.Size.target)
                .padding(horizontal = Ink.Space.l, vertical = Ink.Space.s)
        )
    }
}

/**
 * One 48dp target per color: a swatch with a 1dp ink outline, so white and pale colors read
 * on paper. The current color's target is ink-filled and its swatch outline turns paper.
 */
@Composable
private fun ColorPicker(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    colorOptions: List<Color>,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        colorOptions.forEach { color ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val inked = (color.toArgb() == value.color) != pressed
            Box(
                modifier = Modifier
                    .size(Ink.Size.target)
                    .background(if (inked) Ink.ink else Ink.paper)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            onChange(PenSetting(strokeSize = value.strokeSize, color = color.toArgb()))
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(Ink.Size.icon)
                        .background(color)
                        .border(Ink.Size.hairline, if (inked) Ink.paper else Ink.ink)
                )
            }
        }
    }
}

/** A row of 48dp size buttons, labelled with each size's name. */
@Composable
private fun ThicknessPicker(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    sizeOptions: List<Pair<String, Float>>,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        sizeOptions.forEach { (label, size) ->
            ToolbarButton(
                text = label,
                isSelected = value.strokeSize == size,
                onSelect = { onChange(PenSetting(strokeSize = size, color = value.color)) },
            )
        }
    }
}

/**
 * Discrete slider:
 * - Track is a wedge: thin on the left, gradually thicker to the right.
 * - Tap or drag to change; snapping to nearest integer value between min and max.
 * - A square ink thumb and a down arrow mark the current value.
 * - Takes a List<Float> of suggested values; indicators are shown above these.
 * - All integer values between min and max are selectable by dragging/tapping.
 */
@Composable
private fun DiscreteThicknessSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return // Require at least two values to define a range

    // Use only sorted, distinct values
    val displayValues = values.distinct().sorted()
    val minVal = displayValues.first().toInt()
    val maxVal = displayValues.last().toInt()


    // Map each integer to a normalized position [0,1] along the slider
    fun fractionForInt(v: Int): Float = (v - minVal).toFloat() / (maxVal - minVal).toFloat()

    // For the indicators only: map the suggested values to fractions
    val indicatorFractions = displayValues.map { v ->
        val intV = v.toInt().coerceIn(minVal, maxVal)
        fractionForInt(intV)
    }

    // Clamp and snap to nearest integer on user interaction
    fun snapToNearestInt(xFraction: Float): Float {
        val clamped = xFraction.coerceIn(0f, 1f)
        val rawValue = minVal + ((maxVal - minVal) * clamped)
        return rawValue.roundToInt().coerceIn(minVal, maxVal).toFloat()
    }

    // Thumb's proportional position
    val thumbInt = value.roundToInt().coerceIn(minVal, maxVal)
    val thumbFraction = fractionForInt(thumbInt)

    Box(
        modifier = modifier
            .pointerInput(displayValues) {
                detectTapGestures { offset ->
                    val w = size.width.coerceAtLeast(1)
                    onValueChange(snapToNearestInt(offset.x / w))
                }
            }
            .pointerInput(displayValues) {
                detectDragGestures(onDrag = { change, _ ->
                    val w = size.width.coerceAtLeast(1)
                    onValueChange(snapToNearestInt(change.position.x / w))
                })
            }) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f

            // Wedge parameters (thin -> thick)
            val minTrack = 6f
            val maxTrack = (h * 0.75f).coerceAtLeast(minTrack + 4f)

            fun thicknessAtX(x: Float): Float {
                val frac = if (w > 0) (x / w).coerceIn(0f, 1f) else 0f
                return minTrack + (maxTrack - minTrack) * frac
            }

            // Wedge track path with left thin, right thick (bottom edge kept centered)
            val trackPath = Path().apply {
                moveTo(0f, centerY - minTrack / 2f) // top-left
                lineTo(w, centerY - maxTrack / 2f)  // top-right
                lineTo(w, centerY)                  // bottom-right
                lineTo(0f, centerY)                 // bottom-left
                close()
            }

            // Paper halo first, then the ink fill, then a crisp ink outline
            drawPath(
                path = trackPath,
                color = Ink.paper,
                style = Stroke(
                    width = 6f,
                    cap = StrokeCap.Butt,
                    join = StrokeJoin.Miter
                )
            )
            drawPath(
                path = trackPath,
                color = Ink.ink,
                style = Fill
            )
            drawPath(
                path = trackPath,
                color = Ink.ink,
                style = Stroke(
                    width = 2f,
                    cap = StrokeCap.Butt,
                    join = StrokeJoin.Miter
                )
            )

            // Ticks (indicators) along the top for the suggested values only
            val tickGap = 6f
            val tickHeight = 12f
            for (frac in indicatorFractions) {
                val x = frac * w
                val t = thicknessAtX(x)
                val topY = (centerY - t / 2f)
                val startY = (topY - tickGap - tickHeight).coerceAtLeast(0f)
                val endY = (topY - tickGap).coerceAtLeast(0f)
                drawLine(
                    color = Ink.ink,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Butt
                )
            }

            // Thumb position and prominence
            val thumbX = thumbFraction * w
            val currentThickness = thicknessAtX(thumbX)

            // Vertical thumb: an ink bar on a paper halo
            val thumbW = 10f
            val thumbH = (currentThickness + 14f).coerceAtMost(h * 0.9f) / 2
            val thumbOuterPadding = 3f

            drawRect(
                color = Ink.paper,
                topLeft = Offset(
                    thumbX - thumbW / 2f - thumbOuterPadding, centerY - thumbH - thumbOuterPadding
                ),
                size = Size(
                    thumbW + thumbOuterPadding * 2f, thumbH + thumbOuterPadding * 2f
                )
            )
            drawRect(
                color = Ink.ink,
                topLeft = Offset(thumbX - thumbW / 2f, centerY - thumbH),
                size = Size(thumbW, thumbH)
            )

            // Down arrow pointing to current position for visibility
            val arrowWidth = 30f
            val arrowHeight = 30f
            val downArrow = Path().apply {
                val tipY = (centerY + 6f).coerceAtMost(h)
                moveTo(thumbX, tipY) // tip (upwards)
                lineTo(thumbX - arrowWidth / 2f, (tipY + arrowHeight).coerceAtMost(h))
                lineTo(thumbX + arrowWidth / 2f, (tipY + arrowHeight).coerceAtMost(h))
                close()
            }
            drawPath(downArrow, color = Ink.ink)
        }
    }
}
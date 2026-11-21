package com.ethran.notable.ui.dialogs


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.ethran.notable.R
import com.ethran.notable.data.copyBackgroundToDatabase
import com.ethran.notable.data.ensureBackgroundsFolder
import com.ethran.notable.data.model.BackgroundType
import com.ethran.notable.editor.canvas.CanvasEventBus
import com.ethran.notable.editor.drawing.drawDottedBg
import com.ethran.notable.editor.drawing.drawHexedBg
import com.ethran.notable.editor.drawing.drawLinedBg
import com.ethran.notable.editor.drawing.drawSquaredBg
import com.ethran.notable.editor.utils.autoEInkAnimationOnScroll
import com.ethran.notable.io.getPdfPageCount
import com.ethran.notable.ui.components.OnOffSwitch
import com.ethran.notable.ui.components.ScaledDialog
import compose.icons.FeatherIcons
import compose.icons.feathericons.Loader
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.ethran.notable.ui.theme.Ink
import compose.icons.feathericons.Upload
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Minus
import compose.icons.feathericons.Image
import compose.icons.feathericons.FileText
import com.ethran.notable.ui.theme.InkIconButton
import com.ethran.notable.ui.theme.InkButton
import com.ethran.notable.ui.theme.Flush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.BoxScope
import io.shipbook.shipbooksdk.ShipBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private val log = ShipBook.getLogger("BackgroundSelector")

@Composable
fun BackgroundSelector(
    initialPageBackgroundType: String,
    initialPageBackground: String,
    initialPageNumberInPdf: Int = 0,
    isNotebookBgSelector: Boolean = false, // for notebook default background
    notebookId: String? = null,
    pageNumberInBook: Int = -1,
    onChange: (backgroundType: String, background: String?) -> Unit,
    onClose: () -> Unit
) {
    ScaledDialog(onDismissRequest = { onClose() }) {
        BackgroundSelectorContent(
            initialPageBackgroundType = initialPageBackgroundType,
            initialPageBackground = initialPageBackground,
            initialPageNumberInPdf = initialPageNumberInPdf,
            isNotebookBgSelector = isNotebookBgSelector,
            notebookId = notebookId,
            pageNumberInBook = pageNumberInBook,
            onChange = onChange,
            modifier = Modifier
                .background(Ink.paper)
                .fillMaxWidth()
                .height(550.dp)
                .border(Ink.Size.hairline, Ink.ink)
        )
    }
}

/** The kinds of background as tabs, and the options of the chosen kind. */
@Composable
fun BackgroundSelectorContent(
    initialPageBackgroundType: String,
    initialPageBackground: String,
    initialPageNumberInPdf: Int = 0,
    isNotebookBgSelector: Boolean = false, // for notebook default background
    notebookId: String? = null,
    pageNumberInBook: Int = -1,
    onChange: (backgroundType: String, background: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pageBackground by remember { mutableStateOf(initialPageBackground) }
    var maxPages: Int? by remember { mutableStateOf(getPdfPageCount(pageBackground)) }
    val currentPage: Int? by remember { mutableIntStateOf(initialPageNumberInPdf) }

    var pageBackgroundType: BackgroundType by remember {
        mutableStateOf(
            BackgroundType.fromKey(
                initialPageBackgroundType
            )
        )
    }

    var selectedBackgroundMode by remember {
        mutableStateOf(
            when (pageBackgroundType) {
                is BackgroundType.CoverImage -> "Cover"
                is BackgroundType.Image, is BackgroundType.ImageRepeating -> "Image"
                is BackgroundType.Pdf, is BackgroundType.AutoPdf -> "PDF"
                else -> "Native"
            }
        )
    }

    fun selectedToType(): BackgroundType {
        return when (selectedBackgroundMode) {
            "Cover" -> BackgroundType.CoverImage
            "Image" -> BackgroundType.Image
            "PDF" -> BackgroundType.Pdf(1)
            else -> {
                throw Exception("Unknown BackgroundType for selection $selectedBackgroundMode")
            }

        }
    }

    // Create an activity result launcher for picking visual media (images in this case)
    val pickMedia =
        rememberLauncherForActivityResult(contract = PickVisualMedia()) { uri ->
            if (uri == null) {
                log.w("PickVisualMedia: uri is null (user cancelled or provider returned null)")
                return@rememberLauncherForActivityResult
            }

            val currentType = selectedToType()
            log.d("PickVisualMedia: will copy to subfolder=\"$currentType\"")

            scope.launch(Dispatchers.IO) {
                try {
                    val copiedFile = copyBackgroundToDatabase(context, uri, currentType.folderName)

                    log.i("PickVisualMedia: copied -> ${copiedFile.absolutePath}")
                    onChange(currentType.key, copiedFile.toString())
                    scope.launch { CanvasEventBus.refreshUi.emit(Unit) }
                    pageBackground = copiedFile.toString()
                    log.d("PickVisualMedia: UI updated, pageBackground=$pageBackground, type=${currentType.key}")

                } catch (e: Exception) {
                    log.e("PickVisualMedia: copy failed: ${e.message}", e)
                }
            }
        }
    // PDF picker for backgrounds
    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            log.w("PickPdf: uri is null (user cancelled or provider returned null)")
            return@rememberLauncherForActivityResult
        }

        val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flag)

        val currentType = selectedToType()
        log.d("PickPdf: will copy to subfolder=\"$currentType\"")
        scope.launch(Dispatchers.IO) {
            try {
                val copiedFile = copyBackgroundToDatabase(context, uri, currentType.folderName)
                onChange(currentType.key, copiedFile.toString())
                scope.launch { CanvasEventBus.refreshUi.emit(Unit) }
                pageBackground = copiedFile.toString()
                pageBackgroundType = currentType
                log.i("PDF was received and copied, it is now at:${copiedFile.toUri()}")
                log.i("PageSettingsModal: $pageBackgroundType")
            } catch (e: Exception) {
                log.e("PdfPicker: copy failed: ${e.message}", e)
            }
        }
    }

    Column(modifier = modifier) {
        BackgroundKindSegments(
            selected = selectedBackgroundMode,
            onSelect = { selectedBackgroundMode = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Ink.Space.margin, vertical = Ink.Space.s),
        )
        Column(Modifier.padding(horizontal = Ink.Space.margin, vertical = Ink.Space.l)) {
            when (selectedBackgroundMode) {
                "Image" -> {
                    val currentBackgroundType =
                        if (pageBackgroundType == BackgroundType.ImageRepeating || pageBackgroundType == BackgroundType.Image)
                            pageBackgroundType else BackgroundType.Image
                    ShowImageOption(
                        currentBackground = pageBackground,
                        currentBackgroundType = currentBackgroundType,
                        onBackgroundChange = { background, type ->
                            onChange(type.key, background)
                            pageBackground = background
                            pageBackgroundType = type
                        },
                        onRequestFilePicker = {
                            pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        }
                    )
                    if (pageBackgroundType == BackgroundType.ImageRepeating || pageBackgroundType == BackgroundType.Image) {
                        Row(
                            modifier = Modifier
                                .padding(top = Ink.Space.xl)
                                .heightIn(min = Ink.Size.target),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Ink.Space.l),
                        ) {
                            Text(stringResource(R.string.repeat_background), style = Ink.Type.body)
                            OnOffSwitch(
                                checked = pageBackgroundType == BackgroundType.ImageRepeating,
                                onCheckedChange = { isChecked ->
                                    pageBackgroundType =
                                        if (isChecked) BackgroundType.ImageRepeating else BackgroundType.Image

                                    onChange(pageBackgroundType.key, null)
                                }
                            )
                        }
                    }
                }

                "Cover" -> {
                    ShowImageOption(
                        currentBackground = pageBackground,
                        currentBackgroundType = BackgroundType.CoverImage,
                        onBackgroundChange = { background, type ->
                            onChange(type.key, background)
                            pageBackground = background
                            log.d("onBackgroundChange: $type")
                            pageBackgroundType = type
                        },
                        onRequestFilePicker = {
                            log.d("onRequestFilePicker: $pageBackgroundType")
                            pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        }
                    )
                }

                "Native" -> {
                    ShowNativeOption(
                        currentBackground = pageBackground,
                        currentBackgroundType = BackgroundType.Native,
                        onBackgroundChange = { background, type ->
                            onChange(type.key, background)
                            pageBackground = background
                            pageBackgroundType = type
                        },
                    )
                }

                "PDF" -> {
                    val currentBackgroundType =
                        if (pageBackgroundType == BackgroundType.AutoPdf || pageBackgroundType is BackgroundType.Pdf)
                            pageBackgroundType else BackgroundType.Pdf(1)

                    fun onBackgroundChange(type: BackgroundType, background: String) {
                        onChange(type.key, background)
                        pageBackground = background
                        pageBackgroundType = type
                        maxPages = getPdfPageCount(background)
                    }
                    ShowPdfOption(
                        currentBackground = pageBackground,
                        currentBackgroundType = currentBackgroundType,
                        onBackgroundChange = ::onBackgroundChange,
                        onRequestFilePicker = {
                            log.d("onRequestFilePicker: $pageBackgroundType")
                            pickPdf.launch(arrayOf("application/pdf"))
                        }
                    )
                    PageNumberSelector(
                        currentBackground = pageBackground,
                        currentBackgroundType = pageBackgroundType,
                        maxPages = maxPages,
                        currentPage = currentPage,
                        onBackgroundChange = ::onBackgroundChange,
                        showAutoPdfOption = (notebookId != null) && ((maxPages
                            ?: 0) > pageNumberInBook),
                        isNotebookBgSelector = isNotebookBgSelector
                    )

                }

            }
        }
    }
}


@Composable
fun ShowNativeOption(
    currentBackground: String,
    currentBackgroundType: BackgroundType,
    onBackgroundChange: (String, BackgroundType) -> Unit
) {
    val nativeOptions = listOf(
        "blank" to stringResource(R.string.blank_page),
        "dotted" to stringResource(R.string.dot_grid),
        "lined" to stringResource(R.string.lines),
        "squared" to stringResource(R.string.small_squares_grid),
        "hexed" to stringResource(R.string.hexagon_grid)
    )

    val context = LocalContext.current
    val backgroundFolder = File(context.filesDir, "native_backgrounds")

    val bitmaps = remember {
        mutableStateMapOf<String, Bitmap?>()
    }

    LaunchedEffect(Unit) {
        if (!backgroundFolder.exists()) {
            backgroundFolder.mkdirs()
        }

        nativeOptions.forEach { (key, _) ->
            val file = File(backgroundFolder, "$key.png")
            if (!file.exists()) {
                val bitmap = createBitmap(300, 550)
                val canvas = Canvas(bitmap)

                when (key) {
                    "blank" -> canvas.drawColor(Color.White.toArgb())
                    "dotted" -> drawDottedBg(canvas, Offset.Zero, 1f)
                    "lined" -> drawLinedBg(canvas, Offset.Zero, 1f)
                    "squared" -> drawSquaredBg(canvas, Offset.Zero, 1f)
                    "hexed" -> drawHexedBg(canvas, Offset.Zero, 0.4f)
                }

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                bitmaps[key] = bitmap
            } else {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                bitmaps[key] = bitmap
            }
        }
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Ink.Space.gap),
        verticalArrangement = Arrangement.spacedBy(Ink.Space.xl),
    ) {
        nativeOptions.forEach { (value, label) ->
            TemplateTile(
                label = label,
                selected = value == currentBackground,
                onClick = { onBackgroundChange(value, currentBackgroundType) },
            ) {
                val bitmap = bitmaps[value]
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.None,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = FeatherIcons.Loader,
                        contentDescription = stringResource(R.string.background_preview_loading),
                        tint = Ink.ink2,
                        modifier = Modifier.size(Ink.Size.icon),
                    )
                }
            }
        }
    }
}

/** Background kinds offered by [BackgroundSelectorContent]: the mode key and its display name. */
@Composable
private fun backgroundKinds(): List<Pair<String, String>> = listOf(
    "Native" to stringResource(R.string.background_kind_native),
    "Image" to stringResource(R.string.background_kind_image),
    "Cover" to stringResource(R.string.background_kind_cover),
    "PDF" to stringResource(R.string.background_kind_pdf),
)

/**
 * The background kind as a segmented control: equal-width cells in one hairline ink frame, the
 * [selected] mode key filled with ink. A cell inverts while pressed.
 */
@Composable
private fun BackgroundKindSegments(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = backgroundKinds()
    val groupLabel = stringResource(R.string.background_kind_label)
    Row(
        modifier = modifier
            .height(Ink.Size.target)
            .border(Ink.Size.hairline, Ink.ink)
            .selectableGroup()
            .semantics { contentDescription = groupLabel },
    ) {
        kinds.forEachIndexed { index, (key, name) ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(Ink.Size.hairline)
                        .background(Ink.ink)
                )
            }
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val inked = (key == selected) != pressed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (inked) Ink.ink else Ink.paper)
                    .selectable(
                        selected = key == selected,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(key) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    style = Ink.Type.label,
                    color = if (inked) Ink.paper else Ink.ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A background choice: a 3:4 [preview] in a hairline rule frame with the name below. When
 * [selected] the frame is 2dp ink and the name is set in ink at weight 500; the ink frame also
 * shows while the tile is pressed.
 */
@Composable
private fun TemplateTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val framed = selected || pressed
    Column(
        modifier = Modifier
            .width(Ink.Size.card)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(Ink.Space.s),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .background(Ink.paper)
                .border(
                    width = if (framed) Ink.Size.hairline * 2 else Ink.Size.hairline,
                    color = if (framed) Ink.ink else Ink.rule,
                )
                .padding(if (framed) Ink.Size.hairline * 2 else Ink.Size.hairline)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
            content = preview,
        )
        Row(
            modifier = Modifier.heightIn(min = Ink.Size.iconInline),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ink.Space.xs),
        ) {
            Text(
                text = label,
                style = if (selected) {
                    Ink.Type.meta.copy(color = Ink.ink, fontWeight = FontWeight.Medium)
                } else {
                    Ink.Type.meta
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A glyph centered in a tile preview, for choices that have no picture of their own. */
@Composable
private fun TileGlyph(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = Ink.ink, modifier = Modifier.size(Ink.Size.icon))
}

/** Image backgrounds: the bundled image, the images copied in before, and a tile to add one. */
@Composable
fun ShowImageOption(
    currentBackground: String,
    currentBackgroundType: BackgroundType,
    onBackgroundChange: (String, BackgroundType) -> Unit,
    onRequestFilePicker: () -> Unit
) {
    val folder = File(ensureBackgroundsFolder(), currentBackgroundType.folderName)
    val stored = folder.listFiles()?.filter { it.isFile }?.map { it.absolutePath to it.nameWithoutExtension }
        ?: emptyList()

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Ink.Space.gap),
        verticalArrangement = Arrangement.spacedBy(Ink.Space.xl),
    ) {
        TemplateTile(
            label = stringResource(R.string.background_image_iris),
            selected = currentBackground == "iris",
            onClick = { onBackgroundChange("iris", currentBackgroundType) },
        ) {
            Image(
                painter = painterResource(id = R.drawable.iris),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        stored.forEach { (path, name) ->
            TemplateTile(
                label = name,
                selected = currentBackground == path,
                onClick = { onBackgroundChange(path, currentBackgroundType) },
            ) {
                val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    TileGlyph(FeatherIcons.Image)
                }
            }
        }
        TemplateTile(
            label = stringResource(R.string.background_add_image),
            selected = false,
            onClick = onRequestFilePicker,
        ) { TileGlyph(FeatherIcons.Upload) }
    }
}

/** PDF backgrounds: the PDFs copied in before, and a tile to import one. */
@Composable
fun ShowPdfOption(
    currentBackground: String,
    currentBackgroundType: BackgroundType,
    onBackgroundChange: (BackgroundType, String) -> Unit,
    onRequestFilePicker: () -> Unit,
) {
    val folder = File(ensureBackgroundsFolder(), currentBackgroundType.folderName)
    val stored = folder.listFiles()?.filter { it.isFile }?.map { it.absolutePath to it.nameWithoutExtension }
        ?: emptyList()

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Ink.Space.gap),
        verticalArrangement = Arrangement.spacedBy(Ink.Space.xl),
    ) {
        stored.forEach { (path, name) ->
            TemplateTile(
                label = name,
                selected = currentBackground == path,
                onClick = { onBackgroundChange(currentBackgroundType, path) },
            ) { TileGlyph(FeatherIcons.FileText) }
        }
        TemplateTile(
            label = stringResource(R.string.background_import_pdf),
            selected = false,
            onClick = onRequestFilePicker,
        ) { TileGlyph(FeatherIcons.Upload) }
    }
}

/**
 * The PDF page used as the background: a stepper with the page number, and, when offered, a
 * toggle that follows the notebook's page numbers through the PDF. Muted until a PDF is chosen.
 */
@Composable
fun PageNumberSelector(
    currentBackground: String,
    currentBackgroundType: BackgroundType,
    maxPages: Int?,
    currentPage: Int?,
    onBackgroundChange: (BackgroundType, String) -> Unit,
    showAutoPdfOption: Boolean,
    isNotebookBgSelector: Boolean
) {
    val pdfSelectorEnabled =
        currentBackground.endsWith(".pdf") && maxPages != null && currentPage != null

    var pageText by remember { mutableStateOf((currentPage ?: 1).toString()) }
    val pageNumber = pageText.toIntOrNull() ?: 1

    fun goTo(page: Int) {
        pageText = page.toString()
        onBackgroundChange(BackgroundType.Pdf(page - 1), currentBackground)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Ink.Space.xl)
            .graphicsLayer(alpha = if (pdfSelectorEnabled) 1f else 0.5f),
        verticalArrangement = Arrangement.spacedBy(Ink.Space.s),
    ) {
        Text(stringResource(R.string.select_pdf_page), style = Ink.Type.meta)
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkIconButton(
                icon = FeatherIcons.Minus,
                contentDescription = stringResource(R.string.background_pdf_previous_page),
                onClick = { if (pageNumber > 1) goTo((pageNumber - 1).coerceAtMost(maxPages ?: 1)) },
                flush = Flush.Start,
            )
            BasicTextField(
                value = pageText,
                onValueChange = {
                    val input = it.toIntOrNull()
                    if (input != null && input in 1..(maxPages ?: 1)) goTo(input) else pageText = it
                },
                singleLine = true,
                textStyle = Ink.Type.title.copy(
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = Ink.Type.TABULAR,
                ),
                cursorBrush = SolidColor(Ink.ink),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .width(Ink.Size.target * 2)
                    .border(Ink.Size.hairline, Ink.ink)
                    .padding(vertical = Ink.Space.m),
            )
            InkIconButton(
                icon = FeatherIcons.Plus,
                contentDescription = stringResource(R.string.background_pdf_next_page),
                onClick = { if (pageNumber < (maxPages ?: 1)) goTo((pageNumber + 1).coerceAtLeast(1)) },
            )
            Text(
                pluralStringResource(R.plurals.pdf_has_pages, maxPages ?: 0, maxPages ?: 0),
                style = Ink.Type.meta.copy(fontFeatureSettings = Ink.Type.TABULAR),
                modifier = Modifier
                    .padding(start = Ink.Space.s)
                    .weight(1f),
            )
            if (showAutoPdfOption) {
                val following = currentBackgroundType == BackgroundType.AutoPdf
                val name = if (isNotebookBgSelector) stringResource(R.string.observe_pdf)
                else stringResource(R.string.current_page)
                InkButton(
                    text = name,
                    icon = FeatherIcons.RefreshCw,
                    primary = following,
                    onClick = {
                        if (!following) {
                            onBackgroundChange(BackgroundType.AutoPdf, currentBackground)
                        } else {
                            onBackgroundChange(BackgroundType.Pdf(pageNumber), currentBackground)
                        }
                        log.d("PageNumberSelector: following=${!following}, $currentBackgroundType")
                    },
                )
            }
        }
    }
}

package com.ethran.notable.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.MAX_PRESSURE_NORMALIZED
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.drawing.StrokeStyleRegistry
import com.ethran.notable.editor.state.Operation
import com.ethran.notable.editor.utils.Pen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.UUID
import kotlin.concurrent.thread

private const val TAG = "StrokeApi"

@Serializable
data class StrokeDto(
    val id: String? = null,
    val pen: String = "BALLPEN",
    val size: Float = 3f,
    /** ARGB, e.g. 4278190080 (0xFF000000) for black. */
    val color: Long = 0xFF000000,
    /** Each point is [x, y] or [x, y, pressure 0..1], in page coordinates. */
    val points: List<List<Float>>,
    /** [left, top, right, bottom], filled in on output. */
    val bbox: List<Float>? = null,
)

@Serializable
data class OpsRequest(
    /** The page the edit is meant for; refused when another page is open. */
    val pageId: String? = null,
    val add: List<StrokeDto> = emptyList(),
    val update: List<StrokeDto> = emptyList(),
    val delete: List<String> = emptyList(),
)

@Serializable
data class OpsResponse(val pageId: String, val added: List<String>, val updated: List<String>, val deleted: List<String>)

@Serializable
data class Viewport(val scrollX: Float, val scrollY: Float, val zoom: Float)

@Serializable
data class StatusDto(
    val editorOpen: Boolean,
    val pageId: String? = null,
    val bookId: String? = null,
    val pageLoaded: Boolean = false,
    /** Screen pixel = (page coordinate - scroll) * zoom. */
    val viewport: Viewport? = null,
    val screenWidth: Int,
    val screenHeight: Int,
    val strokeCount: Int = 0,
)

@Serializable
data class NotebookDto(val id: String, val title: String, val pageIds: List<String>, val openPageId: String?)

@Serializable
data class OpenRequest(val pageId: String)

@Serializable
data class ErrorDto(val error: String)

private class ApiError(val code: Int, message: String) : Exception(message)

private class Response(val code: Int, val body: ByteArray, val type: String = "application/json")

/**
 * A small HTTP API on 127.0.0.1:[PORT] for reading the open page's strokes and editing them.
 * Edits go through [com.ethran.notable.editor.state.History.applyOperations], so they draw at once,
 * are saved like pen strokes and undo as one step. Every request needs the header
 * `Authorization: Bearer <token>`, with the token from `Documents/notable/api-token`.
 */
object StrokeApiServer {
    const val PORT = 8765

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var serverSocket: ServerSocket? = null
    private var token: String = ""
    private lateinit var appContext: Context
    private lateinit var appRepository: AppRepository

    @Synchronized
    fun start(context: Context, repository: AppRepository) {
        if (serverSocket != null) return
        appContext = context.applicationContext
        appRepository = repository
        try {
            token = loadOrCreateToken()
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), PORT))
            serverSocket = socket
            thread(name = "stroke-api", isDaemon = true) { acceptLoop(socket) }
            Log.i(TAG, "listening on 127.0.0.1:$PORT")
        } catch (e: Exception) {
            Log.e(TAG, "could not start", e)
        }
    }

    @Synchronized
    fun stop() {
        serverSocket?.close()
        serverSocket = null
    }

    private fun tokenFile(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "notable/api-token"
    )

    private fun loadOrCreateToken(): String {
        val file = tokenFile()
        file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.length >= 16 }?.let { return it }
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val created = bytes.joinToString("") { "%02x".format(it) }
        file.parentFile?.mkdirs()
        file.writeText(created)
        return created
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                break
            }
            thread(name = "stroke-api-conn", isDaemon = true) { client.use { handle(it) } }
        }
    }

    private fun handle(client: Socket) {
        val input = BufferedInputStream(client.getInputStream())
        val head = readHead(input) ?: return
        val lines = head.split("\r\n")
        val (method, target) = lines[0].split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
        val headers = lines.drop(1).mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val response = try {
            if (headers["authorization"] != "Bearer $token") throw ApiError(401, "missing or wrong token")
            route(method, path, query, String(body, Charsets.UTF_8))
        } catch (e: ApiError) {
            error(e.code, e.message ?: "error")
        } catch (e: Exception) {
            Log.e(TAG, "request failed: $method $path", e)
            error(400, e.message ?: e.javaClass.simpleName)
        }
        val out = client.getOutputStream()
        out.write(
            ("HTTP/1.1 ${response.code} ${if (response.code < 400) "OK" else "Error"}\r\n" +
                    "Content-Type: ${response.type}\r\nContent-Length: ${response.body.size}\r\n" +
                    "Connection: close\r\n\r\n").toByteArray()
        )
        out.write(response.body)
        out.flush()
    }

    private fun readHead(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf(13, 10, 13, 10)
        while (matched < 4) {
            val b = input.read()
            if (b < 0) return null
            buffer.write(b)
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b == 13) 1 else 0
            if (buffer.size() > 64 * 1024) return null
        }
        return buffer.toString("UTF-8").trimEnd()
    }

    private fun error(code: Int, message: String) =
        Response(code, json.encodeToString(ErrorDto.serializer(), ErrorDto(message)).toByteArray())

    private inline fun <reified T> ok(value: T) = Response(200, json.encodeToString(value).toByteArray())

    private fun editor(): EditorBridge.OpenEditor =
        EditorBridge.open ?: throw ApiError(409, "no page is open in the editor")

    private fun route(method: String, path: String, query: Map<String, String>, body: String): Response =
        when ("$method $path") {
            "GET /status" -> ok(status())
            "GET /notebooks" -> ok(appRepository.bookRepository.getAll().map {
                NotebookDto(it.id, it.title, it.pageIds, it.openPageId)
            })
            "GET /page/current/strokes" -> ok(currentStrokes(query))
            "POST /page/current/ops" -> ok(applyOps(json.decodeFromString(OpsRequest.serializer(), body)))
            "POST /undo" -> ok(runOnMain { editor().history.undo() }.let { status() })
            "POST /redo" -> ok(runOnMain { editor().history.redo() }.let { status() })
            "POST /open" -> ok(open(json.decodeFromString(OpenRequest.serializer(), body)))
            else -> throw ApiError(404, "no route $method $path")
        }

    private fun <T> runOnMain(block: suspend () -> T): T = runBlocking(Dispatchers.Main) { block() }

    private fun status(): StatusDto {
        val metrics = appContext.resources.displayMetrics
        val open = EditorBridge.open ?: return StatusDto(
            editorOpen = false, screenWidth = metrics.widthPixels, screenHeight = metrics.heightPixels
        )
        val viewport = open.page.viewport.value
        return StatusDto(
            editorOpen = true,
            pageId = open.page.currentPageId,
            bookId = open.bookId,
            pageLoaded = open.page.isPageDataLoaded,
            viewport = Viewport(viewport.scroll.x, viewport.scroll.y, viewport.zoom),
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            strokeCount = open.page.strokes.size,
        )
    }

    private fun currentStrokes(query: Map<String, String>): List<StrokeDto> {
        val strokes = editor().page.strokes
        val region = listOf("left", "top", "right", "bottom").map { query[it]?.toFloatOrNull() }
        val filtered = if (region.all { it != null }) {
            val (l, t, r, b) = region.map { it!! }
            strokes.filter { it.right >= l && it.left <= r && it.bottom >= t && it.top <= b }
        } else strokes
        return filtered.map { it.toDto() }
    }

    private fun applyOps(request: OpsRequest): OpsResponse {
        val open = editor()
        val pageId = open.page.currentPageId
        if (request.pageId != null && request.pageId != pageId) {
            throw ApiError(409, "page ${request.pageId} is not open; the open page is $pageId")
        }
        if (!open.page.isPageDataLoaded) throw ApiError(503, "the open page is still loading")
        val added = request.add.map { it.toStroke(pageId, UUID.randomUUID().toString()) }
        val updated = request.update.map { dto ->
            val id = dto.id ?: throw ApiError(400, "update needs a stroke id")
            dto.toStroke(pageId, id)
        }
        val missing = open.page.getStrokes(updated.map { it.id }).zip(updated).filter { it.first == null }
        if (missing.isNotEmpty()) throw ApiError(404, "unknown stroke ids: ${missing.map { it.second.id }}")
        val deleted = open.page.getStrokes(request.delete).filterNotNull().map { it.id }
        val operations = buildList {
            if (deleted.isNotEmpty()) add(Operation.DeleteStroke(deleted))
            if (updated.isNotEmpty()) add(Operation.UpdateStroke(updated))
            if (added.isNotEmpty()) add(Operation.AddStroke(added))
        }
        val applied = runOnMain { open.history.applyOperations(operations) }
        if (!applied) throw ApiError(503, "the open page is still loading")
        return OpsResponse(pageId, added.map { it.id }, updated.map { it.id }, deleted)
    }

    private fun open(request: OpenRequest): StatusDto {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("notable://page/${request.pageId}"))
            .setPackage(appContext.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return status()
    }

    private fun StrokeDto.toStroke(pageId: String, id: String): Stroke {
        if (points.isEmpty()) throw ApiError(400, "a stroke needs at least one point")
        val strokePoints = points.map { p ->
            if (p.size < 2) throw ApiError(400, "a point is [x, y] or [x, y, pressure]")
            StrokePoint(x = p[0], y = p[1], pressure = (p.getOrNull(2) ?: 0.6f).coerceIn(0f, 1f))
        }
        val inkPen = Pen.fromString(pen)
        if (StrokeStyleRegistry.forPen(inkPen) == null) throw ApiError(400, "pen $pen draws no ink")
        val pad = size / 2 + 1
        return Stroke(
            id = id,
            size = size,
            pen = inkPen,
            color = color.toInt(),
            maxPressure = MAX_PRESSURE_NORMALIZED,
            top = strokePoints.minOf { it.y } - pad,
            bottom = strokePoints.maxOf { it.y } + pad,
            left = strokePoints.minOf { it.x } - pad,
            right = strokePoints.maxOf { it.x } + pad,
            points = strokePoints,
            pageId = pageId,
        )
    }

    private fun Stroke.toDto() = StrokeDto(
        id = id,
        pen = pen.penName,
        size = size,
        color = color.toLong() and 0xFFFFFFFFL,
        points = points.map { p -> listOf(p.x, p.y, p.pressure ?: 0.6f) },
        bbox = listOf(left, top, right, bottom),
    )
}

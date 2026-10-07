package brobata.physiboard.ime.actions

import android.content.ClipDescription
import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import brobata.physiboard.core.actions.gif.GifItem
import brobata.physiboard.core.actions.gif.GifPage
import brobata.physiboard.core.actions.gif.GifShelf
import brobata.physiboard.core.shell.FetchResult
import brobata.physiboard.core.shell.GatedFetcher
import brobata.physiboard.core.shell.NetworkPurpose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * The GIF page's favourites and recents, in the `gif_prefs` file. Read and written on the main
 * thread from a tap, never from the key path; SharedPreferences applies writes in the background.
 * spec: layers-sym-alt.md SS4.5.
 */
internal class GifShelfStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun favourites(): List<GifItem> = GifShelf.decode(prefs.getString(FAVOURITES, null))
    fun recents(): List<GifItem> = GifShelf.decode(prefs.getString(RECENTS, null))
    fun saveFavourites(items: List<GifItem>) = prefs.edit().putString(FAVOURITES, GifShelf.encode(items)).apply()
    fun saveRecents(items: List<GifItem>) = prefs.edit().putString(RECENTS, GifShelf.encode(items)).apply()

    private companion object {
        const val FILE = "gif_prefs"
        const val FAVOURITES = "gif_favourites"
        const val RECENTS = "gif_recents"
    }
}

/**
 * Previews for the GIF grid. Bytes come through the network gate ([NetworkPurpose.GIF_MEDIA]),
 * are kept in memory only (KLIPY's integration rules forbid keeping copies of their media on
 * disk, so there is no disk cache), and are decoded off the main thread with the platform's
 * [ImageDecoder] into an [AnimatedImageDrawable] (animated WebP or GIF; API 28+, minSdk is 31).
 * spec: layers-sym-alt.md SS4.5.
 */
internal class GifPreviews(private val fetcher: () -> GatedFetcher?) {
    private val bytes = object : LruCache<String, ByteArray>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private var permits = Semaphore(PARALLEL)
    private var scope = newScope()

    /** Loads [url] and hands the decoded drawable (or null) to [onReady] on the main thread. */
    fun load(url: String, targetWidthPx: Int, onReady: (Drawable?) -> Unit) {
        val slots = permits
        scope.launch {
            val drawable = slots.withPermit {
                val data = bytes.get(url) ?: when (val r = fetcher()?.get(NetworkPurpose.GIF_MEDIA, url, GifPage.MAX_PREVIEW_BYTES)) {
                    is FetchResult.Ok -> r.body.also { bytes.put(url, it) }
                    else -> null
                }
                data?.let { withContext(Dispatchers.Default) { decode(it, targetWidthPx) } }
            }
            onReady(drawable)
        }
    }

    /** Drops every load in flight (the page closed); the memory cache stays for the next open. */
    fun cancelAll() {
        scope.cancel()
        scope = newScope()
        // A cancelled download may still be unwinding; the next grid's previews get slots of their own.
        permits = Semaphore(PARALLEL)
    }

    private fun decode(data: ByteArray, targetWidthPx: Int): Drawable? = runCatching {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data))) { decoder, info, _ ->
            val w = info.size.width
            if (targetWidthPx in 1 until w) {
                val h = (info.size.height.toLong() * targetWidthPx / w).toInt().coerceAtLeast(1)
                decoder.setTargetSize(targetWidthPx, h)
            }
        }
    }.onFailure { Log.w(TAG, "preview decode failed", it) }.getOrNull()

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private companion object {
        const val TAG = "PhysiBoardGifPreviews"
        const val MEMORY_BYTES = 8 * 1024 * 1024
        const val PARALLEL = 4
    }
}

/**
 * Puts a chosen GIF into the field. spec: layers-sym-alt.md SS4.5: when the field declares a
 * content type that takes `image/gif`, the GIF is downloaded through the gate, written to the
 * cache path `gif-share/`, and committed with [InputConnectionCompat.commitContent] and a read
 * grant; otherwise, or when the app refuses the content, the GIF's address is typed as text.
 * Lives as long as the keyboard service, so a send finishes even when the page closes first.
 */
internal class GifSender(
    private val service: InputMethodService,
    private val fetcher: () -> GatedFetcher?,
    private val offlineReason: () -> String?,
    /**
     * Changes every time the keyboard attaches to or leaves a field. Two chats in one app share
     * their compose box's package and field id, so only this tells "the same field, still open"
     * from "another conversation".
     */
    private val inputSession: () -> Int,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** [onSent] runs on the main thread once the GIF (or its address) went into the field. */
    fun send(item: GifItem, onSent: () -> Unit, onMessage: (String) -> Unit) {
        val startInfo = service.currentInputEditorInfo ?: return
        val session = inputSession()
        if (!GifPage.fieldAcceptsGif(EditorInfoCompat.getContentMimeTypes(startInfo).toList())) {
            if (commitLink(item)) onSent()
            return
        }
        offlineReason()?.let { reason ->
            onMessage(reason)
            return
        }
        val fetch = fetcher() ?: run { onMessage(GifPage.FAILED); return }
        scope.launch {
            when (val r = fetch.get(NetworkPurpose.GIF_MEDIA, item.sendUrl, GifPage.MAX_SEND_BYTES)) {
                is FetchResult.Blocked -> onMessage(r.reason)
                is FetchResult.Failed -> onMessage(SEND_FAILED)
                is FetchResult.Ok -> {
                    val file = withContext(Dispatchers.IO) { runCatching { write(item, r.body) }.onFailure { Log.e(TAG, "gif write failed", it) }.getOrNull() }
                    if (file == null) {
                        onMessage(SEND_FAILED)
                        return@launch
                    }
                    val info = service.currentInputEditorInfo
                    if (info == null || inputSession() != session || !sameField(startInfo, info)) {
                        onMessage(FIELD_CHANGED)
                        return@launch
                    }
                    if (commitFile(item, file, info) || commitLink(item)) onSent()
                }
            }
        }
    }

    private fun commitFile(item: GifItem, file: File, info: EditorInfo): Boolean = runCatching {
        val ic: InputConnection = service.currentInputConnection ?: return false
        val uri = FileProvider.getUriForFile(service, "${service.packageName}.gifshare", file)
        val content = InputContentInfoCompat(uri, ClipDescription(item.title.ifBlank { "GIF" }, arrayOf(GIF_MIME)), Uri.parse(item.sendUrl))
        InputConnectionCompat.commitContent(ic, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
    }.onFailure { Log.e(TAG, "commitContent crashed", it) }.getOrDefault(false)

    private fun commitLink(item: GifItem): Boolean = runCatching {
        val ic = service.currentInputConnection ?: return false
        ic.beginBatchEdit()
        ic.finishComposingText()
        val committed = ic.commitText(item.sendUrl, 1)
        ic.endBatchEdit()
        committed
    }.onFailure { Log.e(TAG, "gif link commit crashed", it) }.getOrDefault(false)

    /**
     * One file per send in `gif-share/` (a unique name, so two sends of one GIF never write the
     * same file). The receiving app reads it through its grant soon after the commit; files older
     * than [KEEP_MS], and all but the [KEEP_FILES] newest, are deleted on every send and when the
     * keyboard service ends.
     */
    private fun write(item: GifItem, body: ByteArray): File {
        val dir = File(service.cacheDir, SHARE_DIR).apply { mkdirs() }
        val name = item.slug.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60).ifEmpty { "gif" } + "-" + System.nanoTime() + ".gif"
        val file = File(dir, name)
        file.writeBytes(body)
        prune(keep = file)
        return file
    }

    private fun prune(keep: File?) {
        val dir = File(service.cacheDir, SHARE_DIR)
        val now = System.currentTimeMillis()
        val files = dir.listFiles()?.filter { it != keep }?.sortedByDescending { it.lastModified() } ?: return
        files.forEachIndexed { index, f -> if (index >= KEEP_FILES - 1 || now - f.lastModified() > KEEP_MS) f.delete() }
    }

    private fun sameField(a: EditorInfo, b: EditorInfo): Boolean = a.packageName == b.packageName && a.fieldId == b.fieldId

    fun onServiceDestroyed() {
        scope.cancel()
        runCatching { prune(keep = null) }
    }

    private companion object {
        const val TAG = "PhysiBoardGifSender"
        const val GIF_MIME = "image/gif"
        const val SHARE_DIR = "gif-share"
        const val KEEP_FILES = 4
        const val KEEP_MS = 10 * 60 * 1000L
        const val SEND_FAILED = "Couldn't download the GIF."
        const val FIELD_CHANGED = "The text field changed, so the GIF was not sent."
    }
}

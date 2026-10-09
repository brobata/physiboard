package brobata.physiboard.design

import android.content.Context
import android.graphics.Typeface
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * The two vendored typefaces as [Typeface]s, for code that draws with Views (the keyboard's
 * panels). Compose screens use the same files through `res/font` directly.
 *
 * Loading a font reads its file, so the keyboard calls [prewarm] off the main thread when it
 * starts and the first Sym page never waits on the disk; a face asked for before that finishes is
 * loaded then and cached. A font that fails to load falls back to the system's monospace or sans,
 * so a panel always draws.
 */
object PhysiFonts {
    enum class Face(internal val res: Int, internal val fallback: Typeface) {
        MONO(R.font.jetbrains_mono_regular, Typeface.MONOSPACE),
        MONO_MEDIUM(R.font.jetbrains_mono_medium, Typeface.MONOSPACE),
        MONO_BOLD(R.font.jetbrains_mono_bold, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)),
        SANS(R.font.inter_regular, Typeface.SANS_SERIF),
        SANS_MEDIUM(R.font.inter_medium, Typeface.SANS_SERIF),
        SANS_SEMIBOLD(R.font.inter_semibold, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)),
    }

    /** Written by the prewarm thread, read on the main thread. */
    private val cache = AtomicReferenceArray<Typeface>(Face.entries.size)

    fun get(context: Context, face: Face): Typeface {
        cache.get(face.ordinal)?.let { return it }
        val loaded = runCatching { context.applicationContext.resources.getFont(face.res) }.getOrNull() ?: face.fallback
        cache.compareAndSet(face.ordinal, null, loaded)
        return cache.get(face.ordinal)
    }

    /** Loads every face on a short-lived background thread. Safe to call more than once. */
    fun prewarm(context: Context) {
        if ((0 until cache.length()).all { cache.get(it) != null }) return
        val app = context.applicationContext ?: return
        Thread({ Face.entries.forEach { get(app, it) } }, "physiboard-fonts").apply { isDaemon = true }.start()
    }
}

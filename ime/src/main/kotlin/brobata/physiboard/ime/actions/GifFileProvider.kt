package brobata.physiboard.ime.actions

import androidx.core.content.FileProvider

/**
 * The GIF page's own content provider (layers-sym-alt.md SS4.5): a subclass only so it can be
 * declared beside the app's FileProvider without the manifest merger folding the two together.
 * It exposes the cache path `gif-share/` and nothing else.
 */
class GifFileProvider : FileProvider()

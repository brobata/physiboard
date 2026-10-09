package brobata.physiboard.app.settings.ui

import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordSource
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.settings.CustomSymPage
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SymPagesConfig

/**
 * Undo instead of confirm (app-shell.md SS22.4): the action happens at once, and for
 * [UndoSlot.WINDOW_MS] a snackbar offers to put back exactly what was there before. Pure rules,
 * so the restoring is tested without a screen.
 */
class UndoSlot<R>(private val windowMs: Long = WINDOW_MS) {

    /** The one action that can still be undone: what to show, and how to put the prior state back. */
    data class Pending<R>(val key: String, val message: String, val restore: R, val offeredAtMs: Long)

    var pending: Pending<R>? = null
        private set

    /**
     * Offers a new undo. A second action with the same [key] inside the window (three arrow taps
     * moving one Sym page) keeps the FIRST action's [restore], so Undo returns to where the burst
     * started rather than one step back; any other action replaces the pending one, which can no
     * longer be undone.
     */
    fun offer(key: String, message: String, restore: R, nowMs: Long): Pending<R> {
        val current = pending
        val next = if (current != null && current.key == key && nowMs - current.offeredAtMs < windowMs) {
            current.copy(message = message, offeredAtMs = nowMs)
        } else {
            Pending(key, message, restore, nowMs)
        }
        pending = next
        return next
    }

    /** The restore to run when Undo is tapped, once; null once the window has passed or it was already taken. */
    fun take(nowMs: Long): R? {
        val current = pending ?: return null
        pending = null
        return if (nowMs - current.offeredAtMs < windowMs) current.restore else null
    }

    /** The snackbar went away without Undo: the action stands. */
    fun dismiss(offeredAtMs: Long) {
        if (pending?.offeredAtMs == offeredAtMs) pending = null
    }

    companion object {
        const val WINDOW_MS: Long = 8_000
    }
}

/**
 * One section of [Settings] that an undoable action changes. Undo writes back only this section,
 * as it was before the action, so a change made elsewhere while the snackbar was up survives.
 */
class SettingsSection<T>(val get: (Settings) -> T, val set: (Settings, T) -> Settings) {

    /** The transform that puts this section back to how [before] had it. */
    fun restoreFrom(before: Settings): (Settings) -> Settings {
        val prior = get(before)
        return { current -> set(current, prior) }
    }

    companion object {
        /** Punctuation spacing's two lists (its reset). */
        val PUNCTUATION_SPACING = SettingsSection(
            get = { it.typing.removeSpaceBefore to it.typing.spaceBeforeNextText },
            set = { s, v -> s.copy(typing = s.typing.copy(removeSpaceBefore = v.first, spaceBeforeNextText = v.second)) },
        )

        /** The Fn layer screen's three switches (its "Reset these switches"). */
        val FN_LAYER_SWITCHES = SettingsSection(
            get = { Triple(it.keys.navModeEnabled, it.keys.navModeCtrlHoldEnabled, it.keys.layoutAwareCtrlShortcuts) },
            set = { s, v -> s.copy(keys = s.keys.copy(navModeEnabled = v.first, navModeCtrlHoldEnabled = v.second, layoutAwareCtrlShortcuts = v.third)) },
        )

        /** Every letter's accent list (Customize Variations' "Reset every letter"). */
        val ALL_VARIATIONS = SettingsSection(
            get = { it.keys.customVariations },
            set = { s, v -> s.copy(keys = s.keys.copy(customVariations = v)) },
        )

        /** One character's accent list; absent means the language's own. */
        fun variationsFor(character: Char) = SettingsSection(
            get = { it.keys.customVariations[character.toString()] },
            set = { s, v ->
                val key = character.toString()
                val map = s.keys.customVariations
                s.copy(keys = s.keys.copy(customVariations = if (v == null) map - key else map + (key to v)))
            },
        )

        /** Which Sym pages are on, and their order. */
        val SYM_PAGE_ORDER = SettingsSection<SymPagesConfig>(
            get = { it.symPages.pages },
            set = { s, v -> s.copy(symPages = s.symPages.copy(pages = v)) },
        )

        /** My page [index]'s keys (its name is not touched by Clear page). */
        fun customPageKeys(index: Int) = SettingsSection(
            get = { it.symPages.customPages.getOrNull(index)?.mappings ?: emptyMap() },
            set = { s, v ->
                val pages = s.symPages.customPages.toMutableList()
                while (pages.size <= index) pages += CustomSymPage()
                pages[index] = pages[index].copy(mappings = v)
                s.copy(symPages = s.symPages.copy(customPages = pages))
            },
        )

        val EMOJI_LAYER = SettingsSection(
            get = { it.symPages.customEmojiPage },
            set = { s, v -> s.copy(symPages = s.symPages.copy(customEmojiPage = v)) },
        )

        val SYMBOLS_LAYER = SettingsSection(
            get = { it.symPages.customSymbolsPage },
            set = { s, v -> s.copy(symPages = s.symPages.copy(customSymbolsPage = v)) },
        )
    }
}

/** Putting a deleted personal-dictionary word back exactly: its spelling, count and last use, and a default word in its old place. */
object DictionaryUndo {
    /** [current] with [removed] back in it; a word the user has since re-added keeps the newer entry. */
    fun restorePersonal(current: UserWordStore, removed: PersonalWord): UserWordStore {
        if (current.sourceOf(removed.word) == WordSource.PERSONAL) return current
        return UserWordStore.of(current.defaultWords(), current.personalWords() + removed)
    }

    /** [currentDefaults] with [removed] back at [index] (clamped), unless it is already there. */
    fun restoreDefault(currentDefaults: List<WordFrequency>, removed: WordFrequency, index: Int): List<WordFrequency> {
        if (currentDefaults.any { it.word == removed.word }) return currentDefaults
        val at = index.coerceIn(0, currentDefaults.size)
        return currentDefaults.subList(0, at) + removed + currentDefaults.subList(at, currentDefaults.size)
    }
}

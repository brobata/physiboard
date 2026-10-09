package brobata.physiboard.app.settings.ui.screens

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import brobata.physiboard.app.settings.ui.TerminalSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import brobata.physiboard.app.settings.ui.AboutExpander
import brobata.physiboard.app.settings.ui.InnerPages
import brobata.physiboard.app.settings.ui.LocalUndo
import brobata.physiboard.app.settings.ui.SettingsSection
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.EmojiPickerDialog
import brobata.physiboard.app.settings.ui.LocalSettingsController
import androidx.compose.material.icons.outlined.ContentPaste
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.Summaries
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.app.settings.ui.TextFieldRow
import brobata.physiboard.app.settings.ui.UnicodeCharacterDialog
import brobata.physiboard.core.actions.emoji.SkinTone
import brobata.physiboard.core.actions.feedback.HapticEvent
import brobata.physiboard.core.actions.emoji.SkinTones
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.settings.CustomSymPage
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SymPage
import brobata.physiboard.core.settings.SymPagesConfig
import brobata.physiboard.device.titan.TitanLayouts
import brobata.physiboard.ime.fill.OneTimeCodeListenerService

/**
 * "Customize SYM Keyboard" (layers-sym-alt.md SS5.9): reorder and enable the Sym pages, the "Alt
 * character layer" dead-end row (SS5.9's own note: "leads nowhere useful" since 2.0), the SYM
 * behaviour switches, and the per-page editors reached by a pencil (SS5.9's last paragraph) or
 * directly when the keyboard opens this screen for a picker (SS5.8's intent extras, [initialPage],
 * [initialKeyCode], [openPickerImmediately], [returnAfterPicker]).
 *
 * The Device page (5) does not exist in 3.0 ([SymPage]'s own KDoc: dropped), so this screen's
 * "Sym pages" list has no Device row, pencil or "under construction" badge; it has the
 * GIF page (SS4.5) instead, on by default and labelled as the one page that goes online, and
 * the user's own three pages (SS4.6), each with a pencil that opens its name and grid.
 */
@Composable
fun CustomizeSymKeyboardScreen(
    initialPage: Int = 0,
    initialKeyCode: Int = -1,
    openPickerImmediately: Boolean = false,
    returnAfterPicker: Boolean = false,
    onBack: () -> Unit,
    onFinishActivity: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val controller = LocalSettingsController.current
    val symPages = controller.current.value.symPages
    val clipboardHistoryOn = controller.current.value.expansion.clipboardHistoryEnabled
    val keys = controller.current.value.keys
    val context = LocalContext.current

    // layers-sym-alt.md SS4.7: notification access changes behind the screen; re-read on every resume.
    var notificationAccess by remember { mutableStateOf(oneTimeCodeAccessGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationAccess = oneTimeCodeAccessGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // app-shell.md SS22.5: which page is open survives a trip away and the process being reclaimed.
    var editingPage by rememberSaveable { mutableStateOf(symPageForNumber(initialPage)) }
    val undo = LocalUndo.current
    var pickerLetter by remember { mutableStateOf<Char?>(null) }
    var pendingReturn by remember { mutableStateOf(false) }

    /**
     * spec: layers-sym-alt.md SS5.8: "The customisation screen converts [pending_restore_sym_page]
     * into restore_sym_page when it finishes normally (back arrow, system back, or the auto-return
     * after a direct picker edit)." Getting destroyed instead (the user switched to another app)
     * never calls this, so the pending value is simply left stranded and nothing is restored,
     * exactly as the spec asks, with no extra bookkeeping needed for that case.
     */
    fun leaveNormally(finish: () -> Unit) {
        val pending = controller.current.value.symPages.pendingRestoreSymPage
        if (pending > 0) controller.update { it.copy(symPages = it.symPages.copy(restoreSymPage = pending)) }
        finish()
    }

    // spec SS5.8: with OPEN_SYM_PICKER and RETURN_AFTER_PICKER both true and a key code present,
    // "the picker opens immediately and the screen finishes as soon as the picker closes".
    LaunchedEffect(Unit) {
        if (openPickerImmediately && initialKeyCode >= 0) {
            letterForKeyCode(initialKeyCode)?.let { letter ->
                pickerLetter = letter
                pendingReturn = returnAfterPicker
            }
        }
    }

    // app-shell.md SS22.2: the page editor is an inner page, pushed and popped like a screen,
    // with predictive back; each side keeps its own scroll while the other shows.
    InnerPages(
        detail = editingPage,
        onCloseDetail = { editingPage = null },
        detailKey = { it.name },
        list = {
            SettingsScreenScaffold(title = "Sym pages", onBack = { leaveNormally(onBack) }) {
                RowList {
                    header("Pages")
                    item {
                        // layers-sym-alt.md SS4.7: Fill joins the cycle only when it has something, so it is not a fixed step.
                        val steps = symPages.pages.order.filter { it != SymPage.FILL && enabledFor(symPages.pages, it) }.map { displayName(it, symPages.customPages) }
                        Text(
                            "Sym: " + (steps + "closed").joinToString(" → "),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m),
                        )
                        AboutExpander(
                            title = "About the Sym pages",
                            text = "Each Sym press opens the next page that is switched on, in this order; after the last one Sym closes. Use the arrows to reorder and the switch to add or remove a page. A page that is off still opens from the chooser (Sym twice, then its letter). My page 1 to 3 are your own key layers: tap ✏ to fill one, then switch it on.",
                        )
                    }
                    // Keyed by page, so a moved page glides to its new place (app-shell.md SS22.2).
                items(symPages.pages.order, key = { it.name }) { entry ->
                    val index = symPages.pages.order.indexOf(entry)
                    val enabledEntry = enabledFor(symPages.pages, entry)
                    val name = displayName(entry, symPages.customPages)
                    // app-shell.md SS22.4: a move or a page switched off happens at once, and Undo
                    // puts the whole list back as it was before this run of changes.
                    fun changePages(message: String, feel: HapticEvent?, change: (SymPagesConfig) -> SymPagesConfig) {
                        val transform: (Settings) -> Settings = { it.copy(symPages = it.symPages.copy(pages = change(it.symPages.pages))) }
                        if (undo != null) undo.updateSettings(controller, "sym-pages", message, SettingsSection.SYM_PAGE_ORDER, feel, transform) else controller.update(transform)
                    }
                    SymPageOrderRow(
                        page = entry,
                        name = name,
                        position = if (enabledEntry && entry != SymPage.FILL) symPages.pages.order.take(index + 1).count { it != SymPage.FILL && enabledFor(symPages.pages, it) } else null,
                        enabled = enabledEntry,
                        canMoveUp = index > 0,
                        canMoveDown = index < symPages.pages.order.lastIndex,
                        onMoveUp = { changePages("Moved $name up", HapticEvent.REORDER) { p -> p.copy(order = p.order.moved(p.order.indexOf(entry), p.order.indexOf(entry) - 1)) } },
                        onMoveDown = { changePages("Moved $name down", HapticEvent.REORDER) { p -> p.copy(order = p.order.moved(p.order.indexOf(entry), p.order.indexOf(entry) + 1)) } },
                        onToggleEnabled = { checked ->
                            if (checked) {
                                controller.update { it.copy(symPages = it.symPages.copy(pages = withEnabled(it.symPages.pages, entry, true))) }
                            } else {
                                // The switch itself already gave the toggle's tick.
                                changePages("$name is off", feel = null) { p -> withEnabled(p, entry, false) }
                            }
                        },
                        onEdit = if (entry == SymPage.EMOJI || entry == SymPage.SYMBOLS || customIndex(entry) != null) ({ editingPage = entry }) else null,
                    )
                }
                header("Emoji")
                    item {
                        // expansion-clipboard-pickers-launcher.md SS4.3: kaomoji only on request.
                        SwitchRow(
                            label = "Kaomoji on the Emoji page",
                            description = "Adds a button on the Emoji page that switches to text faces like (^_^), and a K row in the chooser. Off: the Emoji page only ever shows emoji.",
                            checked = symPages.kaomojiEnabled,
                            onCheckedChange = { checked -> controller.update { it.copy(symPages = it.symPages.copy(kaomojiEnabled = checked)) } },
                        )
                    }
                    item {
                        SwitchRow(
                            label = "Larger emoji picker",
                            description = "About 1.5 times taller; the other pages keep their height.",
                            checked = symPages.emojiPickerExpandedHeight,
                            onCheckedChange = { checked -> controller.update { it.copy(symPages = it.symPages.copy(emojiPickerExpandedHeight = checked)) } },
                        )
                    }
                    item {
                        // spec: expansion-clipboard-pickers-launcher.md SS4.7.
                        SingleChoiceDropdownRow(
                            label = "Default skin tone",
                            description = "Emoji that come in skin tones are typed in this one, from the Emoji page, Sym chords, the emoji picker and its recents. Hold an emoji to pick another tone.",
                            options = SkinTone.entries,
                            optionLabel = ::skinToneLabel,
                            selected = symPages.defaultSkinTone,
                            onSelect = { tone -> controller.update { it.copy(symPages = it.symPages.copy(defaultSkinTone = tone)) } },
                        )
                    }
                    header("Fill page")
                    item {
                        // layers-sym-alt.md SS4.7, app-shell.md SS31.6.
                        SwitchRow(
                            label = "One-time codes from notifications",
                            description = "A sign-in code from a text, e-mail or bank app waits on the Fill page for 10 minutes.",
                            note = if (symPages.otpFromNotifications && !notificationAccess) "Needs notification access (below) before it does anything." else null,
                            checked = symPages.otpFromNotifications,
                            onCheckedChange = { checked -> controller.update { it.copy(symPages = it.symPages.copy(otpFromNotifications = checked)) } },
                        )
                        AboutExpander(
                            title = "About one-time codes",
                            text = "When a sign-in code arrives by text message, e-mail or a banking app, the Fill page offers it for 10 minutes: press Sym in the code box and then the key shown beside the code. " +
                                "PhysiBoard reads each notification's text on the phone to find the code, keeps only the code, in memory, and forgets it after 10 minutes, when it is typed, or when the screen turns off. Nothing is saved, logged or sent anywhere. Not while private mode is on.",
                        )
                    }
                    item {
                        ButtonRow(
                            label = "Notification access",
                            description = if (notificationAccess) {
                                "Allowed for \"PhysiBoard one-time codes\". Turn it off in Android's settings at any time; the codes go with it."
                            } else {
                                "Android asks you to allow \"PhysiBoard one-time codes\" to read notifications. That is how it sees a code arrive. It is separate from the notification ring's access."
                            },
                            buttonText = if (notificationAccess) "Open" else "Allow",
                            onClick = { openNotificationAccess(context) },
                        )
                    }
                    item {
                        SwitchRow(
                            label = "Password manager suggestions (experimental)",
                            description = "Your password manager's saved logins on the Fill page. While on, its own drop-down list does not appear.",
                            checked = symPages.inlineSuggestions,
                            onCheckedChange = { checked -> controller.update { it.copy(symPages = it.symPages.copy(inlineSuggestions = checked)) } },
                        )
                        AboutExpander(
                            title = "Why it is off by default",
                            text = "The logins show first when you press Sym in a login box. Android only hands these to a keyboard that shows an on-screen keyboard, so PhysiBoard has to raise an empty one while you are in a login box, and while this is on the password manager's own drop-down list does not appear.",
                        )
                    }
                    header("Sym key")
                    item {
                        // spec SS5.10: the chooser that opens any page, enabled or not.
                        SwitchRow(
                            label = "Double-tap Sym for the page chooser",
                            description = "Two quick Sym taps show every page with a letter; press the letter to open it, even a page that is switched off above. Off: two quick taps step two pages.",
                            checked = symPages.doubleTapChooser,
                            onCheckedChange = { checked -> controller.update { it.copy(symPages = it.symPages.copy(doubleTapChooser = checked)) } },
                        )
                    }
                    item {
                        SwitchRow(
                            label = "Sym+C/V/X/A: copy, paste, cut, select all",
                            checked = keys.symEditShortcuts,
                            onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(symEditShortcuts = checked)) } },
                        )
                    }
                    item {
                        SwitchRow(
                            label = "Close Sym after typing a character",
                            checked = symPages.autoClose,
                            onCheckedChange = { checked -> controller.update { it.copy(symPages = it.symPages.copy(autoClose = checked)) } },
                        )
                    }
                    item {
                        SwitchRow(
                            label = "Also after tapping a key on screen",
                            checked = symPages.autoCloseOnTouch,
                            enabled = symPages.autoClose,
                            onCheckedChange = { checked -> controller.update { it.copy(symPages = it.symPages.copy(autoCloseOnTouch = checked)) } },
                        )
                    }
                    header("Clipboard")
                    item {
                        NavigateRow(
                            "Clipboard history",
                            "Keep what you copy on the Clipboard page",
                            icon = Icons.Outlined.ContentPaste,
                            value = if (clipboardHistoryOn) "On" else "Off",
                        ) { onNavigate(Routes.CLIPBOARD_HISTORY) }
                    }
                }
            }
        },
        detailContent = { page ->
            if (customIndex(page) != null) {
                // spec SS4.6: one of the user's own pages: a name, the grid, and a way to empty it.
                val index = customIndex(page)!!
                val custom = symPages.customPages.getOrElse(index) { CustomSymPage() }
                // Typed into local state: the store trims a name on the way back, which would eat a
                // space typed between two words, and its round trip lags fast typing.
                var nameText by remember(index) { mutableStateOf(custom.name) }
                SettingsScreenScaffold(title = "Edit ${displayName(page, symPages.customPages)}", onBack = { editingPage = null }) {
                    RowList {
                        item {
                            TextFieldRow(
                                label = "Page name",
                                description = "Shown in the page chooser (Sym, Sym, then ${chooserLetter(page)}). Leave empty for \"${defaultCustomName(index)}\".",
                                value = nameText,
                                onValueChange = { typed ->
                                    val name = typed.take(CustomSymPage.MAX_NAME_LENGTH)
                                    nameText = name
                                    controller.update { it.copy(symPages = it.symPages.copy(customPages = it.symPages.customPages.withPage(index) { p -> p.copy(name = name) })) }
                                },
                            )
                        }
                        item {
                            Text(
                                "Tap a key to choose what it types on this page: any character, symbol, emoji or short text. Turn the page on in the list before this one to reach it with Sym.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        item {
                            SymEditGrid(
                                characters = ('A'..'Z').associateWith { letter -> custom.mappings["KEYCODE_$letter"].orEmpty() },
                                onKeyTapped = { letter -> pickerLetter = letter },
                            )
                        }
                        item {
                            // app-shell.md SS22.4: cleared at once; the snackbar's Undo puts every key back.
                            TextButton(
                                onClick = {
                                    val pageName = displayName(page, symPages.customPages)
                                    val transform: (Settings) -> Settings = { it.copy(symPages = it.symPages.copy(customPages = it.symPages.customPages.withPage(index) { p -> p.copy(mappings = emptyMap()) })) }
                                    if (undo != null) undo.updateSettings(controller, "sym-clear-$index", "Cleared $pageName", SettingsSection.customPageKeys(index), transform = transform) else controller.update(transform)
                                },
                                enabled = custom.mappings.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            ) { Text("Clear page", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            } else {
                val isEmoji = page == SymPage.EMOJI
                SettingsScreenScaffold(title = if (isEmoji) "Edit Emoji Layer" else "Edit Symbols Layer", onBack = { editingPage = null }) {
                    RowList {
                        item {
                            SymEditGrid(
                                characters = effectiveCharacters(isEmoji, symPages.customEmojiPage, symPages.customSymbolsPage),
                                onKeyTapped = { letter -> pickerLetter = letter },
                            )
                        }
                        item {
                            TextButton(
                                onClick = {
                                    val section = if (isEmoji) SettingsSection.EMOJI_LAYER else SettingsSection.SYMBOLS_LAYER
                                    val transform: (Settings) -> Settings = {
                                        if (isEmoji) it.copy(symPages = it.symPages.copy(customEmojiPage = emptyMap()))
                                        else it.copy(symPages = it.symPages.copy(customSymbolsPage = emptyMap()))
                                    }
                                    val message = if (isEmoji) "Emoji layer reset" else "Symbols layer reset"
                                    if (undo != null) undo.updateSettings(controller, "sym-reset-${page.name}", message, section, transform = transform) else controller.update(transform)
                                },
                                enabled = (if (isEmoji) symPages.customEmojiPage else symPages.customSymbolsPage).isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            ) { Text("Reset to Default", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        },
    )

    pickerLetter?.let { letter ->
        fun close() {
            pickerLetter = null
            if (pendingReturn) {
                pendingReturn = false
                leaveNormally(onFinishActivity)
            }
        }
        val isEmoji = editingPage == SymPage.EMOJI
        val customPage = editingPage?.let(::customIndex)
        if (customPage != null) {
            // spec SS4.6: any text at all; the empty choice clears the key.
            UnicodeCharacterDialog(
                letter = letter,
                resetLabel = "Clear this key",
                onDismiss = { close() },
                onChoose = { chosen ->
                    controller.update {
                        val pages = it.symPages.customPages.withPage(customPage) { p ->
                            p.copy(mappings = if (chosen.isEmpty()) p.mappings - "KEYCODE_$letter" else p.mappings + ("KEYCODE_$letter" to chosen))
                        }
                        it.copy(symPages = it.symPages.copy(customPages = pages))
                    }
                    close()
                },
            )
        } else if (isEmoji) {
            EmojiPickerDialog(
                letter = letter,
                onDismiss = { close() },
                onChoose = { chosen ->
                    controller.update { it.copy(symPages = it.symPages.copy(customEmojiPage = it.symPages.customEmojiPage + ("KEYCODE_$letter" to chosen))) }
                    close()
                },
            )
        } else {
            UnicodeCharacterDialog(
                letter = letter,
                onDismiss = { close() },
                onChoose = { chosen ->
                    controller.update {
                        val updated = if (chosen.isEmpty()) it.symPages.customSymbolsPage - "KEYCODE_$letter" else it.symPages.customSymbolsPage + ("KEYCODE_$letter" to chosen)
                        it.copy(symPages = it.symPages.copy(customSymbolsPage = updated))
                    }
                    close()
                },
            )
        }
    }
}

private fun symPageForNumber(page: Int): SymPage? = when (page) {
    1 -> SymPage.EMOJI
    2 -> SymPage.SYMBOLS
    // spec SS4.6: the pencil and a long press on one of the user's own pages (7 to 9).
    7 -> SymPage.CUSTOM_1
    8 -> SymPage.CUSTOM_2
    9 -> SymPage.CUSTOM_3
    else -> null
}

/** spec SS4.6: which of the user's own pages [page] is (0 to 2), or null for a shipped page. */
private fun customIndex(page: SymPage): Int? = when (page) {
    SymPage.CUSTOM_1 -> 0
    SymPage.CUSTOM_2 -> 1
    SymPage.CUSTOM_3 -> 2
    else -> null
}

private fun defaultCustomName(index: Int): String = "My page ${index + 1}"

/** spec SS5.10: the chooser letter of one of the user's own pages. */
private fun chooserLetter(page: SymPage): Char = when (page) {
    SymPage.CUSTOM_1 -> 'M'
    SymPage.CUSTOM_2 -> 'N'
    else -> 'B'
}

private fun List<CustomSymPage>.withPage(index: Int, change: (CustomSymPage) -> CustomSymPage): List<CustomSymPage> =
    List(CustomSymPage.COUNT) { i -> getOrElse(i) { CustomSymPage() }.let { if (i == index) change(it) else it } }

/** Whether "PhysiBoard one-time codes" has notification access (layers-sym-alt.md SS4.7). */
internal fun oneTimeCodeAccessGranted(context: Context): Boolean = runCatching {
    context.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(ComponentName(context, OneTimeCodeListenerService::class.java)) ?: false
}.getOrDefault(false)

/**
 * Android's own page for this one listener (Android 11 and later), else the list of every app
 * with notification access. app-shell.md SS31.6.
 */
internal fun openNotificationAccess(context: Context) {
    val component = ComponentName(context, OneTimeCodeListenerService::class.java).flattenToString()
    val detail = Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(detail) }.isSuccess
    if (!opened) runCatching { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** spec SS5.8's `INITIAL_SYM_KEY_CODE`: an Android `KeyEvent.KEYCODE_A`..`KEYCODE_Z` value (29..54). */
private fun letterForKeyCode(keyCode: Int): Char? {
    val a = android.view.KeyEvent.KEYCODE_A
    val z = android.view.KeyEvent.KEYCODE_Z
    if (keyCode !in a..z) return null
    return 'A' + (keyCode - a)
}

private fun enabledFor(pages: SymPagesConfig, page: SymPage): Boolean = Summaries.isEnabled(pages, page)

private fun withEnabled(pages: SymPagesConfig, page: SymPage, checked: Boolean): SymPagesConfig = when (page) {
    SymPage.EMOJI -> pages.copy(emojiEnabled = checked)
    SymPage.SYMBOLS -> pages.copy(symbolsEnabled = checked)
    SymPage.CLIPBOARD -> pages.copy(clipboardEnabled = checked)
    SymPage.EMOJI_PICKER -> pages.copy(emojiPickerEnabled = checked)
    SymPage.GIF -> pages.copy(gifEnabled = checked)
    SymPage.CUSTOM_1 -> pages.copy(custom1Enabled = checked)
    SymPage.CUSTOM_2 -> pages.copy(custom2Enabled = checked)
    SymPage.CUSTOM_3 -> pages.copy(custom3Enabled = checked)
    SymPage.FILL -> pages.copy(fillEnabled = checked)
}

private fun displayName(page: SymPage, customPages: List<CustomSymPage>): String = Summaries.symPageName(page, customPages)

/**
 * spec SS5.9: "a kind label ('Key layer' or 'Panel')": pages 1 and 2 remap the letter keys, the
 * rest are content panels. The GIF page says it goes online (SS4.5), since it is the only one.
 */
private fun kindLabel(page: SymPage): String = when (page) {
    SymPage.EMOJI_PICKER -> "Every emoji, with search and recents · chooser letter P"
    SymPage.SYMBOLS -> "A symbol on each letter key, 🔍 for every symbol · chooser letter S"
    SymPage.GIF -> "GIF search, online only while the page is open · chooser letter G"
    SymPage.CLIPBOARD -> "Your recent copies · chooser letter C"
    SymPage.EMOJI -> "An emoji on each letter key · chooser letter E"
    SymPage.CUSTOM_1, SymPage.CUSTOM_2, SymPage.CUSTOM_3 -> "Key layer · your own · chooser letter ${chooserLetter(page)}"
    SymPage.FILL -> "One-time codes and saved logins; joins only when it has one, first in a code or login box · chooser letter F"
}

@Composable
private fun SymPageOrderRow(
    page: SymPage,
    name: String,
    /** Where Sym reaches this page (1 for the first press), or null while it is off. */
    position: Int?,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(if (position != null) "$position. $name" else name, style = MaterialTheme.typography.bodyLarge)
            Text(kindLabel(page), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) { Icon(Icons.Outlined.Edit, contentDescription = "Edit $name", tint = MaterialTheme.colorScheme.primary) }
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
        }
        TerminalSwitch(checked = enabled, onCheckedChange = onToggleEnabled)
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (to < 0 || to >= size || from == to) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}

/**
 * spec layers-sym-alt.md SS4.4: "it replaces the shipped page entirely (keys absent from the
 * custom map have no character on that page)" once any custom entry exists; otherwise the shipped
 * Titan 2 Elite table (`:device:titan`'s `TitanLayouts`) shows.
 */
internal fun effectiveCharacters(isEmoji: Boolean, customEmoji: Map<String, String>, customSymbols: Map<String, String>): Map<Char, String> {
    val custom = if (isEmoji) customEmoji else customSymbols
    if (custom.isNotEmpty()) {
        return ('A'..'Z').associateWith { letter -> custom["KEYCODE_$letter"].orEmpty() }
    }
    val shipped = TitanLayouts.titan2EliteQwerty()
    val map = if (isEmoji) shipped.emojiPage else shipped.symbolsPage
    return ('A'..'Z').associateWith { letter -> map[KeyId.Letter(letter)]?.lowercase.orEmpty() }
}

private val EDIT_GRID_ROWS: List<String> = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

/** spec SS5.9: "the same geometry and Titan alignment as the live grid on a black background." A simplified Compose approximation, as [FnLayerKeyGrid] already is for the Fn Layer editor. */
@Composable
private fun SymEditGrid(characters: Map<Char, String>, onKeyTapped: (Char) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 8.dp)) {
        EDIT_GRID_ROWS.forEach { row ->
            // Each key takes a tenth of the width, so the grid fills the screen as the keyboard does.
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                row.forEach { letter ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(1.dp)
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onKeyTapped(letter) },
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text(letter.toString(), style = MaterialTheme.typography.labelSmall)
                            Text(characters[letter].orEmpty(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                val missing = EDIT_GRID_ROWS.first().length - row.length
                if (missing > 0) Spacer(modifier = Modifier.weight(missing.toFloat()))
            }
        }
    }
}

/** The tone's name with the waving hand in it, so the row shows what it sets. */
private fun skinToneLabel(tone: SkinTone): String =
    if (tone == SkinTone.NONE) "No tone (👋)" else "${tone.label} (${SkinTones.apply("👋", tone)})"

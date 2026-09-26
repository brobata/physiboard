package brobata.physiboard.app.settings.ui

import brobata.physiboard.core.settings.EnterOverrideRow
import brobata.physiboard.core.text.EnterOverride

/**
 * [EnterOverrideRow] (`:core:settings`'s stored shape) and [EnterOverride] (`:core:text`'s pure
 * resolver input) carry the same four fields under the same names; `:core:text` cannot depend on
 * `:core:settings` (the dependency runs the other way), so `:core:text`'s Enter logic
 * ([brobata.physiboard.core.text.EnterPresetApplication], [brobata.physiboard.core.text.EnterOverrideResolver])
 * works in terms of [EnterOverride] and the settings screens convert at the edge.
 */
fun EnterOverrideRow.toEnterOverride(): EnterOverride = EnterOverride(packageName, behavior, sendMethod, extraSendShortcut)

fun EnterOverride.toEnterOverrideRow(): EnterOverrideRow = EnterOverrideRow(packageName, behavior, sendMethod, extraSendShortcut)

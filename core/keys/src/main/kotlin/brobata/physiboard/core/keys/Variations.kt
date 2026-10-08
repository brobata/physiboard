package brobata.physiboard.core.keys

/**
 * The accents and related letters a long press in Accent mode reaches. spec: layers-sym-alt.md
 * SS8 (3.0): one built-in table written for PhysiBoard 3.0 (no list is copied from another
 * keyboard), ordered for the keyboard's language when that language has letters of its own, and
 * the user's own lists from "Customize Variations" laid over it one character at a time.
 */
object Variations {

    /** spec SS8.3: a list holds at most ten entries, one for each digit printed on the Titan's keys (the chooser's pick keys). */
    const val MAX_PER_CHARACTER: Int = 10

    /** spec SS8.3: an entry is any short text, a letter, a symbol, a word or a character from another script. */
    const val MAX_ENTRY_LENGTH: Int = 16

    /**
     * The lowercase lists, at most [MAX_PER_CHARACTER] each. Accented Latin letters come first in
     * a fixed order (grave, acute, circumflex, diaeresis, tilde, ring, macron, ogonek, breve and
     * the rest), then the related letters (ø, œ, æ, ł, đ, ß, þ, ı) and last a currency sign that
     * sits on that letter's name (€ on e, $ on s, £ on l, ¥ on y, ¢ on c). Each list carries every
     * letter any language in [LANGUAGE_FIRST] puts first, so reordering never drops one.
     */
    private val LATIN: Map<Char, String> = linkedMapOf(
        'a' to "àáâäãåāąăæ",
        'c' to "çćč¢",
        'd' to "ďđ",
        'e' to "èéêëēęěė€",
        'g' to "ğģ",
        'i' to "ìíîïīįı",
        'l' to "łľĺļ£",
        'n' to "ñńňņ",
        'o' to "òóôöõøōőœơ",
        'r' to "řŕ",
        's' to "ßśšşș$",
        't' to "ťțţþ",
        'u' to "ùúûüūůűųư",
        'y' to "ýÿ¥",
        'z' to "źžż",
    )

    /**
     * Characters outside the Latin letters with a variation of their own, both cases spelled out
     * (the Cyrillic and Armenian layouts): the rouble on Р and the dram on Դ, as before.
     */
    private val OTHER_SCRIPTS: Map<Char, List<String>> = mapOf(
        'е' to listOf("ё", "є"), 'Е' to listOf("Ё", "Є"), // Cyrillic ie
        'г' to listOf("ґ"), 'Г' to listOf("Ґ"), // Cyrillic ghe
        'і' to listOf("ї"), 'І' to listOf("Ї"), // Ukrainian i
        'Р' to listOf("₽"), // Cyrillic er
        'Դ' to listOf("֏"), // Armenian da
    )

    /**
     * For each language with letters of its own, those letters per base letter, in the order the
     * language uses them most. They move to the front of the base letter's list when the keyboard
     * types in that language; every other entry keeps its place behind them.
     */
    private val LANGUAGE_FIRST: Map<String, Map<Char, String>> = mapOf(
        "pl" to mapOf('a' to "ą", 'c' to "ć", 'e' to "ę", 'l' to "ł", 'n' to "ń", 'o' to "ó", 's' to "ś", 'z' to "źż"),
        "fr" to mapOf('e' to "éèêë", 'a' to "àâæ", 'c' to "ç", 'i' to "îï", 'o' to "ôœ", 'u' to "ùûü", 'y' to "ÿ"),
        "de" to mapOf('a' to "ä", 'o' to "ö", 'u' to "ü", 's' to "ß"),
        "es" to mapOf('a' to "á", 'e' to "é", 'i' to "í", 'o' to "ó", 'u' to "úü", 'n' to "ñ"),
        "pt" to mapOf('a' to "ãáâà", 'o' to "õóô", 'e' to "éê", 'i' to "í", 'u' to "ú", 'c' to "ç"),
        "it" to mapOf('a' to "à", 'e' to "èé", 'i' to "ìí", 'o' to "òó", 'u' to "ùú"),
        "ca" to mapOf('a' to "à", 'e' to "èé", 'i' to "íï", 'o' to "òó", 'u' to "úü", 'c' to "ç"),
        "cs" to mapOf('a' to "á", 'c' to "č", 'd' to "ď", 'e' to "éě", 'i' to "í", 'n' to "ň", 'o' to "ó", 'r' to "ř", 's' to "š", 't' to "ť", 'u' to "úů", 'y' to "ý", 'z' to "ž"),
        "sk" to mapOf('a' to "áä", 'c' to "č", 'd' to "ď", 'e' to "é", 'i' to "í", 'l' to "ĺľ", 'n' to "ň", 'o' to "óô", 'r' to "ŕ", 's' to "š", 't' to "ť", 'u' to "ú", 'y' to "ý", 'z' to "ž"),
        "ro" to mapOf('a' to "ăâ", 'i' to "î", 's' to "ș", 't' to "ț"),
        "tr" to mapOf('c' to "ç", 'g' to "ğ", 'i' to "ı", 'o' to "ö", 's' to "ş", 'u' to "ü"),
        "nl" to mapOf('e' to "éëè", 'i' to "ï", 'o' to "óö", 'a' to "á", 'u' to "ü"),
        "sv" to mapOf('a' to "åä", 'o' to "ö", 'e' to "é"),
        "da" to mapOf('a' to "åæ", 'o' to "ø", 'e' to "é"),
        "no" to mapOf('a' to "åæ", 'o' to "øôòó", 'e' to "éèê"),
        "hu" to mapOf('a' to "á", 'e' to "é", 'i' to "í", 'o' to "óöő", 'u' to "úüű"),
        "gd" to mapOf('a' to "à", 'e' to "è", 'i' to "ì", 'o' to "ò", 'u' to "ù"),
        "vi" to mapOf('a' to "ăâ", 'd' to "đ", 'e' to "ê", 'o' to "ôơ", 'u' to "ư"),
    )

    /** The languages whose letters come first when the keyboard types in them, for the settings screen's preview. */
    val languagesWithOwnOrder: List<String> get() = LANGUAGE_FIRST.keys.sorted()

    /** The Latin base letters the built-in table covers, lowercase, a to z order. */
    val latinBases: List<Char> get() = LATIN.keys.sorted()

    /**
     * The language part of a subtype locale or language tag ("pl_PL", "pt-BR", "nb"), lowercase;
     * Norwegian Bokmål and Nynorsk share Norwegian's order. Blank for null or blank.
     */
    fun languageOf(localeOrTag: String?): String {
        val language = localeOrTag?.trim()?.substringBefore('_')?.substringBefore('-')?.lowercase().orEmpty()
        return when (language) {
            "nb", "nn" -> "no"
            else -> language
        }
    }

    /**
     * The built-in table for the keyboard language [localeOrTag] (a subtype locale such as
     * "pl_PL"); null or a language without letters of its own gives the neutral order.
     */
    fun defaults(localeOrTag: String?): Map<Char, List<String>> {
        val first = LANGUAGE_FIRST[languageOf(localeOrTag)].orEmpty()
        val table = LinkedHashMap<Char, List<String>>()
        for ((base, letters) in LATIN) {
            val ordered = ordered(letters, first[base].orEmpty())
            table[base] = ordered.map(Char::toString)
            table[base.uppercaseChar()] = ordered.map(::upper)
        }
        table.putAll(OTHER_SCRIPTS)
        return table
    }

    /**
     * spec SS8.2: the table a long press reads: [defaults] for [localeOrTag], with each character
     * the user customised replaced by the user's own list, exactly as saved (an empty list means
     * "no variations for this character"). Each list is cleaned by [clean].
     */
    fun effective(localeOrTag: String?, overrides: Map<Char, List<String>>): VariationTable {
        val table = defaults(localeOrTag).toMutableMap()
        for ((character, list) in overrides) table[character] = clean(list)
        return VariationTable(table.filterValues { it.isNotEmpty() })
    }

    /**
     * Turns the stored `custom_variations` keys (one character each) into characters; a key that
     * is not exactly one character is ignored.
     */
    fun overridesFromStored(stored: Map<String, List<String>>): Map<Char, List<String>> =
        stored.entries.mapNotNull { (key, list) -> key.singleOrNull()?.let { it to list } }.toMap()

    /**
     * spec SS8.3: blanks dropped, each entry cut to [MAX_ENTRY_LENGTH] characters, duplicates
     * removed keeping the first, at most [MAX_PER_CHARACTER] kept.
     */
    fun clean(list: List<String>): List<String> =
        list.asSequence()
            .filter { it.isNotBlank() }
            .map { if (it.length > MAX_ENTRY_LENGTH) it.take(MAX_ENTRY_LENGTH) else it }
            .distinct()
            .take(MAX_PER_CHARACTER)
            .toList()

    private fun ordered(letters: String, first: String): List<Char> {
        val front = first.filter { it in letters }.toList().distinct()
        return front + letters.filter { it !in front }.toList()
    }

    /**
     * The capital form a capital key offers: ß gives the capital sharp s, Turkish dotless ı gives
     * the dotted capital İ (the letter a capital I is missing in Turkish), a non-letter stays.
     */
    private fun upper(ch: Char): String = when {
        ch == 'ß' -> "ẞ"
        ch == 'ı' -> "İ"
        ch.isLetter() -> ch.uppercaseChar().toString()
        else -> ch.toString()
    }
}

/**
 * The accent chooser a long press in Accent mode opens when the character has more than one
 * variation. spec: layers-sym-alt.md SS8.4 (3.0). It lists every variation, each labelled with
 * the digit the Titan prints on a letter key (1 for the first, 2 for the second, ... 0 for the
 * tenth) and the letter that carries it. The pick keys are ordinary letters, so they pick only
 * while the long-pressed key is still held, or after Alt: once the key is up, a bare letter closes
 * the chooser and types as usual, so typing on after an accent never picks one by mistake.
 */
object VariationChooser {

    /** The chooser as it stands: the key that opened it, its choices, and the text the last choice left before the caret. */
    data class State(
        val heldKey: KeyId,
        val choices: List<String>,
        val committed: String,
        val heldKeyDown: Boolean = true,
        val altArmed: Boolean = false,
    )

    sealed class KeyOutcome {
        /** Replace [State.committed] with the choice at [index] and close; the key and its release are consumed. */
        data class Pick(val index: Int) : KeyOutcome()

        /** Close without a change; the key and its release are consumed (Back). */
        data object Dismiss : KeyOutcome()

        /** The held key's own auto-repeat: consumed, the chooser stays open. */
        data object Swallow : KeyOutcome()

        /** The long-pressed letter tapped again after its release: the next accent replaces the current one; the bar stays. */
        data object Cycle : KeyOutcome()

        /** Alt: consumed with its release, the chooser stays open and the next pick key picks. */
        data object ArmAlt : KeyOutcome()

        /** An auto-repeat of a key held from before (Shift, Fn): goes on as usual, the chooser stays open. */
        data object PassOnKeepOpen : KeyOutcome()

        /** Close, and let the key do what it would have done. */
        data object CloseAndPassOn : KeyOutcome()
    }

    /** Whether a long press that just typed the first of [choices] opens the chooser: only when there is something else to choose. */
    fun opens(choices: List<String>): Boolean = choices.size > 1

    /** The digit labelling the choice at [index]: 1 to 9, then 0 for the tenth. */
    fun digitForIndex(index: Int): Int = (index + 1) % 10

    /** The choice index a digit picks: 1 is the first, 0 the tenth. */
    fun indexForDigit(digit: Int): Int = (digit + 9) % 10

    /** The digit a key stands for: its device-layer (Alt) character when that is a digit, or its own digit. */
    fun digitFor(deviceLayerText: String?, ownDigit: Char?): Int? {
        val digit = deviceLayerText?.singleOrNull()?.takeIf { it in '0'..'9' } ?: ownDigit?.takeIf { it in '0'..'9' } ?: return null
        return digit - '0'
    }

    /**
     * spec SS8.4's key table, for one key down. [digit] is [digitFor]'s answer for this key;
     * [altHeld] is the Alt meta state the event carries.
     */
    fun onKeyDown(state: State, key: KeyId, repeatCount: Int, digit: Int?, altHeld: Boolean): KeyOutcome = when {
        repeatCount > 0 && key == state.heldKey -> KeyOutcome.Swallow
        repeatCount > 0 -> KeyOutcome.PassOnKeepOpen
        key == KeyId.Control(ControlKey.BACK) -> KeyOutcome.Dismiss
        key == KeyId.Modifier(ModifierKey.ALT) -> KeyOutcome.ArmAlt
        // The Titan reports a second key only after the held one is up (live, 2026-10-07), so the
        // natural way on: tap the same letter again for the next accent. Any other letter types.
        key == state.heldKey && !state.heldKeyDown && !(state.altArmed || altHeld) -> KeyOutcome.Cycle
        // The maintainer lets go of the letter and then presses the pick key (live trace,
        // 2026-10-07, twice): while the bar is open, the key labelled with a choice picks it,
        // held or not. The cost: typing straight on with a pick key while the bar is still up
        // picks rather than types (Polish "będę" typed fast), accepted for this.
        digit != null && indexForDigit(digit) in state.choices.indices ->
            KeyOutcome.Pick(indexForDigit(digit))
        else -> KeyOutcome.CloseAndPassOn
    }

    /** The choice after [State.committed], wrapping round to the first. */
    fun nextIndex(state: State): Int = (state.choices.indexOf(state.committed) + 1).mod(state.choices.size)

    /** A key came up: once the held key is up, bare pick keys stop picking. */
    fun onKeyUp(state: State, key: KeyId): State = if (key == state.heldKey) state.copy(heldKeyDown = false) else state

    /**
     * spec SS8.4: a pick replaces the text the long press (or the last pick) left only when the
     * text just before the caret still ends with it; otherwise nothing changes. [textBeforeCaret]
     * null means the field could not be read, which is trusted as unchanged, like a terminal's
     * text box ([terminalMode]), which the terminal empties after every key. An empty read is
     * trusted too: the accent was just typed, and a web field answers "" after every letter.
     */
    fun canReplace(textBeforeCaret: String?, committed: String, terminalMode: Boolean): Boolean =
        terminalMode || textBeforeCaret.isNullOrEmpty() || textBeforeCaret.endsWith(committed)
}

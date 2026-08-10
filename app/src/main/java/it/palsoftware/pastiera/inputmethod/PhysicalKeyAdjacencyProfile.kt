package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent

/** Stable physical key identity, independent of the character emitted by the keyboard firmware. */
@JvmInline
value class PhysicalKeyId(val value: String) {
    override fun toString(): String = value
}

/** Layout knowledge required by the proximity-based accidental-key filter. */
interface PhysicalKeyAdjacencyProfile {
    val profileId: String
    val numberRowKeys: Set<PhysicalKeyId>

    fun isAdjacent(first: PhysicalKeyId, second: PhysicalKeyId): Boolean

    fun resolveDirectKey(keyCode: Int, scanCode: Int): PhysicalKeyId?

    fun resolveNormalizedFirmwareOutput(keyCode: Int, metaState: Int): Set<PhysicalKeyId> =
        emptySet()
}

/** Physical keys and adjacency graph for the Clicks Power Keyboard. */
object ClicksPowerKeyboardLayout : PhysicalKeyAdjacencyProfile {
    override val profileId: String = "clicks_power"

    val DIGIT_1 = key("digit_1")
    val DIGIT_2 = key("digit_2")
    val DIGIT_3 = key("digit_3")
    val DIGIT_4 = key("digit_4")
    val DIGIT_5 = key("digit_5")
    val DIGIT_6 = key("digit_6")
    val DIGIT_7 = key("digit_7")
    val DIGIT_8 = key("digit_8")
    val DIGIT_9 = key("digit_9")
    val DIGIT_0 = key("digit_0")

    val Q = key("q")
    val W = key("w")
    val E = key("e")
    val R = key("r")
    val T = key("t")
    val Y = key("y")
    val U = key("u")
    val I = key("i")
    val O = key("o")
    val P = key("p")

    val A = key("a")
    val S = key("s")
    val D = key("d")
    val F = key("f")
    val G = key("g")
    val H = key("h")
    val J = key("j")
    val K = key("k")
    val L = key("l")
    val BACKSPACE = key("backspace")

    val SHIFT = key("shift")
    val Z = key("z")
    val X = key("x")
    val C = key("c")
    val V = key("v")
    val B = key("b")
    val N = key("n")
    val M = key("m")
    val PERIOD = key("period")
    val ENTER = key("enter")

    val SYM = key("sym")
    val CTRL = key("ctrl")
    val LAUNCHER = key("launcher")
    val SPACE = key("space")
    val RED_CLICKS = key("red_clicks")
    val KEYBOARD = key("keyboard")
    val MICROPHONE = key("microphone")

    private val numberRow = listOf(
        DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5,
        DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9, DIGIT_0
    )
    private val qwertyRow = listOf(Q, W, E, R, T, Y, U, I, O, P)
    private val homeRow = listOf(A, S, D, F, G, H, J, K, L, BACKSPACE)
    private val lowerRow = listOf(SHIFT, Z, X, C, V, B, N, M, PERIOD, ENTER)
    private val functionRow = listOf(SYM, CTRL, LAUNCHER, SPACE, RED_CLICKS, KEYBOARD, MICROPHONE)

    override val numberRowKeys: Set<PhysicalKeyId> = numberRow.toSet()

    private val adjacency: Map<PhysicalKeyId, Set<PhysicalKeyId>> =
        buildMap<PhysicalKeyId, MutableSet<PhysicalKeyId>> {
            fun connect(first: PhysicalKeyId, second: PhysicalKeyId) {
                getOrPut(first) { linkedSetOf() }.add(second)
                getOrPut(second) { linkedSetOf() }.add(first)
            }

            fun connectWithin(row: List<PhysicalKeyId>) {
                row.zipWithNext(::connect)
            }

            // Each lower circular-key row is staggered half a key to the right. Thus an upper
            // key touches the lower key at the same index and the one immediately to its left.
            fun connectStaggeredRows(upper: List<PhysicalKeyId>, lower: List<PhysicalKeyId>) {
                upper.forEachIndexed { index, upperKey ->
                    lower.getOrNull(index - 1)?.let { connect(upperKey, it) }
                    lower.getOrNull(index)?.let { connect(upperKey, it) }
                }
            }

            listOf(numberRow, qwertyRow, homeRow, lowerRow, functionRow).forEach(::connectWithin)
            connectStaggeredRows(numberRow, qwertyRow)
            connectStaggeredRows(qwertyRow, homeRow)
            connectStaggeredRows(homeRow, lowerRow)

            // The space bar spans four letter-key positions; the remaining bottom keys align
            // with the two circular keys directly above them in the photographed layout.
            connect(SHIFT, SYM)
            connect(Z, SYM)
            connect(Z, CTRL)
            connect(X, CTRL)
            connect(X, LAUNCHER)
            connect(C, LAUNCHER)
            listOf(C, V, B, N).forEach { connect(it, SPACE) }
            connect(N, RED_CLICKS)
            connect(M, RED_CLICKS)
            connect(M, KEYBOARD)
            connect(PERIOD, KEYBOARD)
            connect(PERIOD, MICROPHONE)
            connect(ENTER, MICROPHONE)
        }

    override fun isAdjacent(first: PhysicalKeyId, second: PhysicalKeyId): Boolean =
        second in adjacency[first].orEmpty()

    override fun resolveDirectKey(keyCode: Int, scanCode: Int): PhysicalKeyId? =
        SCAN_CODE_KEYS[scanCode] ?: KEY_CODE_KEYS[keyCode]

    override fun resolveNormalizedFirmwareOutput(
        keyCode: Int,
        metaState: Int
    ): Set<PhysicalKeyId> =
        NORMALIZED_SYM_OUTPUTS[OutputSignature(keyCode, broadModifiers(metaState))].orEmpty()

    private fun key(value: String) = PhysicalKeyId(value)

    private data class OutputSignature(val keyCode: Int, val modifiers: Int)

    private fun broadModifiers(metaState: Int): Int {
        val normalized = KeyEvent.normalizeMetaState(metaState)
        var result = 0
        if (normalized and KeyEvent.META_SHIFT_MASK != 0) result = result or KeyEvent.META_SHIFT_ON
        if (normalized and KeyEvent.META_CTRL_MASK != 0) result = result or KeyEvent.META_CTRL_ON
        if (normalized and KeyEvent.META_ALT_MASK != 0) result = result or KeyEvent.META_ALT_ON
        if (normalized and KeyEvent.META_META_MASK != 0) result = result or KeyEvent.META_META_ON
        if (normalized and KeyEvent.META_SYM_ON != 0) result = result or KeyEvent.META_SYM_ON
        return result
    }

    /** Legends whose current firmware output can be represented by Android KeyEvents. */
    private val NORMALIZED_SYM_OUTPUTS: Map<OutputSignature, Set<PhysicalKeyId>> = buildMap {
        fun add(keyCode: Int, modifiers: Int, physicalKey: PhysicalKeyId) {
            val signature = OutputSignature(keyCode, broadModifiers(modifiers))
            put(signature, get(signature).orEmpty() + physicalKey)
        }

        add(KeyEvent.KEYCODE_3, KeyEvent.META_SHIFT_ON, W) // #
        add(KeyEvent.KEYCODE_GRAVE, KeyEvent.META_SHIFT_ON, E) // ~
        add(KeyEvent.KEYCODE_EQUALS, 0, R) // =
        add(KeyEvent.KEYCODE_MINUS, KeyEvent.META_SHIFT_ON, U) // _
        add(KeyEvent.KEYCODE_MINUS, 0, I) // -
        add(KeyEvent.KEYCODE_EQUALS, KeyEvent.META_SHIFT_ON, O) // +
        add(KeyEvent.KEYCODE_2, KeyEvent.META_SHIFT_ON, P) // @
        add(KeyEvent.KEYCODE_6, KeyEvent.META_SHIFT_ON, A) // ^
        add(KeyEvent.KEYCODE_GRAVE, 0, S) // `
        add(KeyEvent.KEYCODE_BACKSLASH, KeyEvent.META_SHIFT_ON, D) // |
        add(KeyEvent.KEYCODE_8, KeyEvent.META_SHIFT_ON, F) // *
        add(KeyEvent.KEYCODE_SLASH, 0, G) // /
        add(KeyEvent.KEYCODE_SEMICOLON, KeyEvent.META_SHIFT_ON, H) // :
        add(KeyEvent.KEYCODE_SEMICOLON, 0, J) // ;
        add(KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.META_SHIFT_ON, K) // "
        add(KeyEvent.KEYCODE_APOSTROPHE, 0, L) // '
        add(KeyEvent.KEYCODE_5, KeyEvent.META_SHIFT_ON, Z) // %
        add(KeyEvent.KEYCODE_COMMA, KeyEvent.META_SHIFT_ON, V) // <
        add(KeyEvent.KEYCODE_PERIOD, KeyEvent.META_SHIFT_ON, B) // >
        add(KeyEvent.KEYCODE_1, KeyEvent.META_SHIFT_ON, N) // !
        add(KeyEvent.KEYCODE_SLASH, KeyEvent.META_SHIFT_ON, M) // ?
        add(KeyEvent.KEYCODE_4, KeyEvent.META_SHIFT_ON, PERIOD) // $
    }

    private val LETTER_KEY_CODES = intArrayOf(
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
        KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
        KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
        KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_T,
        KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_X,
        KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_Z
    )

    private val SCAN_CODE_KEYS = mapOf(
        2 to DIGIT_1, 3 to DIGIT_2, 4 to DIGIT_3, 5 to DIGIT_4, 6 to DIGIT_5,
        7 to DIGIT_6, 8 to DIGIT_7, 9 to DIGIT_8, 10 to DIGIT_9, 11 to DIGIT_0,
        14 to BACKSPACE, 15 to RED_CLICKS,
        16 to Q, 17 to W, 18 to E, 19 to R, 20 to T, 21 to Y, 22 to U, 23 to I,
        24 to O, 25 to P,
        28 to ENTER, 29 to CTRL,
        30 to A, 31 to S, 32 to D, 33 to F, 34 to G, 35 to H, 36 to J, 37 to K,
        38 to L,
        42 to SHIFT,
        44 to Z, 45 to X, 46 to C, 47 to V, 48 to B, 49 to N, 50 to M,
        52 to PERIOD, 57 to SPACE, 125 to LAUNCHER
    )

    private val KEY_CODE_KEYS = buildMap {
        listOf(
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0
        ).zip(numberRow).forEach { (keyCode, key) -> put(keyCode, key) }
        LETTER_KEY_CODES.zip(
            listOf(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z)
        ).forEach { (keyCode, key) -> put(keyCode, key) }
        put(KeyEvent.KEYCODE_DEL, BACKSPACE)
        put(KeyEvent.KEYCODE_ENTER, ENTER)
        put(KeyEvent.KEYCODE_SHIFT_LEFT, SHIFT)
        put(KeyEvent.KEYCODE_CTRL_LEFT, CTRL)
        put(KeyEvent.KEYCODE_SYM, SYM)
        put(KeyEvent.KEYCODE_TAB, RED_CLICKS)
        put(KeyEvent.KEYCODE_META_LEFT, LAUNCHER)
        put(KeyEvent.KEYCODE_SPACE, SPACE)
        put(KeyEvent.KEYCODE_PERIOD, PERIOD)
    }
}

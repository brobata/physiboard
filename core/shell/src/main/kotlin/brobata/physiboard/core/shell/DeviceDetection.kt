package brobata.physiboard.core.shell

/** Which Titan the phone identifies as, for the About screen, bug reports and the untested-device notice. spec: app-shell.md SS27 D1, D2. */
enum class TitanModel { TITAN_2_ELITE, TITAN_2, OTHER }

/** The `Build.*` strings the detection reads, already lower-cased by the caller (Android glue owns reading `Build`). spec: SS27. */
data class DeviceFields(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val display: String,
)

/**
 * Recognizes a Titan 2 Elite or a plain Titan 2 from `Build` fields alone (app-shell.md SS27 D1,
 * D2). Reviewer units expose Titan-2-like model strings but leak Elite traits through `board` or
 * `display`, which is why D1 has a fallback branch instead of one fixed string.
 */
object DeviceDetection {

    private val eliteMarkers = listOf("titan2elite_qwerty", "titan2elite-qwerty", "titan2eliteqwerty")

    fun classify(fields: DeviceFields): TitanModel {
        val all = listOf(fields.brand, fields.manufacturer, fields.model, fields.device, fields.product, fields.board, fields.display)
        val anyContains = { needle: String -> all.any { it.contains(needle) } }

        val isElite = eliteMarkers.any(anyContains) ||
            (anyContains("unihertz") || anyContains("titan")) &&
            (fields.display.contains("elite") || fields.board.contains("g72"))
        if (isElite) return TitanModel.TITAN_2_ELITE

        val isPlainTitan2 = (anyContains("unihertz") || anyContains("titan")) && (anyContains("titan 2") || anyContains("titan2"))
        return if (isPlainTitan2) TitanModel.TITAN_2 else TitanModel.OTHER
    }

    /** spec: SS15.1, the bug-report template's device dropdown label; the three strings must stay byte-identical to `.github/ISSUE_TEMPLATE/bug.yml`. */
    fun bugReportLabel(model: TitanModel): String = when (model) {
        TitanModel.TITAN_2_ELITE -> "Unihertz Titan 2 Elite"
        TitanModel.TITAN_2 -> "Unihertz Titan 2 (untested/unsupported)"
        TitanModel.OTHER -> "Something else"
    }
}

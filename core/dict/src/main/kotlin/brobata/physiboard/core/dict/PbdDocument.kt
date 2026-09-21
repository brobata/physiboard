package brobata.physiboard.core.dict

/**
 * The decoded contents of a `.pbd` file: its header fields and the sorted word list from the
 * `WORD` block. See [PbdReader] for the byte layout this was read from and [PbdWriter] for how
 * one is produced.
 */
data class PbdDocument(
    val language: LanguageCode,
    val formatVersion: Int,
    val entries: List<WordFrequency>,
)

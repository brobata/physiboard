package brobata.physiboard.core.dict

/**
 * Constants shared by [PbdWriter] and [PbdReader]. See [PbdReader]'s KDoc for the full byte
 * layout these describe.
 */
internal object PbdFormat {
    /** The ASCII bytes "PBD1", read/written as a big-endian Int32. */
    const val MAGIC = 0x50424431

    /** The only format version this milestone writes or reads. */
    const val CURRENT_VERSION = 1

    /** Width in bytes of the header's language-code field (NUL-padded ASCII). */
    const val LANGUAGE_FIELD_BYTES = 4

    /** The ASCII bytes "WORD", the tag of the block carrying the sorted word list. */
    const val WORD_BLOCK_TAG = 0x574F5244

    /** Fixed header size: magic(4) + version(2) + language(4) + reserved(2) + wordCount(4) + checksum(4). */
    const val HEADER_BYTES = 4 + 2 + LANGUAGE_FIELD_BYTES + 2 + 4 + 4
}

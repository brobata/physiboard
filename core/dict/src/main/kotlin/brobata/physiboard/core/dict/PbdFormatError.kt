package brobata.physiboard.core.dict

/**
 * Every way a byte stream can fail to be a valid `.pbd` dictionary. [PbdReader] never throws
 * for a malformed file; it reports one of these instead, so a corrupt download or a stale
 * build never crashes the keyboard process. spec: this module's own format (see [PbdReader]
 * for the byte layout the checks below refer to).
 */
sealed class PbdFormatError(val reason: String) {

    /** The stream ended before a field the format requires was fully read. */
    data object Truncated : PbdFormatError("truncated: file ends before a required field")

    /** The first four bytes are not the `PBD1` magic number. */
    data object BadMagic : PbdFormatError("not a .pbd file: bad magic number")

    /** The header's format version is not one this reader understands. */
    data class UnsupportedVersion(val version: Int) :
        PbdFormatError("unsupported format version $version")

    /** The header's language field is not a valid two-letter language code. */
    data class InvalidLanguageCode(val raw: String) :
        PbdFormatError("invalid language code '$raw' in header")

    /** No `WORD` block was found before the end of the file. */
    data object MissingWordBlock : PbdFormatError("missing WORD block")

    /** The `WORD` block's own entry count disagrees with the header's word count. */
    data class WordCountMismatch(val headerCount: Int, val blockCount: Int) :
        PbdFormatError(
            "header word count $headerCount does not match WORD block count $blockCount",
        )

    /** The stored checksum does not match the checksum computed while reading the file. */
    data class ChecksumMismatch(val expected: Long, val actual: Long) :
        PbdFormatError("checksum mismatch: expected $expected, computed $actual")

    /**
     * A block's count, length or word offset points outside the file. A single flipped bit in
     * a length field produces exactly this, so it is a refusal and not a crash.
     */
    data class CorruptBlock(val detail: String) : PbdFormatError("corrupt block: $detail")

    /**
     * The stream itself failed (an I/O error, not a format error). Reported rather than thrown
     * because the reader runs on a start-up thread where a throw would kill the keyboard.
     */
    data class Unreadable(val detail: String) : PbdFormatError("unreadable: $detail")
}

/** The outcome of reading a `.pbd` file: its decoded contents, or why it was refused. */
sealed class PbdReadResult {
    data class Loaded(val document: PbdDocument) : PbdReadResult()
    data class Failed(val error: PbdFormatError) : PbdReadResult()
}

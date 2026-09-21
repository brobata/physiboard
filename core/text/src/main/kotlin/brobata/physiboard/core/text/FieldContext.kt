package brobata.physiboard.core.text

/**
 * Which kind of text field the cursor is in, already classified by the caller from whatever
 * platform editor info it has (field class, variation, raw-mode app list). This module never
 * inspects Android constants; it only ever sees this one value. spec: text-input.md SS3 (the
 * field-type table) and SS19 ("Field classification ... keep").
 */
enum class FieldKind {
    /** class TEXT, no restricting variation. */
    NORMAL,

    /** variation URI. */
    URL,

    /** variations PASSWORD, VISIBLE_PASSWORD, WEB_PASSWORD, NUMBER_VARIATION_PASSWORD. */
    PASSWORD,

    /** variation EMAIL_ADDRESS. */
    EMAIL,

    /** variation FILTER (search-as-you-type lists). */
    FILTER,

    /** package is in the user's raw-mode list and no field variation above applies. */
    RAW_MODE_APP,

    /** class NUMBER or PHONE. */
    NUMBER_OR_PHONE,

    /** class DATETIME. */
    DATE_TIME,

    /** class NULL or 0. */
    NOT_EDITABLE,
}

/** The field's own capitalization hints, read once at field start. spec: text-input.md SS3. */
data class FieldCapFlags(
    /** `TYPE_TEXT_FLAG_CAP_CHARACTERS`: Caps Lock at field start; no per-character auto-cap. */
    val capCharacters: Boolean = false,
    /** `TYPE_TEXT_FLAG_CAP_WORDS`: arm Shift at every word start regardless of user settings. */
    val capWords: Boolean = false,
    /** `TYPE_TEXT_FLAG_CAP_SENTENCES`: use the normal auto-cap rules, gated on the user settings as usual. */
    val capSentences: Boolean = false,
)

/** The editor action the field declares, or none. spec: text-input.md SS3, SS7; autocorrect-suggestions.md SS7.1. */
enum class ImeAction { NONE, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS, CUSTOM }

/**
 * Everything the rest of this module needs to know about the field the cursor is in, bundled as
 * one value so every function below takes field awareness as a single parameter rather than a
 * scattered set of booleans. spec: text-input.md SS3, SS4.
 */
data class FieldContext(
    val kind: FieldKind,
    val capFlags: FieldCapFlags = FieldCapFlags(),
    val imeAction: ImeAction = ImeAction.NONE,
    val isMultiLine: Boolean = false,
    /**
     * The field reports itself as editable but not as class TEXT, NUMBER, PHONE or DATETIME (some
     * custom views). spec: text-input.md SS3, "'Really editable' ... A field that is editable but
     * not really editable ... gets auto-cap evaluation and nothing else." [kind] for such a field
     * should still be the closest match ([FieldKind.NORMAL] in practice); this flag is what turns
     * every other feature off while leaving auto-cap running.
     */
    val isEditableButNotReallyEditable: Boolean = false,
) {
    /** spec: text-input.md SS3, "Restricted field": password, URL, email, filter, or a raw-mode app. */
    val isRestricted: Boolean
        get() = kind == FieldKind.PASSWORD || kind == FieldKind.URL || kind == FieldKind.EMAIL ||
            kind == FieldKind.FILTER || kind == FieldKind.RAW_MODE_APP

    /** spec: text-input.md SS3, "Really editable" means class TEXT, NUMBER, PHONE or DATETIME. */
    val isReallyEditable: Boolean
        get() = kind != FieldKind.NOT_EDITABLE && !isEditableButNotReallyEditable

    /** spec: text-input.md SS3 table, "Suggestions" column: off for every restricted kind, NOT_EDITABLE and any not-really-editable field. */
    val suggestionsAllowed: Boolean
        get() = !isRestricted && kind != FieldKind.NOT_EDITABLE && !isEditableButNotReallyEditable

    /** spec: text-input.md SS3 table, "Autocorrect" column: same gate as suggestions here, since 3.0 has one engine (SS19 W4). */
    val autocorrectAllowed: Boolean get() = suggestionsAllowed

    /** spec: text-input.md SS3 table, "Double-space period" column: off in every restricted field. */
    val doubleSpacePeriodAllowed: Boolean get() = !isRestricted

    /** spec: text-input.md SS3 table, "Variations" column: on everywhere except email (no accents in addresses). */
    val variationsAllowed: Boolean get() = kind != FieldKind.EMAIL

    /** spec: text-input.md SS6.8, "Off in restricted fields." */
    val hyphenToDashAllowed: Boolean get() = !isRestricted

    /** spec: text-input.md SS6.10, "Off in restricted fields." */
    val smartQuotesAllowed: Boolean get() = !isRestricted

    /** spec: text-input.md SS6.5 has no explicit restricted-field carve-out; treated the same as the other smart-punctuation features. */
    val frenchSpacingAllowed: Boolean get() = !isRestricted

    /**
     * spec: text-input.md SS9.4: whether auto-cap runs at all in this field, given
     * [restrictedFieldsCapitalizeSetting] (`auto_capitalize_restricted_fields`). Password fields
     * and raw-mode apps are never lifted (spec D8: "Raw-mode apps keep auto-cap off even when
     * 'Shift in all text fields' is on").
     */
    fun autoCapAllowed(restrictedFieldsCapitalizeSetting: Boolean): Boolean = when (kind) {
        FieldKind.PASSWORD, FieldKind.RAW_MODE_APP, FieldKind.NOT_EDITABLE -> false
        FieldKind.URL, FieldKind.EMAIL, FieldKind.FILTER -> restrictedFieldsCapitalizeSetting
        else -> true
    }
}

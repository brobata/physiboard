package brobata.physiboard.core.dict

/**
 * A word and its raw dictionary frequency. The `.pbd` format widens this to an unsigned
 * 16-bit field (0..65535); spec: dictionaries-languages.md §2.2 describes the old format's
 * `{"word","frequency","source"}` entry and its 0..255 clamp, which §17's Keep/Drop table
 * calls out to fix rather than carry forward ("Norwegian and Ukrainian need rebuilt scales,
 * not a clamp").
 */
data class WordFrequency(val word: String, val frequency: Int)

/**
 * One fuzzy-match candidate returned by [DictionaryIndex.neighbours]: its spelling, raw
 * frequency and edit distance from the query word. spec: autocorrect-suggestions.md §3.4.
 */
data class ScoredCandidate(val word: String, val frequency: Int, val distance: Int)

package brobata.physiboard.core.dict

/**
 * spec dictionaries-languages.md SS3, "Resolution for a language": decides which tier's file the
 * keyboard should load for a language, given only whether each tier already has a usable file
 * there. This is deliberately separate from [DictionaryCatalog] (the installed-dictionaries
 * *screen*'s merge and dedup, which answers "how is this language listed") because the runtime
 * loader (`:ime`'s `DictionaryAssetLoader`) needs this exact decision with no Android import and
 * no file system at all, so it can be driven directly by the spec's own T1-T11 test cases.
 */
object DictionaryTierResolver {

    /**
     * SS3: "the first of `imported/<lang>_base.dict`, `downloaded/<lang>_base.dict` that exists
     * as a regular file with length greater than 0; otherwise the bundled asset if it exists;
     * otherwise nothing." [imported] and [downloaded] must already carry that zero-length check;
     * [bundled] is a plain existence flag since SS3 states "the zero-length rule applies only to
     * the writable tiers".
     */
    fun resolve(imported: Boolean, downloaded: Boolean, bundled: Boolean): DictionaryOrigin? = when {
        imported -> DictionaryOrigin.IMPORTED
        downloaded -> DictionaryOrigin.DOWNLOADED
        bundled -> DictionaryOrigin.BUNDLED
        else -> null
    }

    /** SS3: "A language 'has a dictionary' when any of the three exists." */
    fun hasDictionary(imported: Boolean, downloaded: Boolean, bundled: Boolean): Boolean =
        resolve(imported, downloaded, bundled) != null
}

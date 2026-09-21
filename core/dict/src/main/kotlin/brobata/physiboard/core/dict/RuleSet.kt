package brobata.physiboard.core.dict

/**
 * One named set of substitution rules for a language code, or a custom code such as
 * `x-pastiera`. [rules] maps a trigger, as it would be typed, to its replacement; spec:
 * autocorrect-suggestions.md §8.1's reserved `__name` key is not stored in [rules], it becomes
 * [displayName] instead, so this type can never represent the invalid state of `__name` being
 * treated as an ordinary rule.
 */
data class RuleSet(val code: String, val displayName: String?, val rules: Map<String, String>) {
    init {
        require("__name" !in rules) {
            "__name is RuleSet.displayName's source key, not a rule; strip it before constructing a RuleSet"
        }
    }

    /**
     * Overlays [custom] rules over this set, key by key, the way a saved custom set overrides
     * the bundled set of the same code. spec: autocorrect-suggestions.md §8.1 ("Custom rules
     * per code... overlay the bundled set of the same code, key by key").
     */
    fun overlaidWith(custom: Map<String, String>): RuleSet = copy(rules = rules + custom)
}

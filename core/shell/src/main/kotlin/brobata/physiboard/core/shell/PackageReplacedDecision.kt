package brobata.physiboard.core.shell

/**
 * The 2.x package-replaced receiver's whole decision (app-shell.md SS17, T21): given what
 * `default_input_method` reads right after an update, does the phone still have PhysiBoard
 * selected. spec: SS30 Keep/Drop drops the receiver itself for 3.0 ("3.0 must instead decide its
 * own component name and keep it"), but the predicate is kept here because it is the same
 * "does this id match" question the enabled/selected probe answers, and because T21 pins it down.
 * Nothing in `:app` wires this to a `MY_PACKAGE_REPLACED` receiver.
 */
object PackageReplacedDecision {
    /** True (post a "pick PhysiBoard again" notification) when the stored id is not this build's own component, in either id form. */
    fun shouldNotify(defaultInputMethodId: String?, packageName: String, serviceClassName: String): Boolean =
        !ImeIdentity.matches(defaultInputMethodId, packageName, serviceClassName)
}

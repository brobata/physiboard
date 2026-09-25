# PhysiBoard 3.0: consumer keep rules for :ime, merged automatically into any app that depends on
# this module (docs/release.md, "Shrinking"). Both classes below are instantiated by the system
# from a name in AndroidManifest.xml, never constructed by this app's own code, so R8's ordinary
# reachability analysis has no way to know they are used.

# The keyboard service: android.view.InputMethod's intent-filter names it, and the framework binds
# to it as android.permission.BIND_INPUT_METHOD.
-keep class brobata.physiboard.ime.PhysiBoardInputMethodService { *; }

# dictation.md SS10: a keyboard service cannot show the runtime RECORD_AUDIO dialog itself, so this
# is launched from the service above by class name.
-keep class brobata.physiboard.ime.DictationPermissionActivity { *; }

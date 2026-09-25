package brobata.physiboard.app.shell

/** This build's own input-method service (declared in `ime/src/main/AndroidManifest.xml`), the id every shell screen probes against. spec: app-shell.md SS1. */
object ImeComponent {
    const val SERVICE_CLASS_NAME = "brobata.physiboard.ime.PhysiBoardInputMethodService"
}

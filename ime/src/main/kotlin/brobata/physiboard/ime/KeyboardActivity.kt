package brobata.physiboard.ime

/**
 * What the keyboard is doing right now, for the one reader outside `:ime` that must not interrupt
 * it: the app's automatic updater, which waits for a dictation session to end before installing
 * (app-shell.md SS32.4). Written on the main thread when a session starts or ends, read from a
 * background worker; nothing on the key path touches it.
 */
object KeyboardActivity {
    @Volatile
    var dictationActive: Boolean = false
        internal set
}

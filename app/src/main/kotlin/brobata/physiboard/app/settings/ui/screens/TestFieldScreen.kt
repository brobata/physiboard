package brobata.physiboard.app.settings.ui.screens

import android.graphics.Typeface
import android.view.Gravity
import android.widget.EditText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold

/**
 * "A field to type in, and nothing else" (the milestone-3 `MainActivity`, kept verbatim as a
 * plain [EditText] rather than a Compose text field, so this stays the same real target the
 * pipeline was proven against on the phone). rebuild-from-scratch.md: "Leave MainActivity's
 * typing field reachable from a Test field row."
 *
 * It sits at the top at its natural height. Filling the screen put the hint halfway down with the
 * field's own underline four hundred pixels below it, which read as a broken layout rather than a
 * text box (2026-09-29); the typeface matches the rest of the app for the same reason. Focus is
 * asked for on arrival because typing is the only thing this screen is for.
 */
@Composable
fun TestFieldScreen(onBack: () -> Unit) {
    SettingsScreenScaffold(title = "Test field", onBack = onBack) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            AndroidView(
                factory = { context ->
                    EditText(context).apply {
                        hint = "Type here"
                        typeface = Typeface.MONOSPACE
                        gravity = Gravity.TOP or Gravity.START
                        setLines(4)
                        requestFocus()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

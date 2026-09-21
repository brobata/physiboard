package brobata.physiboard.app

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout

/**
 * A field to type in, and nothing else.
 *
 * Milestone 3 is about proving the pipeline on the real phone, so the app is a
 * place to type. Onboarding, settings and the rest arrive with their own
 * milestones.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val field = EditText(this).apply { hint = "Type here" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(field)
        })
    }
}

package it.palsoftware.pastiera

import android.app.Activity

@Suppress("DEPRECATION")
fun Activity.applySlideInFromRightTransition() {
    overridePendingTransition(R.anim.slide_in_from_right, 0)
}

@Suppress("DEPRECATION")
fun Activity.applySlideOutToRightTransition() {
    overridePendingTransition(0, R.anim.slide_out_to_right)
}

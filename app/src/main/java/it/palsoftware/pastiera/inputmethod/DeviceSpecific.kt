package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent

object DeviceSpecific {
    private const val TAG = "DeviceSpecific"

    // Keyboard type
    private val kbdIsUnihertz: Boolean =
        android.os.Build.DEVICE.contains("titan", ignoreCase = true)
    private val kbdIsQ25: Boolean = android.os.Build.DEVICE == "Q25"

    // Unihertz scan codes (Titan2)
    private const val SCANCODE_TITAN2_CTRL: Int = 251
    private const val SCANCODE_TITAN2_SYM: Int = 253

    // private const val KEYCODE_SHIFT = KeyEvent.KEYCODE_SHIFT_LEFT
    // private const val KEYCODE_ALT = KeyEvent.KEYCODE_ALT_LEFT
    private const val KEYCODE_CTRL  = KeyEvent.KEYCODE_CTRL_LEFT
    private const val KEYCODE_SYM = KeyEvent.KEYCODE_SYM
    // Blackberry Q25 CTRL and SYM keycodes
    private const val KEYCODE_Q25_CTRL = KeyEvent.KEYCODE_SHIFT_RIGHT
    private const val KEYCODE_Q25_SYM = KeyEvent.KEYCODE_ALT_RIGHT

    // mask of all the metas we'll rebuild
    private const val RELOAD_METAS =
        KeyEvent.META_SHIFT_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_CTRL_MASK or KeyEvent.META_SYM_ON

    // Blackberry Q25 SHIFT, ALT and CTRL meta states (form hardware)
    private const val META_Q25_SHIFT = KeyEvent.META_SHIFT_LEFT_ON
    private const val META_Q25_ALT = KeyEvent.META_ALT_LEFT_ON
    private const val META_Q25_CTRL = KeyEvent.META_SHIFT_RIGHT_ON
    private const val META_Q25_SYM = KeyEvent.META_ALT_RIGHT_ON

    // Android metas the Q25 ones need to be remapped to
    private const val META_SHIFT = KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON
    private const val META_ALT = KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_ON
    private const val META_CTRL = KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_ON
    private const val META_SYM = KeyEvent.META_SYM_ON

    private var lastMeta: Int = 0

    private inline fun q25needsPatching(k: Int, e: KeyEvent?): Boolean {
        // a (key,event) pair needs to be patched if either
        // - the key is CTRL or SYM
        // - we do have an event, and
        //   the current and/or the previous meta had CTRL and/or SYM
        if ((k == KEYCODE_Q25_CTRL) || (k == KEYCODE_Q25_SYM))
            return true
        if (e == null)
            return false
        if (((e.metaState or lastMeta) and (META_Q25_CTRL or META_Q25_ALT)) != 0)
            return true;
        return false;
    }

    private fun q25patchMetaState(e: KeyEvent?): KeyEvent? {
        if (e == null)
            return e
        // rebuild the meta state
        val crtMeta = e.metaState
        // do nothing at all unless either
        // - one of the Q25-specific metas is active
        // - one of the Q25-specific metas was active on the previous event
        //   the later is needed only for patching the keyCode of the ACTION_UP on SYM and CTRL
        if (((crtMeta or lastMeta) and (META_Q25_CTRL or META_Q25_SYM)) == 0)
            return e;
        lastMeta = crtMeta
        val newShift = if ((crtMeta and META_Q25_SHIFT) != 0) META_SHIFT else 0
        val newCtrl = if ((crtMeta and META_Q25_CTRL) != 0) META_CTRL else 0
        val newAlt = if ((crtMeta and META_Q25_ALT) != 0) META_ALT else 0
        val newSym = if ((crtMeta and META_Q25_SYM) != 0) META_SYM else 0
        val newMeta = (crtMeta and RELOAD_METAS.inv()) or (newShift or newCtrl or newAlt or newSym)
        // for CTRL itself, patch the keycode and scancode too (use the Titan2 scancode)
        if (e.keyCode == KEYCODE_Q25_CTRL) // KEYCODE_SHIFT_RIGHT
            return KeyEvent(
                e.downTime, e.eventTime, e.action,
                KEYCODE_CTRL, e.repeatCount, newMeta,
                e.deviceId, SCANCODE_TITAN2_CTRL, e.flags, e.source
            )
        // for SYM itself, patch the keycode and scancode too (use the Titan2 scancode)
        if (e.keyCode == KEYCODE_Q25_SYM) // KEYCODE_ALT_RIGHT
            return KeyEvent(
                e.downTime, e.eventTime, e.action,
                KEYCODE_SYM, e.repeatCount, newMeta,
                e.deviceId, SCANCODE_TITAN2_SYM, e.flags, e.source
            )
        // any other key only needs its metas patched
        return KeyEvent(
            e.downTime, e.eventTime, e.action,
            e.keyCode, e.repeatCount, newMeta,
            e.deviceId, e.scanCode, e.flags, e.source
        )
    }

    fun needsRemapping(): Boolean
    {
        return kbdIsQ25
    }

    fun remapKeyEvent(k_: Int, e_: KeyEvent?): Pair<Int, KeyEvent?>? {
        if (!kbdIsQ25 || !q25needsPatching(k_, e_))
            return null
        val k = if (k_ == KEYCODE_Q25_CTRL) KEYCODE_CTRL else
            if (k_ == KEYCODE_Q25_SYM) KEYCODE_SYM else k_
        return Pair(k, q25patchMetaState(e_))
    }

    fun deviceName(): String {
        return android.os.Build.BRAND + " " + android.os.Build.MODEL
    }

    fun keyboardName(): String {
        if (kbdIsQ25)
            return "Blackberry"
        if (kbdIsUnihertz)
            return "Unihertz"
        return "unknown"
    }

    fun physicalKeyboardName(): String {
        if (kbdIsUnihertz)
            return "titan2"
        if (kbdIsQ25)
            return "Q25"
        return "unknown"
    }
}
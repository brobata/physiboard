package brobata.physiboard.ime.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import brobata.physiboard.core.text.FieldFocusRescue
import brobata.physiboard.ime.SettingsSourceOwner
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * PhysiBoard's optional accessibility service. It does two things and nothing else
 * (per-app-behavior.md SS16):
 *
 * 1. **Focus the text box when you start typing** (`accessibility_focus_field`): when the first
 *    key typed into a field lands in a box that is connected to the keyboard but has no focus
 *    (Google Messages opens like this), the keyboard asks the service to focus that box, as a tap
 *    would, and to put the cursor back where it was. The decisions are `:core:text`'s
 *    [FieldFocusRescue]; this class only reads the window and performs the two actions.
 * 2. **Fn shortcuts everywhere** (`accessibility_fn_shortcuts`): with key filtering on, every
 *    hardware key comes here first. While the keyboard has no text box, the key goes to the
 *    keyboard's own no-field path ([AccessibilityBridge.KeyboardHook], keys-and-modifiers.md
 *    SS15.1); everything else is returned unhandled, untouched.
 *
 * Its footprint is kept to what those need. It subscribes to no accessibility events at all: the
 * window is read only on the keyboard's request, once per field. It reads window content only to
 * find the box to focus. It logs no node text and no key, stores nothing, and has no network
 * access of its own. With "Fn shortcuts everywhere" off it gives up key filtering, so Android
 * stops routing keys through it.
 */
class PhysiBoardAccessibilityService : AccessibilityService() {

    /** Window reads and actions are calls into the app being typed into; none of them runs on the main thread, where the keyboard works. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "physiboard-a11y") }
    private val scope = MainScope()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        // No events are subscribed, so nothing would ever tell the node cache it is stale: every
        // read goes to the window instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { setCacheEnabled(false) }
        AccessibilityBridge.attach(this)
        (applicationContext as? SettingsSourceOwner)?.settingsSource?.let { source ->
            scope.launch {
                source.settings.map { it.keys.accessibilityFnShortcuts }.distinctUntilChanged().collect { on -> setKeyFiltering(on) }
            }
        }
        Log.i(TAG, "connected")
    }

    /** Key filtering is declared in the service's XML, so Android allows it; it is switched on and off here to follow the setting. */
    private fun setKeyFiltering(on: Boolean) {
        val info = serviceInfo ?: return
        val flags = if (on) info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        if (flags == info.flags) return
        info.flags = flags
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /**
     * Called for every hardware key before any window sees it, so it must answer at once and must
     * never throw: false hands the key on exactly as if the service were not there.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean = runCatching {
        AccessibilityBridge.keyboard?.onKeyFromAccessibility(event) ?: false
    }.getOrElse { error ->
        Log.e(TAG, "key relay crashed; the key goes on to the app", error)
        false
    }

    /** spec: expansion-clipboard-pickers-launcher.md SS8.2, the Home command, done the way the Home key does it. */
    fun goHome(): Boolean = runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }.getOrDefault(false)

    fun focusField(request: FocusRequest) {
        runCatching { worker.execute { runCatching { rescue(request) }.onFailure { error -> Log.w(TAG, "focus: failed (${error.javaClass.simpleName})") } } }
    }

    /** spec: per-app-behavior.md SS16.2. Logs outcomes only: never a node's text, hint or the field's contents. */
    private fun rescue(request: FocusRequest) {
        val root = rootInActiveWindow
        if (root == null) {
            Log.i(TAG, "focus: no window")
            return
        }
        if (root.packageName?.toString() != request.packageName) {
            Log.i(TAG, "focus: the active window is another app's")
            return
        }
        // The common case: some view already has input focus. D9's box is the one where none
        // does; a focused view that is not editable in accessibility terms (a terminal, a code
        // editor) is still the user's box, so any focus at all means hands off.
        if (root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) != null) return
        val reading = readOnMain(request.readField)
        if (reading == null) {
            Log.i(TAG, "focus: the field changed or could not be read")
            return
        }
        val nodes = editableNodes(root)
        val boxes = nodes.map { node ->
            val password = node.isPassword
            FieldFocusRescue.Box(
                focused = node.isFocused,
                visible = node.isVisibleToUser,
                enabled = node.isEnabled,
                packageName = node.packageName?.toString(),
                password = password,
                text = if (password) null else node.text?.toString(),
                hint = if (password) null else node.hintText?.toString(),
            )
        }
        when (val target = FieldFocusRescue.choose(boxes, reading.expected)) {
            FieldFocusRescue.Target.AlreadyFocused -> Unit
            is FieldFocusRescue.Target.Nothing -> Log.i(TAG, "focus: nothing done (${target.reason}, ${boxes.size} boxes)")
            is FieldFocusRescue.Target.Focus -> focus(nodes[target.index], boxes[target.index].password, reading.keyboardSelection)
        }
    }

    /** Runs [read] on the main thread, where the keyboard's connection lives, and waits for it briefly; null on timeout. */
    private fun readOnMain(read: () -> FieldReading?): FieldReading? {
        val latch = CountDownLatch(1)
        var result: FieldReading? = null
        mainHandler.post {
            result = runCatching(read).getOrNull()
            latch.countDown()
        }
        return if (latch.await(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)) result else null
    }

    private fun focus(node: AccessibilityNodeInfo, password: Boolean, keyboardSelection: FieldFocusRescue.Selection?) {
        val before = node.selection()
        val lengthBefore = node.textLength()
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.refresh()
        var how = "focus"
        if (!node.isFocused) {
            // Some boxes take focus only from a click, which is what the tap does.
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.refresh()
            how = "click"
        }
        val restore = FieldFocusRescue.selectionToRestore(before, node.selection(), keyboardSelection, password, lengthBefore, node.textLength())
        if (restore != null) {
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, restore.start)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, restore.end)
                },
            )
        }
        Log.i(TAG, "focus: by $how, focused=${node.isFocused}, cursor ${if (restore != null) "restored" else "kept"}")
    }

    /** The length only, never the text; -1 for a password box or none. */
    private fun AccessibilityNodeInfo.textLength(): Int = if (isPassword) -1 else text?.length ?: -1

    private fun AccessibilityNodeInfo.selection(): FieldFocusRescue.Selection = FieldFocusRescue.Selection(textSelectionStart, textSelectionEnd)

    /** The window's editable nodes, breadth first, bounded so a huge list can never stall the thread. */
    private fun editableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val found = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_VISITED) {
            val node = queue.removeFirst()
            visited++
            if (node.isEditable) found += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return found
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        AccessibilityBridge.detach(this)
        scope.cancel()
        worker.shutdownNow()
    }

    private companion object {
        const val TAG = "PhysiBoardA11y"
        const val MAX_NODES_VISITED = 2_000
        const val READ_TIMEOUT_MS = 1_000L
    }
}

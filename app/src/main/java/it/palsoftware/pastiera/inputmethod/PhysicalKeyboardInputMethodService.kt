package it.palsoftware.pastiera.inputmethod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import it.palsoftware.pastiera.SettingsManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.view.MotionEvent
import android.view.View
import android.view.InputDevice
import it.palsoftware.pastiera.inputmethod.MotionEventTracker
import it.palsoftware.pastiera.core.AutoCorrectionManager
import it.palsoftware.pastiera.core.InputContextState
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.core.TextInputController
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.data.variation.VariationRepository

/**
 * Input method service specialized for physical keyboards.
 * Handles advanced features such as long press that simulates Alt+key.
 */
class PhysicalKeyboardInputMethodService : InputMethodService() {

    companion object {
        private const val TAG = "PastieraInputMethod"
    }

    // SharedPreferences for settings
    private lateinit var prefs: SharedPreferences
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private lateinit var altSymManager: AltSymManager
    
    // Broadcast receiver for speech recognition
    private var speechResultReceiver: BroadcastReceiver? = null
    private lateinit var candidatesBarController: CandidatesBarController

    // Keycode for the SYM key
    private val KEYCODE_SYM = 63
    
    // Mapping Ctrl+key -> action or keycode (loaded from JSON)
    private val ctrlKeyMap = mutableMapOf<Int, KeyMappingLoader.CtrlMapping>()
    
    // Accessor properties for backwards compatibility with existing code
    private var capsLockEnabled: Boolean
        get() = modifierStateController.capsLockEnabled
        set(value) { modifierStateController.capsLockEnabled = value }
    
    private var shiftPressed: Boolean
        get() = modifierStateController.shiftPressed
        set(value) { modifierStateController.shiftPressed = value }
    
    private var ctrlLatchActive: Boolean
        get() = modifierStateController.ctrlLatchActive
        set(value) { modifierStateController.ctrlLatchActive = value }
    
    private var altLatchActive: Boolean
        get() = modifierStateController.altLatchActive
        set(value) { modifierStateController.altLatchActive = value }
    
    private var ctrlPressed: Boolean
        get() = modifierStateController.ctrlPressed
        set(value) { modifierStateController.ctrlPressed = value }
    
    private var altPressed: Boolean
        get() = modifierStateController.altPressed
        set(value) { modifierStateController.altPressed = value }
    
    private var shiftPhysicallyPressed: Boolean
        get() = modifierStateController.shiftPhysicallyPressed
        set(value) { modifierStateController.shiftPhysicallyPressed = value }
    
    private var ctrlPhysicallyPressed: Boolean
        get() = modifierStateController.ctrlPhysicallyPressed
        set(value) { modifierStateController.ctrlPhysicallyPressed = value }
    
    private var altPhysicallyPressed: Boolean
        get() = modifierStateController.altPhysicallyPressed
        set(value) { modifierStateController.altPhysicallyPressed = value }
    
    private var shiftOneShot: Boolean
        get() = modifierStateController.shiftOneShot
        set(value) { modifierStateController.shiftOneShot = value }
    
    private var ctrlOneShot: Boolean
        get() = modifierStateController.ctrlOneShot
        set(value) { modifierStateController.ctrlOneShot = value }
    
    private var altOneShot: Boolean
        get() = modifierStateController.altOneShot
        set(value) { modifierStateController.altOneShot = value }
    
    private var shiftOneShotEnabledTime: Long
        get() = modifierStateController.shiftOneShotEnabledTime
        set(value) { modifierStateController.shiftOneShotEnabledTime = value }
    
    private var ctrlLatchFromNavMode: Boolean
        get() = modifierStateController.ctrlLatchFromNavMode
        set(value) { modifierStateController.ctrlLatchFromNavMode = value }
    
    // Flag to track whether we are in a valid input context
    private var isInputViewActive = false
    
    // Snapshot of the current input context (numeric/password/restricted fields, etc.)
    private var inputContextState: InputContextState = InputContextState.EMPTY
    
    private val isNumericField: Boolean
        get() = inputContextState.isNumericField
    
    private val shouldDisableSmartFeatures: Boolean
        get() = inputContextState.shouldDisableSmartFeatures
    
    
    // Cache for launcher packages
    private var cachedLauncherPackages: Set<String>? = null
    
    // Current package name
    private var currentPackageName: String? = null
    
    // Modifier/nav/SYM controllers
    private lateinit var modifierStateController: ModifierStateController
    private lateinit var navModeController: NavModeController
    private lateinit var symLayoutController: SymLayoutController
    private lateinit var textInputController: TextInputController
    private lateinit var autoCorrectionManager: AutoCorrectionManager
    private lateinit var variationStateController: VariationStateController
    private lateinit var inputEventRouter: InputEventRouter
    private lateinit var keyboardVisibilityController: KeyboardVisibilityController
    
    // Auto-capitalize helper state
    private val autoCapitalizeState = AutoCapitalizeHelper.AutoCapitalizeState()
    
    // Constants
    private val DOUBLE_TAP_THRESHOLD = 500L
    private val CURSOR_UPDATE_DELAY = 50L

    private val symPage: Int
        get() = if (::symLayoutController.isInitialized) symLayoutController.currentSymPage() else 0

    private fun updateInputContextState(info: EditorInfo?) {
        inputContextState = InputContextState.fromEditorInfo(info)
    }

    private fun refreshStatusBar() {
        updateStatusBarText()
    }
    
    

    /**
     * Initializes the input context for a field.
     * This method contains all common initialization logic that must run
     * regardless of whether input view or candidates view is shown.
     */
    private fun initializeInputContext(restarting: Boolean) {
        if (restarting) {
            return
        }
        
        val state = inputContextState
        val isEditable = state.isEditable
        val isReallyEditable = state.isReallyEditable
        val canCheckAutoCapitalize = isEditable && !state.shouldDisableSmartFeatures
        
        if (!isReallyEditable) {
            isInputViewActive = false
            
            if (canCheckAutoCapitalize) {
                AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
                    this,
                    currentInputConnection,
                    shouldDisableSmartFeatures,
                    autoCapitalizeState,
                    onUpdateStatusBar = { updateStatusBarText() }
                )
            }
            return
        }
        
        isInputViewActive = true
        
        enforceSmartFeatureDisabledState()
        
        if (ctrlLatchFromNavMode && ctrlLatchActive) {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                navModeController.exitNavMode()
            }
        }
        
        AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
            this,
            currentInputConnection,
            shouldDisableSmartFeatures,
            autoCapitalizeState,
            onUpdateStatusBar = { updateStatusBarText() }
        )
        
        symLayoutController.restoreSymPageIfNeeded { updateStatusBarText() }
        
        altSymManager.reloadLongPressThreshold()
        altSymManager.resetTransientState()
    }
    
    private fun enforceSmartFeatureDisabledState() {
        if (!shouldDisableSmartFeatures) {
            return
        }
        setCandidatesViewShown(false)
        deactivateVariations()
    }
    
    /**
     * Reloads nav mode key mappings from the file.
     */
    private fun loadKeyboardLayout() {
        val layoutName = SettingsManager.getKeyboardLayout(this)
        val layout = LayoutMappingRepository.loadLayout(assets, layoutName, this)
        Log.d(TAG, "Keyboard layout loaded: $layoutName")
    }
    
    /**
     * Gets the character from the selected keyboard layout for a given keyCode and shift state.
     * If the keyCode is mapped in the layout, returns that character.
     * Otherwise, returns the character from the event (if available).
     * This ensures that keyboard layouts work correctly regardless of Android's system layout settings.
     */
    private fun getCharacterFromLayout(keyCode: Int, event: KeyEvent?, isShift: Boolean): Char? {
        // First, try to get the character from the selected layout
        val layoutChar = LayoutMappingRepository.getCharacter(keyCode, isShift)
        if (layoutChar != null) {
            return layoutChar
        }
        // If not mapped in layout, fall back to event's unicode character
        if (event != null && event.unicodeChar != 0) {
            return event.unicodeChar.toChar()
        }
        return null
    }
    
    /**
     * Gets the character string from the selected keyboard layout.
     * Returns the original event character if not mapped in layout.
     */
    private fun getCharacterStringFromLayout(keyCode: Int, event: KeyEvent?, isShift: Boolean): String {
        val char = getCharacterFromLayout(keyCode, event, isShift)
        return char?.toString() ?: ""
    }
    
    private fun reloadNavModeMappings() {
        try {
            ctrlKeyMap.clear()
            val assets = assets
            ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets, this))
            Log.d(TAG, "Nav mode mappings reloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading nav mode mappings", e)
        }
    }
    
    /**
     * Verifica se il package corrente è un launcher.
     */
    private fun isLauncher(packageName: String?): Boolean {
        if (packageName == null) return false
        
        // Cache la lista dei launcher per evitare query ripetute
        if (cachedLauncherPackages == null) {
            try {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                
                val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                cachedLauncherPackages = resolveInfos.map { it.activityInfo.packageName }.toSet()
                Log.d(TAG, "Launcher packages trovati: $cachedLauncherPackages")
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel rilevamento dei launcher", e)
                cachedLauncherPackages = emptySet()
            }
        }
        
        val isLauncher = cachedLauncherPackages?.contains(packageName) ?: false
        Log.d(TAG, "isLauncher($packageName) = $isLauncher")
        return isLauncher
    }
    
    /**
     * Apre un'app tramite package name.
     */
    private fun launchApp(packageName: String): Boolean {
        try {
            val pm = packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d(TAG, "App aperta: $packageName")
                return true
            } else {
                Log.w(TAG, "Nessun launch intent trovato per: $packageName")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'apertura dell'app $packageName", e)
            return false
        }
    }
    
    /**
     * Checks if a keycode corresponds to an alphabetic key (A-Z).
     * Returns true only for alphabetic keys, false for all others (modifiers, volume, etc.).
     */
    private fun isAlphabeticKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_Z -> true
            else -> false
        }
    }
    
    /**
     * Handles launcher shortcuts when not in a text field.
     */
    private fun handleLauncherShortcut(keyCode: Int): Boolean {
        val shortcut = SettingsManager.getLauncherShortcut(this, keyCode)
        if (shortcut != null) {
            // Gestisci diversi tipi di azioni
            when (shortcut.type) {
                SettingsManager.LauncherShortcut.TYPE_APP -> {
                    if (shortcut.packageName != null) {
                        val success = launchApp(shortcut.packageName)
                        if (success) {
                            Log.d(TAG, "Scorciatoia launcher eseguita: tasto $keyCode -> ${shortcut.packageName}")
                            return true // Consumiamo l'evento
                        }
                    }
                }
                SettingsManager.LauncherShortcut.TYPE_SHORTCUT -> {
                    // TODO: Gestire scorciatoie in futuro
                    Log.d(TAG, "Tipo scorciatoia non ancora implementato: ${shortcut.type}")
                }
                else -> {
                    Log.d(TAG, "Tipo azione sconosciuto: ${shortcut.type}")
                }
            }
        } else {
            // Tasto non assegnato: mostra dialog per assegnare un'app
            showLauncherShortcutAssignmentDialog(keyCode)
            return true // Consumiamo l'evento per evitare che venga gestito altrove
        }
        return false // Non consumiamo l'evento
    }
    
    /**
     * Mostra il dialog per assegnare un'app a un tasto.
     */
    private fun showLauncherShortcutAssignmentDialog(keyCode: Int) {
        try {
            val intent = Intent(this, LauncherShortcutAssignmentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(LauncherShortcutAssignmentActivity.EXTRA_KEY_CODE, keyCode)
            }
            startActivity(intent)
            Log.d(TAG, "Dialog assegnazione mostrato per tasto $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel mostrare il dialog di assegnazione", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        
        NotificationHelper.createNotificationChannel(this)
        
        modifierStateController = ModifierStateController(DOUBLE_TAP_THRESHOLD, autoCapitalizeState)
        navModeController = NavModeController(this, modifierStateController)
        inputEventRouter = InputEventRouter(this, navModeController)
        textInputController = TextInputController(
            context = this,
            modifierStateController = modifierStateController,
            autoCapitalizeState = autoCapitalizeState,
            doubleTapThreshold = DOUBLE_TAP_THRESHOLD
        )
        autoCorrectionManager = AutoCorrectionManager(this)
        
        candidatesBarController = CandidatesBarController(this)

        // Register listener for variation selection (both controllers)
        val variationListener = object : VariationButtonHandler.OnVariationSelectedListener {
            override fun onVariationSelected(variation: String) {
                // Update variations after one has been selected (refresh view if needed)
                updateStatusBarText()
            }
        }
        candidatesBarController.onVariationSelectedListener = variationListener

        // Register listener for cursor movement (both controllers)
        val cursorListener = {
            updateStatusBarText()
        }
        candidatesBarController.onCursorMovedListener = cursorListener
        altSymManager = AltSymManager(assets, prefs, this)
        altSymManager.reloadSymMappings() // Load custom mappings for page 1 if present
        altSymManager.reloadSymMappings2() // Load custom mappings for page 2 if present
        // Register callback to be notified when an Alt character is inserted after long press.
        // Variations are updated automatically by updateStatusBarText().
        altSymManager.onAltCharInserted = { char ->
            updateStatusBarText()
        }
        symLayoutController = SymLayoutController(this, prefs, altSymManager)
        keyboardVisibilityController = KeyboardVisibilityController(
            candidatesBarController = candidatesBarController,
            symLayoutController = symLayoutController,
            isInputViewActive = { isInputViewActive },
            isNavModeLatched = { ctrlLatchFromNavMode },
            currentInputConnection = { currentInputConnection },
            isInputViewShown = { isInputViewShown },
            attachInputView = { view -> setInputView(view) },
            setCandidatesViewShown = { shown -> setCandidatesViewShown(shown) },
            requestShowInputView = { requestShowSelf(0) },
            refreshStatusBar = { refreshStatusBar() }
        )
        
        // Initialize keyboard layout
        loadKeyboardLayout()
        
        // Initialize nav mode mappings file if needed
        it.palsoftware.pastiera.SettingsManager.initializeNavModeMappingsFile(this)
        ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets, this))
        variationStateController = VariationStateController(VariationRepository.loadVariations(assets))
        
        // Load auto-correction rules
        AutoCorrector.loadCorrections(assets, this)
        
        // Register listener for SharedPreferences changes
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == "sym_mappings_custom") {
                Log.d(TAG, "SYM mappings page 1 changed, reloading...")
                // Reload SYM mappings for page 1
                altSymManager.reloadSymMappings()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "sym_mappings_page2_custom") {
                Log.d(TAG, "SYM mappings page 2 changed, reloading...")
                // Reload SYM mappings for page 2
                altSymManager.reloadSymMappings2()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key != null && (key.startsWith("auto_correct_custom_") || key == "auto_correct_enabled_languages")) {
                Log.d(TAG, "Auto-correction rules changed, reloading...")
                // Reload auto-corrections (including new custom languages)
                AutoCorrector.loadCorrections(assets, this)
            } else if (key == "nav_mode_mappings_updated") {
                Log.d(TAG, "Nav mode mappings changed, reloading...")
                // Reload nav mode key mappings
                reloadNavModeMappings()
            } else if (key == "keyboard_layout") {
                Log.d(TAG, "Keyboard layout changed, reloading...")
                // Reload keyboard layout
                loadKeyboardLayout()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        
        // Register broadcast receiver for speech recognition
        speechResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Broadcast receiver called - action: ${intent?.action}")
                if (intent?.action == SpeechRecognitionActivity.ACTION_SPEECH_RESULT) {
                    val text = intent.getStringExtra(SpeechRecognitionActivity.EXTRA_TEXT)
                    Log.d(TAG, "Broadcast received with text: $text")
                    if (text != null && text.isNotEmpty()) {
                        Log.d(TAG, "Received speech recognition result: $text")
                        
                        // Delay text insertion to give the system time to restore InputConnection
                        // after the speech recognition activity has closed.
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Try multiple times if InputConnection is not immediately available
                            var attempts = 0
                            val maxAttempts = 10
                            
                            fun tryInsertText() {
                                val inputConnection = currentInputConnection
                                if (inputConnection != null) {
                                    inputConnection.commitText(text, 1)
                                    Log.d(TAG, "Speech text inserted successfully: $text")
                                } else {
                                    attempts++
                                    if (attempts < maxAttempts) {
                                        Log.d(TAG, "InputConnection not available, attempt $attempts/$maxAttempts, retrying in 100ms...")
                                        Handler(Looper.getMainLooper()).postDelayed({ tryInsertText() }, 100)
                                    } else {
                                        Log.w(TAG, "InputConnection not available after $maxAttempts attempts, text not inserted: $text")
                                    }
                                }
                            }
                            
                            tryInsertText()
                        }, 300) // Wait 300ms before trying to insert text
                    }
                }
            }
        }
        
        val filter = IntentFilter(SpeechRecognitionActivity.ACTION_SPEECH_RESULT)
        
        // On Android 13+ (API 33+) we must specify whether the receiver is exported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speechResultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(speechResultReceiver, filter)
        }
        
        Log.d(TAG, "Broadcast receiver registered for: ${SpeechRecognitionActivity.ACTION_SPEECH_RESULT}")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Remove listener when service is destroyed
        prefsListener?.let {
            prefs.unregisterOnSharedPreferenceChangeListener(it)
        }
        
        // Unregister broadcast receiver
        speechResultReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering broadcast receiver", e)
            }
        }
        speechResultReceiver = null
        
    }

    override fun onCreateInputView(): View? = keyboardVisibilityController.onCreateInputView()

    /**
     * Creates the candidates view shown when the soft keyboard is disabled.
     * Uses a separate StatusBarController instance to provide identical functionality.
     */
    override fun onCreateCandidatesView(): View? = keyboardVisibilityController.onCreateCandidatesView()

    /**
     * Determines whether the input view (soft keyboard) should be shown.
     * Respects the system flag (e.g. "Mostra tastiera virtuale" off for tastiere fisiche):
     * when the system asks for candidate-only mode we hide the main status UI and
     * expose the slim candidates view (LED strip + SYM layout on demand).
     */
    override fun onEvaluateInputViewShown(): Boolean {
        val shouldShowInputView = super.onEvaluateInputViewShown()
        return keyboardVisibilityController.onEvaluateInputViewShown(shouldShowInputView)
    }

    /**
     * Computes the insets for the IME window.
     * This increases the "content" area to include the candidate view area,
     * allowing the application to shift upwards properly without the candidates view
     * covering system UI.
     */
    override fun onComputeInsets(outInsets: InputMethodService.Insets?) {
        super.onComputeInsets(outInsets)
        
        if (outInsets != null && !isFullscreenMode()) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets
        }
    }

    /**
     * Resets all modifier key states.
     * Called when leaving a field or closing/reopening the keyboard.
     * @param preserveNavMode If true, keeps Ctrl latch active when nav mode is enabled.
     */
    private fun resetModifierStates(preserveNavMode: Boolean = false) {
        modifierStateController.resetModifiers(
            preserveNavMode = preserveNavMode,
            onNavModeCancelled = { navModeController.cancelNotification() }
        )
        
        symLayoutController.reset()
        altSymManager.resetTransientState()
        deactivateVariations()
        refreshStatusBar()
    }
    
    /**
     * Forces creation and display of the input view.
     * Called when the first physical key is pressed.
     * Shows the keyboard if there is an active text field.
     * IMPORTANT: UI is never shown in nav mode.
     */
    private fun ensureInputViewCreated() {
        keyboardVisibilityController.ensureInputViewCreated()
    }
    /**
     * Aggiorna la status bar delegando al controller dedicato.
     */
    private fun updateStatusBarText() {
        val variationSnapshot = variationStateController.refreshFromCursor(
            currentInputConnection,
            shouldDisableSmartFeatures
        )
        
        val modifierSnapshot = modifierStateController.snapshot()
        val snapshot = StatusBarController.StatusSnapshot(
            capsLockEnabled = modifierSnapshot.capsLockEnabled,
            shiftPhysicallyPressed = modifierSnapshot.shiftPhysicallyPressed,
            shiftOneShot = modifierSnapshot.shiftOneShot,
            ctrlLatchActive = modifierSnapshot.ctrlLatchActive,
            ctrlPhysicallyPressed = modifierSnapshot.ctrlPhysicallyPressed,
            ctrlOneShot = modifierSnapshot.ctrlOneShot,
            ctrlLatchFromNavMode = modifierSnapshot.ctrlLatchFromNavMode,
            altLatchActive = modifierSnapshot.altLatchActive,
            altPhysicallyPressed = modifierSnapshot.altPhysicallyPressed,
            altOneShot = modifierSnapshot.altOneShot,
            symPage = symPage,
            variations = variationSnapshot.variations,
            lastInsertedChar = variationSnapshot.lastInsertedChar,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures
        )
        // Passa anche la mappa emoji quando SYM è attivo (solo pagina 1)
        val emojiMapText = symLayoutController.emojiMapText()
        // Passa le mappature SYM per la griglia emoji/caratteri
        val symMappings = symLayoutController.currentSymMappings()
        // Passa l'inputConnection per rendere i pulsanti clickabili
        val inputConnection = currentInputConnection
        candidatesBarController.updateStatusBars(snapshot, emojiMapText, inputConnection, symMappings)
    }
    
    /**
     * Disattiva le variazioni.
     */
    private fun deactivateVariations() {
        if (::variationStateController.isInitialized) {
            variationStateController.clear()
        }
    }
    

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        
        currentPackageName = info?.packageName
        
        updateInputContextState(info)
        val state = inputContextState
        val isEditable = state.isEditable
        val isReallyEditable = state.isReallyEditable
        isInputViewActive = isEditable
        
        if (restarting) {
            enforceSmartFeatureDisabledState()
        }
        
        if (info != null && isEditable) {
            info.inputType = info.inputType or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        
        if (isEditable && !restarting) {
            val autoShowKeyboardEnabled = SettingsManager.getAutoShowKeyboard(this)
            if (autoShowKeyboardEnabled && isReallyEditable) {
                if (!isInputViewShown && isInputViewActive) {
                    ensureInputViewCreated()
                }
            }
        }
        
        if (!restarting) {
            if (ctrlLatchFromNavMode && ctrlLatchActive) {
                val inputConnection = currentInputConnection
                val hasValidInputConnection = inputConnection != null
                
                if (isReallyEditable && hasValidInputConnection) {
                    navModeController.exitNavMode()
                    resetModifierStates(preserveNavMode = false)
                }
            } else if (isEditable || !ctrlLatchFromNavMode) {
                resetModifierStates(preserveNavMode = false)
            }
        }
        
        initializeInputContext(restarting)
        
        if (restarting && isEditable && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableSmartFeatures,
                autoCapitalizeState,
                onUpdateStatusBar = { updateStatusBarText() }
            )
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        updateInputContextState(info)
        initializeInputContext(restarting)
        
        val isEditable = inputContextState.isEditable
        
        if (restarting && isEditable && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableSmartFeatures,
                autoCapitalizeState,
                onUpdateStatusBar = { updateStatusBarText() }
            )
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        isInputViewActive = false
        inputContextState = InputContextState.EMPTY
        resetModifierStates(preserveNavMode = true)
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        isInputViewActive = false
        if (finishingInput) {
            resetModifierStates(preserveNavMode = true)
        }
    }
    
    override fun onWindowShown() {
        super.onWindowShown()
        updateStatusBarText()
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        resetModifierStates(preserveNavMode = true)
    }
    
    /**
     * Called when the cursor position or selection changes in the text field.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        if (!shouldDisableSmartFeatures) {
            val cursorPositionChanged = (oldSelStart != newSelStart) || (oldSelEnd != newSelEnd)
            if (cursorPositionChanged && newSelStart == newSelEnd) {
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
            }
        }
        
        AutoCapitalizeHelper.checkAutoCapitalizeOnSelectionChange(
            this,
            currentInputConnection,
            shouldDisableSmartFeatures,
            autoCapitalizeState,
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            onUpdateStatusBar = { updateStatusBarText() }
        )
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle long press even when the keyboard is hidden but we still have a valid InputConnection.
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            return super.onKeyLongPress(keyCode, event)
        }
        
        // If the keyboard is hidden but we have an InputConnection, reactivate it
        if (!isInputViewActive) {
            isInputViewActive = true
            if (!isInputViewShown) {
                ensureInputViewCreated()
            }
        }
        
        // Intercept long presses BEFORE Android handles them
        if (altSymManager.hasAltMapping(keyCode)) {
            // Consumiamo l'evento per evitare il popup di Android
            return true
        }
        
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Check if we have an editable field at the very start
        val info = currentInputEditorInfo
        val initialInputConnection = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = initialInputConnection != null && inputType != EditorInfo.TYPE_NULL
        if (hasEditableField && !isInputViewActive) {
            isInputViewActive = true
        }
        
        // If NO editable field is active, handle ONLY nav mode
        if (!hasEditableField) {
            return inputEventRouter.handleKeyDownWithNoEditableField(
                keyCode = keyCode,
                event = event,
                ctrlKeyMap = ctrlKeyMap,
                callbacks = InputEventRouter.NoEditableFieldCallbacks(
                    isAlphabeticKey = { code -> isAlphabeticKey(code) },
                    isLauncherPackage = { pkg -> isLauncher(pkg) },
                    handleLauncherShortcut = { key -> handleLauncherShortcut(key) },
                    callSuper = { super.onKeyDown(keyCode, event) },
                    currentInputConnection = { currentInputConnection }
                ),
                ctrlLatchActive = ctrlLatchActive,
                editorInfo = info,
                currentPackageName = currentPackageName
            )
        }
        
        val routingResult = inputEventRouter.handleEditableFieldKeyDownPrelude(
            keyCode = keyCode,
            params = InputEventRouter.EditableFieldKeyDownParams(
                ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                ctrlLatchActive = ctrlLatchActive,
                isInputViewActive = isInputViewActive,
                isInputViewShown = isInputViewShown,
                hasInputConnection = initialInputConnection != null
            ),
            callbacks = InputEventRouter.EditableFieldKeyDownCallbacks(
                exitNavMode = { navModeController.exitNavMode() },
                ensureInputViewCreated = { keyboardVisibilityController.ensureInputViewCreated() },
                callSuper = { super.onKeyDown(keyCode, event) }
            )
        )
        when (routingResult) {
            InputEventRouter.EditableFieldRoutingResult.Consume -> return true
            InputEventRouter.EditableFieldRoutingResult.CallSuper -> return super.onKeyDown(keyCode, event)
            InputEventRouter.EditableFieldRoutingResult.Continue -> {}
        }
        
        val ic = currentInputConnection
        
        // Continue with normal IME logic
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
        if (!isInputViewShown && isInputViewActive) {
            ensureInputViewCreated()
        }
        
        val isAutoCorrectEnabled = SettingsManager.getAutoCorrectEnabled(this) && !shouldDisableSmartFeatures
        if (
            inputEventRouter.handleTextInputPipeline(
                keyCode = keyCode,
                event = event,
                inputConnection = ic,
                shouldDisableSmartFeatures = shouldDisableSmartFeatures,
                isAutoCorrectEnabled = isAutoCorrectEnabled,
                textInputController = textInputController,
                autoCorrectionManager = autoCorrectionManager
            ) { updateStatusBarText() }
        ) {
            return true
        }
        
        // Handle double-tap Shift to toggle Caps Lock
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (!shiftPressed) {
                val result = modifierStateController.handleShiftKeyDown(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                } else if (result.shouldRefreshStatusBar) {
                    refreshStatusBar()
                }
            }
            return super.onKeyDown(keyCode, event)
        }
        
        // Handle double-tap Ctrl to toggle Ctrl latch
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (!ctrlPressed) {
                val result = modifierStateController.handleCtrlKeyDown(
                    keyCode,
                    isInputViewActive,
                    onNavModeDeactivated = {
                        navModeController.cancelNotification()
                    }
                )
                if (result.shouldConsume) {
                    if (result.shouldUpdateStatusBar) {
                        updateStatusBarText()
                    }
                    return true
                } else if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyDown(keyCode, event)
        }
        
        // Handle double-tap Alt to toggle Alt latch
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (symLayoutController.isSymActive()) {
                if (symLayoutController.closeSymPage()) {
                    updateStatusBarText()
                }
            }
            if (!altPressed) {
                val result = modifierStateController.handleAltKeyDown(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return true  // Consume Alt event to prevent Android symbol picker popup
        }
        
        // Handle SYM key (toggle/latch with 3 states: disabled -> page1 -> page2 -> disabled)
        if (keyCode == KEYCODE_SYM) {
            symLayoutController.toggleSymPage()
            updateStatusBarText()
            // Consume the event to prevent Android from handling it
            return true
        }
        
        // Handle keycode 322 to delete the last word (swipe to delete)
        if (keyCode == 322) {
            val swipeToDeleteEnabled = SettingsManager.getSwipeToDelete(this)
                if (swipeToDeleteEnabled) {
                if (TextSelectionHelper.deleteLastWord(ic)) {
                    // Consumiamo l'evento
                    return true
                }
                    } else {
                // Feature disabled, still consume the event to avoid unwanted behavior
                return true
            }
        }
        
        // If the key is already pressed, consume the event to avoid repeats and popups
        if (altSymManager.hasPendingPress(keyCode)) {
            return true
        }
        
        if (
            inputEventRouter.handleNumericAndSym(
                keyCode = keyCode,
                event = event,
                inputConnection = ic,
                isNumericField = isNumericField,
                altSymManager = altSymManager,
                symLayoutController = symLayoutController,
                ctrlLatchActive = ctrlLatchActive,
                ctrlOneShot = ctrlOneShot,
                altLatchActive = altLatchActive,
                cursorUpdateDelayMs = CURSOR_UPDATE_DELAY,
                updateStatusBar = { updateStatusBarText() },
                callSuper = { super.onKeyDown(keyCode, event) }
            )
        ) {
            return true
        }
        
        // If Alt is pressed or Alt latch / Alt one-shot are active, handle Alt+key combination
        // Alt has priority over Ctrl
        if (event?.isAltPressed == true || altLatchActive || altOneShot) {
            altSymManager.cancelPendingLongPress(keyCode)
            if (altOneShot) {
                altOneShot = false
                refreshStatusBar()
            }
            
            // Eccezione per Back: chiude sempre la tastiera anche con Alt premuto
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // Lascia passare Back anche con Alt premuto per chiudere la tastiera
                return super.onKeyDown(keyCode, event)
            }
            
            if (
                inputEventRouter.handleAltModifiedKey(
                    keyCode = keyCode,
                    event = event,
                    inputConnection = ic,
                    altSymManager = altSymManager,
                    updateStatusBar = { updateStatusBarText() },
                    callSuperWithKey = { defaultKeyCode, defaultEvent ->
                        super.onKeyDown(defaultKeyCode, defaultEvent)
                    }
                )
            ) {
                return true
            }
        }
        
        // Handle Ctrl+key shortcuts (checks both physical Ctrl, Ctrl latch and one-shot).
        // IMPORTANT: If we are in nav mode (ctrlLatchFromNavMode), Ctrl latch MUST NOT be disabled here.
        // BUT: in a text field, nav mode is already disabled, so treat Ctrl latch as normal.
        if (event?.isCtrlPressed == true || ctrlLatchActive || ctrlOneShot) {
            if (
                inputEventRouter.handleCtrlModifiedKey(
                    keyCode = keyCode,
                    event = event,
                    inputConnection = ic,
                    ctrlKeyMap = ctrlKeyMap,
                    ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                    ctrlOneShot = ctrlOneShot,
                    clearCtrlOneShot = { ctrlOneShot = false },
                    updateStatusBar = { updateStatusBarText() },
                    callSuper = { super.onKeyDown(keyCode, event) }
                )
            ) {
                return true
            }
        }
        
        // Check whether this key has an Alt mapping or should support long press with Shift.
        // If it does, handle it with long press (even when shiftOneShot is active).
        // This avoids inserting the normal character when the user intends a long press.
        val useShift = SettingsManager.isLongPressShift(this)
        val hasLongPressSupport = if (useShift) {
            // With Shift, support long press for any letter key
            event != null && event.unicodeChar != 0 && event.unicodeChar.toChar().isLetter()
        } else {
            // With Alt, only keys with Alt mapping
            altSymManager.hasAltMapping(keyCode)
        }
        
        if (hasLongPressSupport) {
            // Standard long-press handling (numeric fields were already handled earlier)
            val wasShiftOneShot = shiftOneShot
            // Get character from layout for conversion
            val layoutChar = getCharacterFromLayout(keyCode, event, event?.isShiftPressed == true)
            altSymManager.handleKeyWithAltMapping(
                keyCode,
                event,
                capsLockEnabled,
                ic,
                shiftOneShot,
                layoutChar
            )
            // Disable shiftOneShot after handling a key with Alt mapping,
            // so that it does not stay active for the next key.
            if (wasShiftOneShot) {
                shiftOneShot = false
                shiftOneShotEnabledTime = 0
                // Sync to shiftState
                modifierStateController.syncShiftOneShotToShiftState()
                updateStatusBarText()
            }
            return true
        }
        
        // Handle Shift one-shot for keys without Alt mapping
        if (shiftOneShot) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = true
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                // Disable shift one-shot (used when letter is typed)
                shiftOneShot = false
                shiftOneShotEnabledTime = 0
                // Sync to shiftState
                modifierStateController.syncShiftOneShotToShiftState()
                ic.commitText(char, 1)
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
                return true
            }
        }
        
        // When there is no mapping, handle Caps Lock for regular characters.
        // Apply Caps Lock to alphabetical characters.
        if (capsLockEnabled && LayoutMappingRepository.isMapped(keyCode)) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = false
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                ic.commitText(char, 1)
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
                return true
            }
        }
        
        // When there is no mapping, check whether the character has variations.
        // If it does, handle it ourselves so we can show variation suggestions.
        val charForVariations = if (LayoutMappingRepository.isMapped(keyCode)) {
            LayoutMappingRepository.getCharacterWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = shiftOneShot
            )
        } else {
            getCharacterFromLayout(keyCode, event, event?.isShiftPressed == true)
        }
        if (charForVariations != null) {
            // Check whether the character has variations
            if (variationStateController.hasVariationsFor(charForVariations)) {
                // Insert the character ourselves so we can show variations
                ic.commitText(charForVariations.toString(), 1)
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
                return true
            }
            // If the character has no variations, previous variations remain visible
            // (display only, no action)
        }
        
        // Convert all alphabetic characters using the selected keyboard layout
        // This ensures that layout conversion works even when no special modifiers are active
        // Check if this is an alphabetic key that should be converted
        val isAlphabeticKey = isAlphabeticKey(keyCode)
        if (isAlphabeticKey && LayoutMappingRepository.isMapped(keyCode)) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = shiftOneShot
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                // Insert the converted character
                ic.commitText(char, 1)
                
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
                
                // Consume the event to prevent Android from handling it
                return true
            }
        }
        
        // When there is no mapping, let Android handle the event normally.
        // Variations will be updated automatically by onUpdateSelection() after Android completes the insertion.
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Check if we have an editable field at the start (same logic as onKeyDown)
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = ic != null && inputType != EditorInfo.TYPE_NULL
        
        // If NO editable field is active, handle ONLY nav mode Ctrl release
        if (!hasEditableField) {
            return inputEventRouter.handleKeyUpWithNoEditableField(
                keyCode = keyCode,
                event = event,
                ctrlKeyMap = ctrlKeyMap,
                callbacks = InputEventRouter.NoEditableFieldCallbacks(
                    isAlphabeticKey = { code -> isAlphabeticKey(code) },
                    isLauncherPackage = { pkg -> isLauncher(pkg) },
                    handleLauncherShortcut = { key -> handleLauncherShortcut(key) },
                    callSuper = { super.onKeyUp(keyCode, event) },
                    currentInputConnection = { currentInputConnection }
                )
            )
        }
        
        // Continue with normal IME logic for text fields
        val inputConnection = currentInputConnection ?: return super.onKeyUp(keyCode, event)
        
        // Always notify the tracker (even when the event is consumed)
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_UP")
        
        // Handle Shift release for double-tap
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (shiftPressed) {
                val result = modifierStateController.handleShiftKeyUp(keyCode)
                // Shift one-shot remains active until used (when letter is typed)
                modifierStateController.syncShiftOneShotFromShiftState()
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Ctrl release for double-tap
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (ctrlPressed) {
                val result = modifierStateController.handleCtrlKeyUp(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Alt release for double-tap
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (altPressed) {
                val result = modifierStateController.handleAltKeyUp(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle SYM key release (nothing to do; it is a toggle)
        if (keyCode == KEYCODE_SYM) {
            // Consumiamo l'evento
            return true
        }
        
        if (symLayoutController.handleKeyUp(keyCode, shiftPressed)) {
            return true
        }
        
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Aggiunge una nuova mappatura Alt+tasto -> carattere.
     */
    fun addAltKeyMapping(keyCode: Int, character: String) {
        altSymManager.addAltKeyMapping(keyCode, character)
    }

    /**
     * Rimuove una mappatura Alt+tasto esistente.
     */
    fun removeAltKeyMapping(keyCode: Int) {
        altSymManager.removeAltKeyMapping(keyCode)
    }
    
    /**
     * Intercepts trackpad/touch-sensitive keyboard motion events.
     * The Unihertz Titan 2 keyboard can act as a trackpad, sending MotionEvents
     * for scrolling, cursor movement, and gestures.
     */
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onGenericMotionEvent(event)
        }
        
        // Check if this is a trackpad/touch event from the keyboard
        val source = event.source
        val isFromTrackpad = (source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                            (source and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
        
        // Also check if it's from a keyboard device (touch-sensitive keyboard)
        val device = event.device
        val isFromKeyboard = device != null && 
                            ((source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD ||
                             device.name?.contains("keyboard", ignoreCase = true) == true ||
                             device.name?.contains("titan", ignoreCase = true) == true)
        
        if (isFromTrackpad || isFromKeyboard) {
            Log.d(TAG, "Motion event intercepted - Action: ${MotionEventTracker.getActionName(event.action)}, " +
                    "Source: ${MotionEventTracker.getSourceName(source)}, " +
                    "Device: ${device?.name}, " +
                    "X: ${event.x}, Y: ${event.y}, " +
                    "ScrollX: ${event.getAxisValue(MotionEvent.AXIS_HSCROLL)}, " +
                    "ScrollY: ${event.getAxisValue(MotionEvent.AXIS_VSCROLL)}")
            
            // Notify the tracker for debug display
            MotionEventTracker.notifyMotionEvent(event)
            
            // Handle different motion event types
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    val scrollX = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                    val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    Log.d(TAG, "Trackpad scroll detected - X: $scrollX, Y: $scrollY")
                    
                    // You can handle scroll events here
                    // For example, convert to cursor movement or scroll actions
                    // return true to consume the event, false to pass it to Android
                }
                MotionEvent.ACTION_MOVE -> {
                    Log.d(TAG, "Trackpad move detected - X: ${event.x}, Y: ${event.y}")
                    // Handle cursor movement
                }
                MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "Trackpad touch down detected - X: ${event.x}, Y: ${event.y}")
                    // Handle touch down (click)
                }
                MotionEvent.ACTION_UP -> {
                    Log.d(TAG, "Trackpad touch up detected")
                    // Handle touch up (release)
                }
            }
            
            // Return true to consume the event, false to let Android handle it
            // For now, we'll let Android handle it but log everything for debugging
            return false
        }
        
        return super.onGenericMotionEvent(event)
    }
}


# Pastiera IME Refactoring Log

Questo documento traccia le fasi del refactoring modulare dell’IME fisica di Pastiera, con l’obiettivo di ridurre il “god class” `PhysicalKeyboardInputMethodService`, migliorare il riuso e facilitare futuri test automatizzati. Ogni fase è stata pensata per essere behaviour-preserving e accompagnata da build/test lato utente.

---

## Fase 1 – Data/Repository Layer _(Completato)_
**Obiettivo:** isolare accesso a JSON/layout/variations dal servizio IME.

- **Nuove classi**:  
  - `data/layout/LayoutMappingRepository`, `JsonLayoutLoader`, `LayoutFileStore`  
  - `data/variation/VariationRepository`  
  - `data/mappings/KeyMappingLoader` (spostato fuori da `inputmethod`)
- **Principali cambiamenti**:  
  - `PhysicalKeyboardInputMethodService` ora carica layout, nav-mode mappings e variazioni tramite i repository (nessuna logica JSON nel servizio).  
  - `KeyboardLayoutSettingsScreen`, `NavModeSettingsScreen`, `SettingsManager` aggiornati a usare i nuovi moduli.  
  - `AltSymManager` e `AutoCorrector` continuano a funzionare ma consumano le nuove API.
- **Testing suggerito**: cambio layout, import/export JSON, long-press/auto-correct con keyboard fisica.

---

## Fase 2 – Modifier & Nav Controllers _(Completato)_
**Obiettivo:** centralizzare stato Shift/Ctrl/Alt e nav-mode per ridurre duplicazione e accoppiamento.

- **`core/ModifierStateController`**  
  - Mantiene gli stati `Shift/Ctrl/Alt` (pressed/latch/one-shot/physically pressed) e sincronizza automaticamente con `AutoCapitalizeHelper`.  
  - Espone snapshot per la Status Bar e funzioni `handleShift/Ctrl/AltKeyDown/Up`.

- **`core/NavModeController`**  
  - Incapsula NavModeHandler, latch state & notifiche.  
  - Decide se una key è nav-mode, gestisce DPAD mapping e conserva la latched-state tra servizi/UI.

- **`PhysicalKeyboardInputMethodService`**  
  - Ridotto ai wiring: delega key handling a `ModifierStateController` e `NavModeController`.  
  - Status bar aggiornata via snapshot, niente più accesso diretto ai campi `ShiftState/CtrlState`.

- **Testing suggerito**: double-tap shift/caps lock, ctrl latch + nav mode (fuori da text field), alt latch/one-shot, status bar LED.

---

## Fase 3 – SYM Layout Controller _(Completato)_
**Obiettivo:** isolare tutta la logica SYM (pagine emoji/simboli, auto-close, restore, UI data).

- **`core/SymLayoutController`**  
  - Gestisce `symPage`, persistenza, restore da `SettingsManager`, auto-close rules e snapshot per UI.  
  - Espone `handleKeyWhenActive` con `SymKeyResult` per distinguere `CONSUME`, `CALL_SUPER`, `NOT_HANDLED`.  
  - Fornisce `emojiMapText()` e `currentSymMappings()` per StatusBar/Candidates.

- **Priorità Alt/Ctrl (correzioni successive)**  
  - **Alt**: chiude sempre SYM all’attivazione per permettere agli Alt mappings di funzionare subito.  
  - **Ctrl**: bypassa la griglia SYM; con latch/pressed, i Ctrl shortcuts (es. DPAD, copy/paste) hanno precedenza, mantenendo SYM aperto finché Ctrl resta attivo.
  - Inserimento SYM via tastiera fisica chiude il layout quando auto-close è attivo; gli inserimenti via touchscreen lo lasciano aperto.

- **UI**: la Status Bar riceve ora `emojiMapText`/`symMappings` direttamente dal controller, mantenendo il layout LED coerente fra input e candidates view.

- **Testing suggerito**:  
  1. Cycle SYM (0→emoji→symbols) e verifica persistenza/restore.  
  2. Inserisci simboli via tastiera con Alt/Ctrl attivi: Alt deve chiudere SYM, Ctrl non deve inserire simboli.  
  3. Inserisci gli stessi simboli via touchscreen: SYM resta aperto.  
  4. Verifica auto-close su Back/Enter/Alt e dopo commit fisico (quando l’opzione è abilitata).

---

## Fase 4 – Text Pipeline Refactor _(Completato)_
**Obiettivo:** spostare auto-correct, double-space, auto-cap e undo fuori dal servizio e in controller dedicati.

- **`core/TextInputController`**  
  - Gestisce double-space→“. ”, auto-cap dopo punteggiatura e Enter; usa `ModifierStateController` per lo Shift one-shot e centralizza il timing (50 ms) nell’`AutoCapitalizeHelper`.
- **`core/AutoCorrectionManager`**  
  - Incapsula undo via Backspace, correzioni su spazio/punteggiatura e accettazione/clear dei reject su altri tasti.
- **`PhysicalKeyboardInputMethodService`**  
  - Deleghe per oltre 200 righe di logica text pipeline; ora richiama solo i metodi dei controller e aggiorna la status bar via callback.
- **Auto-cap delay**  
  - Il servizio non passa più `delayMs` personalizzati: la tempistica è centralizzata nell’helper (default 50 ms) così futuri tweak richiedono una modifica sola.

- **Testing suggerito**:
  1. Digita una parola autocorretta e premi Backspace subito → deve ripristinare la parola originale.
  2. Double-space produce “. ”, abilita Shift one-shot e non introduce ritardi percepibili.
  3. Auto-correction/accept/reset continua a funzionare su spazio/punteggiatura e altri tasti.
  4. Campi con smart features disabilitate ignorano tutte le funzioni sopra.

---

## Fasi successive (pianificate – **da fare**)
2. **SYM/Variation UI refinement**
   - Introdurre `CandidatesBarController` + `LedStatusBarView` per separare logic UI.
3. **Event Router**
   - `InputEventRouter` per orchestrare nav-mode vs text-input vs launcher shortcuts.
4. **Service Slim Down**
   - Rinominare/estrarre `PastieraImeService` con puro wiring/DI.

Ogni fase continuerà a essere accompagnata da `assembleDebug` e test manuali suggeriti per garantire parità funzionale.

---

## Checklist di regressione suggerita
- Cambio layout fisico, import/export JSON.
- Shift/Ctrl/Alt double-tap + status bar LED.
- Nav mode (fuori campo testo) con DPAD + ritorno in campo testo.
- SYM: toggle, auto-close, Alt priority, Ctrl shortcuts while SYM is open.
- Long-press Alt/SYM e Alt+Space.
- Inserimento touch vs fisico per emoji/simboli.

Con questa struttura modulare, le prossime fasi potranno concentrarsi su UI e text pipeline senza toccare nuovamente il servizio principale, riducendo il rischio di regressioni.


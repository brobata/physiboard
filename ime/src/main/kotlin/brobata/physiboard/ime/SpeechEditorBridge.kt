package brobata.physiboard.ime

import android.view.inputmethod.InputConnection
import brobata.physiboard.core.speech.DictationTextOp

/**
 * Applies the [DictationTextOp]s `:core:speech` returns to a real `InputConnection`, the same way
 * [applyEditorOps] already applies `:core:text`'s [brobata.physiboard.core.text.EditorOp]s: one
 * batch edit, in the order the pure module decided. Kept as its own small vocabulary rather than
 * folded into [EditorOp] because dictation's one extra need, composing text that keeps replacing
 * itself while the engine is still listening, has no counterpart in the ordinary typing pipeline
 * (see [DictationTextOp]'s own KDoc).
 */
internal fun InputConnection.applyDictationTextOps(ops: List<DictationTextOp>) {
    if (ops.isEmpty()) return
    beginBatchEdit()
    try {
        for (op in ops) {
            when (op) {
                is DictationTextOp.SetComposingText -> setComposingText(op.text, 1)
                DictationTextOp.FinishComposing -> finishComposingText()
                is DictationTextOp.CommitText -> commitText(op.text, 1)
            }
        }
    } finally {
        endBatchEdit()
    }
}

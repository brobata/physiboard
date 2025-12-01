package it.palsoftware.pastiera.inputmethod

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import it.palsoftware.pastiera.R

/**
 * Helper activity for handling speech recognition.
 * Receives the result and sends it via broadcast to the input method service.
 */
class SpeechRecognitionActivity : Activity() {
    companion object {
        private const val TAG = "SpeechRecognition"
        const val REQUEST_CODE_SPEECH = 1000
        const val ACTION_SPEECH_RESULT = "it.palsoftware.pastiera.SPEECH_RESULT"
        const val EXTRA_TEXT = "text"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SpeechRecognitionActivity onCreate - starting speech recognition")
        
        try {
            // Google Voice Typing package (may vary on different devices)
            val GOOGLE_VOICE_TYPING_PACKAGES = listOf(
                "com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch"
            )
            
            var intent: Intent? = null
            
            // Try Google Voice Typing first
            for (packageName in GOOGLE_VOICE_TYPING_PACKAGES) {
                intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    setPackage(packageName)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_recognition_prompt))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                
                if (intent.resolveActivity(packageManager) != null) {
                    Log.d(TAG, "Using Google Voice Typing: $packageName")
                    break
                } else {
                    intent = null
                }
            }
            
            // Fallback: use generic speech recognition
            if (intent == null) {
                Log.d(TAG, "Google Voice Typing not available, using generic recognition")
                intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_recognition_prompt))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                
                if (intent.resolveActivity(packageManager) == null) {
                    Log.e(TAG, "Speech recognition not available")
                    finish()
                    return
                }
            }
            
            startActivityForResult(intent, REQUEST_CODE_SPEECH)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching speech recognition", e)
            finish()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SPEECH) {
            Log.i(TAG, "onActivityResult called - requestCode: $requestCode, resultCode: $resultCode")
            
            if (resultCode == RESULT_OK && data != null) {
                // Debug: log all extras to see what Google Voice Typing returns
                Log.i(TAG, "=== All extras in speech recognition result ===")
                data.extras?.let { bundle ->
                    if (bundle.isEmpty) {
                        Log.i(TAG, "Bundle is empty")
                    } else {
                        Log.i(TAG, "Bundle contains ${bundle.keySet().size} keys")
                        for (key in bundle.keySet()) {
                            val value = bundle.get(key)
                            val valueType = value?.javaClass?.simpleName ?: "null"
                            val valueString = when (value) {
                                is ArrayList<*> -> {
                                    val list = value as ArrayList<*>
                                    "ArrayList(${list.size} items): ${list.joinToString(", ") { "'$it'" }}"
                                }
                                is Bundle -> "Bundle(${value.keySet().size} keys)"
                                else -> value.toString()
                            }
                            Log.i(TAG, "  Extra: '$key' = $valueString (type: $valueType)")
                        }
                    }
                } ?: Log.w(TAG, "Bundle is null")
                
                val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                Log.i(TAG, "EXTRA_RESULTS: ${if (results == null) "null" else "ArrayList(${results.size} items)"}")
                results?.forEachIndexed { index, result ->
                    Log.i(TAG, "  Result[$index]: '$result'")
                }
                
                // Check for other known RecognizerIntent extras
                val confidenceScores = data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES)
                if (confidenceScores != null) {
                    Log.i(TAG, "EXTRA_CONFIDENCE_SCORES: ${confidenceScores.joinToString(", ")}")
                }
                
                // Also check all extras directly from data
                Log.i(TAG, "=== Checking data.extras directly ===")
                data.extras?.let { bundle ->
                    for (key in bundle.keySet()) {
                        Log.i(TAG, "  data.extras['$key'] = ${bundle.get(key)}")
                    }
                }
                
                val spokenText = results?.get(0) ?: ""
                
                if (spokenText.isNotEmpty()) {
                    Log.i(TAG, "Using recognized text: '$spokenText'")
                    
                    // Send result via broadcast with explicit package
                    val broadcastIntent = Intent(ACTION_SPEECH_RESULT).apply {
                        putExtra(EXTRA_TEXT, spokenText)
                        setPackage(packageName) // Specify package explicitly
                    }
                    sendBroadcast(broadcastIntent)
                    Log.i(TAG, "Broadcast sent: $ACTION_SPEECH_RESULT with text: $spokenText")
                } else {
                    Log.w(TAG, "No text recognized (empty result)")
                }
            } else {
                Log.w(TAG, "Speech recognition cancelled or failed (resultCode: $resultCode, data: ${if (data == null) "null" else "not null"})")
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                finishAndRemoveTask()
            } catch (e: Exception) {
                finish()
            }
        } else {
            finish()
        }
    }
}


package brobata.physiboard.ime.actions

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import brobata.physiboard.core.actions.feedback.SoundGroup
import brobata.physiboard.core.actions.feedback.TypingSoundMode
import brobata.physiboard.core.actions.feedback.TypingSounds
import brobata.physiboard.core.settings.TypingSoundOutputMode
import brobata.physiboard.ime.R
import kotlin.random.Random

/**
 * Plays one of [TypingSounds]' pack files on a key down, the Android-only half of
 * `TypingFeedback.kt`'s pure rules.
 *
 * spec: expansion-clipboard-pickers-launcher.md SS9.1: "One file of the group is picked at random,
 * played at a random volume ... and a random rate ..., at most 8 overlapping streams. Files that
 * have not finished loading are skipped." This build ships exactly one file per group (see
 * `tools/typing_sounds/generate_sounds.py`), so "picked at random" degenerates to the one file
 * `TypingSounds.resourceName(mode, group, index = 1)` names; the random volume and rate still
 * apply on every play, so repeated keys do not all sound identical.
 *
 * A pool is rebuilt (via a fresh [TypingSoundPlayer] instance, see [KeyboardSession.applySettings])
 * whenever `typing_sound_mode` or `typing_sound_output_mode` changes, matching SS9.1's "rebuilt
 * whenever any of ... changes"; [release] matches "released when the service is destroyed".
 */
internal class TypingSoundPlayer(context: Context, private val mode: TypingSoundMode, outputMode: TypingSoundOutputMode) {

    private val pool: SoundPool? = if (mode == TypingSoundMode.OFF) null else SoundPool.Builder()
        .setMaxStreams(TypingSounds.MAX_STREAMS)
        .setAudioAttributes(attributesFor(outputMode))
        .build()

    /** Resource name to its loaded sound id; a name not yet in here (or never loaded) is skipped, per SS9.1. */
    private val loadedIds = mutableMapOf<String, Int>()
    private val loadingIds = mutableSetOf<Int>()

    init {
        val soundPool = pool
        if (soundPool != null) {
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                loadingIds.remove(sampleId)
                if (status != 0) Log.w(TAG, "sound $sampleId failed to load, status=$status")
            }
            for (group in SoundGroup.entries) {
                val name = TypingSounds.resourceName(mode, group, index = 1) ?: continue
                val resId = RESOURCE_IDS[name] ?: continue
                val soundId = runCatching { soundPool.load(context, resId, 1) }.getOrNull() ?: continue
                loadedIds[name] = soundId
                loadingIds += soundId
            }
        }
    }

    /** Plays [group]'s file for the pack this player was built with, or does nothing (off, unresolved, still loading). */
    fun play(group: SoundGroup) {
        val soundPool = pool ?: return
        val name = TypingSounds.resourceName(mode, group, index = 1) ?: return
        val soundId = loadedIds[name] ?: return
        if (soundId in loadingIds) return
        val volume = TypingSounds.MIN_VOLUME + Random.nextFloat() * (TypingSounds.MAX_VOLUME - TypingSounds.MIN_VOLUME)
        val rate = TypingSounds.MIN_RATE + Random.nextFloat() * (TypingSounds.MAX_RATE - TypingSounds.MIN_RATE)
        runCatching { soundPool.play(soundId, volume, volume, 1, 0, rate) }
    }

    fun release() {
        pool?.release()
    }

    private fun attributesFor(outputMode: TypingSoundOutputMode): AudioAttributes = AudioAttributes.Builder()
        .setUsage(
            when (outputMode) {
                TypingSoundOutputMode.MEDIA -> AudioAttributes.USAGE_MEDIA
                TypingSoundOutputMode.SYSTEM -> AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                TypingSoundOutputMode.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION_EVENT
            },
        )
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private companion object {
        const val TAG = "PhysiBoardTypingSound"

        /**
         * Every raw resource `tools/typing_sounds/generate_sounds.py` writes, keyed by the exact
         * name [TypingSounds.resourceName] builds. Only index 1 exists per group (this pass's
         * scope reduction, see the class KDoc), so this is the whole catalogue.
         */
        val RESOURCE_IDS: Map<String, Int> = mapOf(
            "typing_click_normal_1" to R.raw.typing_click_normal_1,
            "typing_click_space_1" to R.raw.typing_click_space_1,
            "typing_click_backspace_1" to R.raw.typing_click_backspace_1,
            "typing_click_enter_1" to R.raw.typing_click_enter_1,
            "typing_click_modifier_1" to R.raw.typing_click_modifier_1,
            "typing_typewriter_normal_1" to R.raw.typing_typewriter_normal_1,
            "typing_typewriter_space_1" to R.raw.typing_typewriter_space_1,
            "typing_typewriter_backspace_1" to R.raw.typing_typewriter_backspace_1,
            "typing_typewriter_enter_1" to R.raw.typing_typewriter_enter_1,
            "typing_typewriter_modifier_1" to R.raw.typing_typewriter_modifier_1,
        )
    }
}

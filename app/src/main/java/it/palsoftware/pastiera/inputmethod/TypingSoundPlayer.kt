package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import kotlin.random.Random

class TypingSoundPlayer(private val context: Context) {
    companion object {
        private const val MAX_STREAMS = 8
        private const val NORMAL_POOL_SIZE = 24
        private const val SPECIAL_POOL_SIZE = 5
    }

    private var soundPool: SoundPool? = null
    private var soundIdsByGroup: Map<KeySoundGroup, List<Int>> = emptyMap()
    private var customSoundIdsByGroup: Map<KeySoundGroup, List<Int>> = emptyMap()
    private var loadedSoundIds: Set<Int> = emptySet()
    private var activeMode: String = SettingsManager.TYPING_SOUND_MODE_OFF

    fun reload() {
        release()

        activeMode = SettingsManager.getTypingSoundMode(context)
        if (activeMode == SettingsManager.TYPING_SOUND_MODE_OFF) {
            return
        }

        val pool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(resolveAudioUsage())
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        soundPool = pool
        loadedSoundIds = emptySet()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSoundIds = loadedSoundIds + sampleId
            }
        }

        when (activeMode) {
            SettingsManager.TYPING_SOUND_MODE_CLICK -> {
                soundIdsByGroup = loadBuiltInPools(pool, "typing_click")
            }
            SettingsManager.TYPING_SOUND_MODE_TYPEWRITER -> {
                soundIdsByGroup = loadBuiltInPools(pool, "typing_typewriter")
            }
            SettingsManager.TYPING_SOUND_MODE_CUSTOM -> {
                customSoundIdsByGroup = loadCustomPools(pool)
            }
        }
    }

    fun play(keyCode: Int) {
        val pool = soundPool ?: return
        val candidateIds = if (activeMode == SettingsManager.TYPING_SOUND_MODE_CUSTOM) {
            customSoundsForGroup(groupForKeyCode(keyCode))
        } else {
            soundIdsByGroup[groupForKeyCode(keyCode)].orEmpty()
        }
        val availableIds = candidateIds.filter { it in loadedSoundIds }
        if (availableIds.isEmpty()) {
            return
        }

        val soundId = availableIds.random(Random.Default)
        val volume = Random.nextDouble(0.82, 1.0).toFloat()
        val rate = Random.nextDouble(0.965, 1.035).toFloat()
        pool.play(soundId, volume, volume, 1, 0, rate)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundIdsByGroup = emptyMap()
        customSoundIdsByGroup = emptyMap()
        loadedSoundIds = emptySet()
    }

    private fun resolveAudioUsage(): Int {
        return when (SettingsManager.getTypingSoundOutputMode(context)) {
            SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM -> AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
            SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
            else -> AudioAttributes.USAGE_MEDIA
        }
    }

    private fun loadBuiltInPools(pool: SoundPool, prefix: String): Map<KeySoundGroup, List<Int>> {
        return KeySoundGroup.values().associateWith { group ->
            val count = if (group == KeySoundGroup.Normal) NORMAL_POOL_SIZE else SPECIAL_POOL_SIZE
            (1..count).mapNotNull { index ->
                val resourceId = context.resources.getIdentifier(
                    "${prefix}_${group.resourceName}_$index",
                    "raw",
                    context.packageName
                )
                if (resourceId == 0) null else pool.load(context, resourceId, 1)
            }
        }
    }

    private fun loadCustomPools(pool: SoundPool): Map<KeySoundGroup, List<Int>> {
        val filesByGroup = SettingsManager.getTypingSoundCustomGroupFiles(context)
        return KeySoundGroup.values().associateWith { group ->
            filesByGroup[group.resourceName].orEmpty().map { file ->
                pool.load(file.absolutePath, 1)
            }
        }
    }

    private fun customSoundsForGroup(group: KeySoundGroup): List<Int> {
        return customSoundIdsByGroup[group]
            .orEmpty()
            .ifEmpty { customSoundIdsByGroup[KeySoundGroup.Normal].orEmpty() }
    }

    private fun groupForKeyCode(keyCode: Int): KeySoundGroup {
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> KeySoundGroup.Space
            KeyEvent.KEYCODE_DEL -> KeySoundGroup.Backspace
            KeyEvent.KEYCODE_ENTER -> KeySoundGroup.Enter
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_SYM -> KeySoundGroup.Modifier
            else -> KeySoundGroup.Normal
        }
    }

    private enum class KeySoundGroup(val resourceName: String) {
        Normal("normal"),
        Space("space"),
        Backspace("backspace"),
        Enter("enter"),
        Modifier("modifier")
    }
}

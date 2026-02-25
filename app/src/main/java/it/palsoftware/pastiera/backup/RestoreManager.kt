package it.palsoftware.pastiera.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import it.palsoftware.pastiera.AppBroadcastActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RestoreManager {
    private const val TAG = "RestoreManager"
    private const val USER_DICTIONARY_PREF_KEY = "pastiera_prefs:user_dictionary_entries"
    private const val USER_DEFAULTS_FILE_NAME = "user_defaults.json"

    enum class PostRestoreAction {
        REFRESH_USER_DICTIONARY
    }

    private data class PostRestoreTriggerRule(
        val action: PostRestoreAction,
        val matchingAppliedPrefKeys: Set<String> = emptySet(),
        val matchingRestoredFileNames: Set<String> = emptySet()
    )

    private val postRestoreTriggerRules = listOf(
        PostRestoreTriggerRule(
            action = PostRestoreAction.REFRESH_USER_DICTIONARY,
            matchingAppliedPrefKeys = setOf(USER_DICTIONARY_PREF_KEY),
            matchingRestoredFileNames = setOf(USER_DEFAULTS_FILE_NAME)
        )
    )

    suspend fun restore(context: Context, sourceUri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val workingDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
        val extractedDir = File(workingDir, "unzipped").apply { mkdirs() }

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipHelper.unzip(input, extractedDir)
            } ?: return@withContext RestoreResult.Failure("Unable to open source backup")

            val metadata = BackupMetadata.fromFile(File(extractedDir, "backup_meta.json"))
            val prefsDir = File(extractedDir, "prefs")
            val filesDir = File(extractedDir, "files")

            val prefsData = PreferencesBackupHelper.readPreferencesFromBackup(prefsDir)
            val fileSummary = FileBackupHelper.restoreFiles(context, filesDir)
            val prefsSummary = PreferencesBackupHelper.restorePreferences(context, prefsData)
            val postRestoreActions = collectTriggeredPostRestoreActions(prefsSummary, fileSummary)
            notifyPostRestoreEffects(context, postRestoreActions)

            RestoreResult.Success(
                metadata = metadata,
                preferencesSummary = prefsSummary,
                fileSummary = fileSummary,
                postActionsTriggered = postRestoreActions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            RestoreResult.Failure(e.message ?: "Restore failed")
        } finally {
            extractedDir.deleteRecursively()
            workingDir.deleteRecursively()
        }
    }

    internal fun shouldNotifyUserDictionaryRefresh(
        preferencesSummary: PreferencesRestoreSummary,
        fileSummary: FileRestoreSummary
    ): Boolean {
        return collectTriggeredPostRestoreActions(preferencesSummary, fileSummary)
            .contains(PostRestoreAction.REFRESH_USER_DICTIONARY)
    }

    internal fun collectTriggeredPostRestoreActions(
        preferencesSummary: PreferencesRestoreSummary,
        fileSummary: FileRestoreSummary
    ): Set<PostRestoreAction> {
        val appliedPrefKeys = preferencesSummary.appliedKeys.toSet()
        val restoredFiles = fileSummary.restoredFiles

        return postRestoreTriggerRules.mapNotNullTo(linkedSetOf()) { rule ->
            val prefMatch = rule.matchingAppliedPrefKeys.any(appliedPrefKeys::contains)
            val fileMatch = rule.matchingRestoredFileNames.any { fileName ->
                restoredFiles.any { restored ->
                    restored == fileName || restored.endsWith("/$fileName")
                }
            }
            if (prefMatch || fileMatch) rule.action else null
        }
    }

    private fun notifyPostRestoreEffects(
        context: Context,
        actions: Set<PostRestoreAction>
    ) {
        Log.i(TAG, "Restore post-actions triggered: $actions")
        if (actions.isEmpty()) return

        actions.forEach { action ->
            when (action) {
                PostRestoreAction.REFRESH_USER_DICTIONARY -> {
                    val intent = Intent(AppBroadcastActions.USER_DICTIONARY_UPDATED).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                    Log.i(TAG, "Sent user dictionary refresh broadcast after restore")
                }
            }
        }
    }
}

sealed class RestoreResult {
    data class Success(
        val metadata: BackupMetadata?,
        val preferencesSummary: PreferencesRestoreSummary,
        val fileSummary: FileRestoreSummary,
        val postActionsTriggered: Set<RestoreManager.PostRestoreAction>
    ) : RestoreResult()

    data class Failure(val reason: String) : RestoreResult()
}

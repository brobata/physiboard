package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages custom keyboard layout files on device storage.
 * Provides functions to load, save, list, and delete custom layouts.
 * Layouts are stored in filesDir/keyboard_layouts/ directory.
 */
object KeyboardLayoutFileManager {
    private const val TAG = "KeyboardLayoutFileManager"
    private const val LAYOUTS_DIR_NAME = "keyboard_layouts"

    private val keyboardLayoutNameToKeyCode = mapOf(
        "KEYCODE_Q" to KeyEvent.KEYCODE_Q,
        "KEYCODE_W" to KeyEvent.KEYCODE_W,
        "KEYCODE_E" to KeyEvent.KEYCODE_E,
        "KEYCODE_R" to KeyEvent.KEYCODE_R,
        "KEYCODE_T" to KeyEvent.KEYCODE_T,
        "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
        "KEYCODE_U" to KeyEvent.KEYCODE_U,
        "KEYCODE_I" to KeyEvent.KEYCODE_I,
        "KEYCODE_O" to KeyEvent.KEYCODE_O,
        "KEYCODE_P" to KeyEvent.KEYCODE_P,
        "KEYCODE_A" to KeyEvent.KEYCODE_A,
        "KEYCODE_S" to KeyEvent.KEYCODE_S,
        "KEYCODE_D" to KeyEvent.KEYCODE_D,
        "KEYCODE_F" to KeyEvent.KEYCODE_F,
        "KEYCODE_G" to KeyEvent.KEYCODE_G,
        "KEYCODE_H" to KeyEvent.KEYCODE_H,
        "KEYCODE_J" to KeyEvent.KEYCODE_J,
        "KEYCODE_K" to KeyEvent.KEYCODE_K,
        "KEYCODE_L" to KeyEvent.KEYCODE_L,
        "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
        "KEYCODE_X" to KeyEvent.KEYCODE_X,
        "KEYCODE_C" to KeyEvent.KEYCODE_C,
        "KEYCODE_V" to KeyEvent.KEYCODE_V,
        "KEYCODE_B" to KeyEvent.KEYCODE_B,
        "KEYCODE_N" to KeyEvent.KEYCODE_N,
        "KEYCODE_M" to KeyEvent.KEYCODE_M
    )
    private val keyboardLayoutKeyCodeToName = keyboardLayoutNameToKeyCode.entries.associate { (name, code) -> code to name }
    
    /**
     * Gets the directory where custom layouts are stored.
     */
    fun getLayoutsDirectory(context: Context): File {
        return File(context.filesDir, LAYOUTS_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Gets the file path for a custom layout by name.
     */
    fun getLayoutFile(context: Context, layoutName: String): File {
        val layoutsDir = getLayoutsDirectory(context)
        return File(layoutsDir, "$layoutName.json")
    }
    
    /**
     * Loads a keyboard layout from a file (either from filesDir or external file).
     * Returns null if the file cannot be loaded or parsed.
     */
    fun loadLayoutFromFile(file: File): Map<Int, KeyboardLayoutManager.LayoutMapping>? {
        return try {
            if (!file.exists() || !file.canRead()) {
                Log.w(TAG, "File does not exist or cannot be read: ${file.absolutePath}")
                return null
            }
            
            val jsonString = file.readText()
            parseLayoutJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout from file: ${file.absolutePath}", e)
            null
        }
    }
    
    /**
     * Loads a keyboard layout from an InputStream (e.g., from file picker).
     * Returns null if the stream cannot be read or parsed.
     */
    fun loadLayoutFromStream(inputStream: InputStream): Map<Int, KeyboardLayoutManager.LayoutMapping>? {
        return try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            parseLayoutJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout from stream", e)
            null
        }
    }
    
    /**
     * Parses a JSON string into a layout mapping.
     * Returns null if the JSON is invalid or incomplete.
     */
    private fun parseLayoutJson(jsonString: String): Map<Int, KeyboardLayoutManager.LayoutMapping>? {
        return try {
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            
            val layout = mutableMapOf<Int, KeyboardLayoutManager.LayoutMapping>()
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyboardLayoutNameToKeyCode[keyName]
                
                if (keyCode != null) {
                    val mappingObj = mappingsObject.getJSONObject(keyName)
                    val lowercase = mappingObj.getString("lowercase")
                    val uppercase = mappingObj.getString("uppercase")
                    
                    if (lowercase.length == 1 && uppercase.length == 1) {
                        layout[keyCode] = KeyboardLayoutManager.LayoutMapping(
                            lowercase[0],
                            uppercase[0]
                        )
                    }
                }
            }
            
            Log.d(TAG, "Parsed layout with ${layout.size} mappings")
            layout
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing layout JSON", e)
            null
        }
    }
    
    /**
     * Saves a keyboard layout to device storage.
     * @param context The application context
     * @param layoutName The name of the layout (without .json extension)
     * @param layout The layout mapping to save
     * @param name Optional display name for the layout
     * @param description Optional description for the layout
     * @return true if the layout was saved successfully, false otherwise
     */
    fun saveLayout(
        context: Context,
        layoutName: String,
        layout: Map<Int, KeyboardLayoutManager.LayoutMapping>,
        name: String? = null,
        description: String? = null
    ): Boolean {
        return try {
            val layoutFile = getLayoutFile(context, layoutName)
            val jsonString = buildLayoutJsonString(layoutName, layout, name, description)
            FileOutputStream(layoutFile).use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            }

            Log.d(TAG, "Saved layout: $layoutName to ${layoutFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving layout: $layoutName", e)
            false
        }
    }

    /**
     * Builds the JSON string for a layout mapping.
     */
    fun buildLayoutJsonString(
        layoutName: String,
        layout: Map<Int, KeyboardLayoutManager.LayoutMapping>,
        name: String?,
        description: String?
    ): String {
        val jsonObject = JSONObject()
        name?.takeIf { it.isNotBlank() }?.let { jsonObject.put("name", it) }
        description?.takeIf { it.isNotBlank() }?.let { jsonObject.put("description", it) }

        val mappingsObject = JSONObject()
        layout.forEach { (keyCode, mapping) ->
            val keyName = keyboardLayoutKeyCodeToName[keyCode]
            if (keyName != null) {
                val mappingObj = JSONObject()
                mappingObj.put("lowercase", mapping.lowercase.toString())
                mappingObj.put("uppercase", mapping.uppercase.toString())
                mappingsObject.put(keyName, mappingObj)
            }
        }

        jsonObject.put("mappings", mappingsObject)
        return jsonObject.toString(2)
    }
    
    /**
     * Saves a layout from a JSON string to device storage.
     * Useful when importing from external sources.
     * @param context The application context
     * @param layoutName The name of the layout (without .json extension)
     * @param jsonString The JSON string to save
     * @return true if the layout was saved successfully, false otherwise
     */
    fun saveLayoutFromJson(
        context: Context,
        layoutName: String,
        jsonString: String
    ): Boolean {
        return try {
            // Validate JSON by parsing it first
            val layout = parseLayoutJson(jsonString)
            if (layout == null) {
                Log.e(TAG, "Invalid JSON format, cannot save layout: $layoutName")
                return false
            }
            
            val layoutFile = getLayoutFile(context, layoutName)
            FileOutputStream(layoutFile).use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            }
            
            Log.d(TAG, "Saved layout from JSON: $layoutName to ${layoutFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving layout from JSON: $layoutName", e)
            false
        }
    }
    
    /**
     * Gets all custom layout names from device storage.
     */
    fun getCustomLayoutNames(context: Context): List<String> {
        return try {
            val layoutsDir = getLayoutsDirectory(context)
            val layoutFiles = layoutsDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            }
            
            layoutFiles?.map { it.name.removeSuffix(".json") }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting custom layout names", e)
            emptyList()
        }
    }
    
    /**
     * Gets layout metadata (name and description) from a file.
     * Returns null if the file doesn't exist or cannot be read.
     */
    fun getLayoutMetadata(context: Context, layoutName: String): LayoutMetadata? {
        return try {
            val layoutFile = getLayoutFile(context, layoutName)
            if (!layoutFile.exists() || !layoutFile.canRead()) {
                return null
            }
            
            val jsonString = layoutFile.readText()
            val jsonObject = JSONObject(jsonString)
            
            LayoutMetadata(
                name = jsonObject.optString("name", layoutName),
                description = jsonObject.optString("description", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout metadata: $layoutName", e)
            null
        }
    }
    
    /**
     * Gets layout metadata (name and description) from assets.
     * Returns null if the file doesn't exist or cannot be read.
     */
    fun getLayoutMetadataFromAssets(assets: android.content.res.AssetManager, layoutName: String): LayoutMetadata? {
        return try {
            val filePath = "common/layouts/$layoutName.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            LayoutMetadata(
                name = jsonObject.optString("name", layoutName),
                description = jsonObject.optString("description", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout metadata from assets: $layoutName", e)
            null
        }
    }
    
    /**
     * Deletes a custom layout from device storage.
     * @return true if the layout was deleted successfully, false otherwise
     */
    fun deleteLayout(context: Context, layoutName: String): Boolean {
        return try {
            val layoutFile = getLayoutFile(context, layoutName)
            if (layoutFile.exists()) {
                val deleted = layoutFile.delete()
                if (deleted) {
                    Log.d(TAG, "Deleted layout: $layoutName")
                }
                deleted
            } else {
                Log.w(TAG, "Layout file does not exist: $layoutName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting layout: $layoutName", e)
            false
        }
    }
    
    /**
     * Checks if a custom layout exists.
     */
    fun layoutExists(context: Context, layoutName: String): Boolean {
        return getLayoutFile(context, layoutName).exists()
    }
    
    /**
     * Copies a layout file from one location to device storage.
     * Useful for importing from external sources.
     * @param sourceFile The source file to copy
     * @param targetLayoutName The name for the layout in device storage
     * @return true if the copy was successful, false otherwise
     */
    fun importLayoutFromFile(
        context: Context,
        sourceFile: File,
        targetLayoutName: String
    ): Boolean {
        return try {
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                Log.e(TAG, "Source file does not exist or cannot be read: ${sourceFile.absolutePath}")
                return false
            }
            
            // Validate the layout by parsing it first
            val layout = loadLayoutFromFile(sourceFile)
            if (layout == null) {
                Log.e(TAG, "Invalid layout file, cannot import: ${sourceFile.absolutePath}")
                return false
            }
            
            // Copy the file
            val targetFile = getLayoutFile(context, targetLayoutName)
            sourceFile.copyTo(targetFile, overwrite = true)
            
            Log.d(TAG, "Imported layout from ${sourceFile.absolutePath} to ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing layout from file", e)
            false
        }
    }
    
    /**
     * Data class for layout metadata.
     */
    data class LayoutMetadata(
        val name: String,
        val description: String
    )
}


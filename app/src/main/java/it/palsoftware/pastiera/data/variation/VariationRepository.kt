package it.palsoftware.pastiera.data.variation

import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject

/**
 * Loads character variations from JSON assets.
 */
object VariationRepository {
    private const val TAG = "VariationRepository"

    fun loadVariations(assets: AssetManager): Map<Char, List<String>> {
        val variationsMap = mutableMapOf<Char, List<String>>()
        return try {
            val filePath = "common/variations/variations.json"
            val inputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val variationsObject = jsonObject.getJSONObject("variations")

            val keys = variationsObject.keys()
            while (keys.hasNext()) {
                val baseChar = keys.next()
                if (baseChar.length == 1) {
                    val variationsArray = variationsObject.getJSONArray(baseChar)
                    val variationsList = mutableListOf<String>()
                    for (i in 0 until variationsArray.length()) {
                        variationsList.add(variationsArray.getString(i))
                    }
                    variationsMap[baseChar[0]] = variationsList
                }
            }
            variationsMap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading character variations", e)
            // Fallback to basic variations
            variationsMap['e'] = listOf("è", "é", "€")
            variationsMap['a'] = listOf("à", "á", "ä")
            variationsMap
        }
    }

    /**
     * Loads static utility variations from JSON assets.
     * These are shown in the variation bar when static mode is enabled
     * or when smart features are disabled for the current field.
     */
    fun loadStaticVariations(assets: AssetManager): List<String> {
        return try {
            val filePath = "common/variations/variations.json"
            val inputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            if (jsonObject.has("staticVariations")) {
                val staticArray = jsonObject.getJSONArray("staticVariations")
                val result = mutableListOf<String>()
                for (i in 0 until staticArray.length()) {
                    val value = staticArray.getString(i)
                    if (!value.isNullOrEmpty()) {
                        result.add(value)
                    }
                }
                if (result.isNotEmpty()) {
                    return result
                }
            }

            // Fallback: a small set of utility symbols
            listOf(";", ",", ":", "…", "?", "!", "\"")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading static variations", e)
            // Fallback: same default set
            listOf(";", ",", ":", "…", "?", "!", "\"")
        }
    }
}

package it.palsoftware.pastiera.commands

import org.json.JSONArray
import org.json.JSONObject

object CommandJson {
    fun launchToJson(launch: CommandLaunchSpec): JSONObject {
        return JSONObject().apply {
            when (launch) {
                is CommandLaunchSpec.AppPackage -> {
                    put("type", "app_package")
                    put("packageName", launch.packageName)
                }
                is CommandLaunchSpec.IntentUri -> {
                    put("type", "intent_uri")
                    put("action", launch.action)
                    launch.data?.let { put("data", it) }
                    launch.packageName?.let { put("packageName", it) }
                    launch.componentName?.let { put("componentName", it) }
                    put("categories", JSONArray(launch.categories))
                    put("flags", JSONArray(launch.flags))
                }
                is CommandLaunchSpec.InternalAction -> {
                    put("type", "internal_action")
                    put("actionId", launch.actionId)
                }
                is CommandLaunchSpec.NavAction -> {
                    put("type", "nav_action")
                    put("mappingType", launch.mappingType)
                    put("value", launch.value)
                }
            }
        }
    }

    fun launchFromJson(json: JSONObject?): CommandLaunchSpec? {
        if (json == null) return null
        return when (json.optString("type")) {
            "app_package" -> json.optString("packageName")
                .takeIf { it.isNotBlank() }
                ?.let { CommandLaunchSpec.AppPackage(it) }
            "intent_uri" -> CommandLaunchSpec.IntentUri(
                action = json.optString("action").takeIf { it.isNotBlank() } ?: return null,
                data = json.optString("data").takeIf { it.isNotBlank() },
                packageName = json.optString("packageName").takeIf { it.isNotBlank() },
                componentName = json.optString("componentName").takeIf { it.isNotBlank() },
                categories = json.optJSONArray("categories").toStringList(),
                flags = json.optJSONArray("flags").toStringList()
            )
            "internal_action" -> json.optString("actionId")
                .takeIf { it.isNotBlank() }
                ?.let { CommandLaunchSpec.InternalAction(it) }
            "nav_action" -> CommandLaunchSpec.NavAction(
                mappingType = json.optString("mappingType").takeIf { it.isNotBlank() } ?: return null,
                value = json.optString("value")
            )
            else -> null
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
    }
}

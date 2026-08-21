package com.example.data

import com.example.data.model.DownloadProfile
import com.example.data.model.MediaType
import org.json.JSONArray
import org.json.JSONObject

object ProfileManager {

    fun serializeProfiles(profiles: List<DownloadProfile>): String {
        val array = JSONArray()
        for (p in profiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("description", p.description)
                put("mediaType", p.mediaType.name)
                put("videoQuality", p.videoQuality)
                put("container", p.container)
                put("audioBitrate", p.audioBitrate ?: 320)
                put("embedSubs", p.embedSubs)
                put("embedThumbnail", p.embedThumbnail)
                put("isPreset", p.isPreset)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeProfiles(json: String?): List<DownloadProfile> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<DownloadProfile>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val mediaTypeStr = obj.optString("mediaType", "VIDEO")
                val mediaType = try {
                    MediaType.valueOf(mediaTypeStr)
                } catch (e: Exception) {
                    MediaType.VIDEO
                }
                list.add(
                    DownloadProfile(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Custom Profile"),
                        description = obj.optString("description", ""),
                        mediaType = mediaType,
                        videoQuality = obj.optString("videoQuality", "1080p"),
                        container = obj.optString("container", "mp4"),
                        audioBitrate = if (obj.has("audioBitrate")) obj.getInt("audioBitrate") else 320,
                        embedSubs = obj.optBoolean("embedSubs", false),
                        embedThumbnail = obj.optBoolean("embedThumbnail", true),
                        isPreset = obj.optBoolean("isPreset", false)
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore deserialization issues and return what was parsed
        }
        return list
    }

    fun getAllProfiles(customJson: String?): List<DownloadProfile> {
        val custom = deserializeProfiles(customJson)
        return DownloadProfile.DEFAULT_PROFILES + custom
    }
}

package com.movtery.zalithlauncher.game.download.addon.platform.horizon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for Horizon's public mod registry index (schema 1).
 * Source: https://horizon-rust.github.io/horizon-mods/index.json
 */
@Serializable
data class HorizonIndex(
    val schema: Int = 1,
    val generated: String? = null,
    val projects: List<HorizonProject> = emptyList(),
    val revoked: List<HorizonRevoked> = emptyList(),
)

@Serializable
data class HorizonProject(
    val id: String,
    val kind: String = "mod",
    val name: String,
    val summary: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val owners: List<String> = emptyList(),
    val license: String? = null,
    val source: String? = null,
    val icon: String? = null,
    val key: String? = null,
    val downloads: Long = 0,
    val versions: List<HorizonVersion> = emptyList(),
)

@Serializable
data class HorizonVersion(
    val version: String,
    val published: String? = null,
    val changelog: String? = null,
    val api: String? = null,
    val side: String? = null,
    val capabilities: List<String> = emptyList(),
    val game: String? = null,
    val file: HorizonFile,
    @SerialName("api_level") val apiLevel: Int? = null,
    val requires: List<HorizonRequire>? = null,
)

@Serializable
data class HorizonFile(
    val name: String,
    val url: String,
    val size: Long = 0,
    val sha256: String? = null,
    val signature: String? = null,
)

@Serializable
data class HorizonRequire(
    val id: String,
    val version: String? = null,
    val sha256: String? = null,
)

@Serializable
data class HorizonRevoked(
    val id: String,
    val sha256: String? = null,
    val reason: String? = null,
    val date: String? = null,
)

enum class HorizonKind(val folder: String) {
    MOD("horizon-mods"),
    NATIVE_MOD("horizon-native-mods"),
    MODPACK("horizon-packs"),
    RESOURCE_PACK("resourcepacks"),
    DATAPACK("datapacks"),
    SHADER("shaderpacks");

    companion object {
        fun from(value: String): HorizonKind = when (value) {
            "native-mod" -> NATIVE_MOD
            "modpack", "hpack" -> MODPACK
            "resourcepack" -> RESOURCE_PACK
            "datapack" -> DATAPACK
            "shader", "shaderpack" -> SHADER
            else -> MOD
        }
    }
}
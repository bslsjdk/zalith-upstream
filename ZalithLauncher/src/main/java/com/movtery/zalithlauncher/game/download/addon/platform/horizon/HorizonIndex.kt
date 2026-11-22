package com.movtery.zalithlauncher.game.download.addon.platform.horizon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for Horizon's public mod registry index.
 * Source: https://horizon-rust.github.io/horizon-mods/index.json
 * Schema 1, documented at horizon-upstream/docs/api/mod-index.md.
 *
 * These are intentionally independent of ZL2's Modrinth/CurseForge models;
 * HorizonSearcher maps them onto the common Platform* interfaces so ZL2's
 * existing mod-download UI can display and install Horizon content.
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
    val kind: String = "mod",          // mod | native-mod | modpack | resourcepack | shader
    val name: String,
    val summary: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val owners: List<String> = emptyList(),
    val license: String? = null,
    val source: String? = null,
    val icon: String? = null,
    val key: String? = null,            // minisign public key (empty = unsigned)
    val downloads: Long = 0,
    val versions: List<HorizonVersion> = emptyList(),
)

@Serializable
data class HorizonVersion(
    val version: String,
    val published: String? = null,
    val changelog: String? = null,
    val api: String? = null,
    val side: String? = null,            // client | server | universal
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

/** Maps an index `kind` string to a ZL2 VersionFolders target. */
enum class HorizonKind(val folder: String) {
    MOD("horizon-mods"),
    NATIVE_MOD("horizon-native-mods"),
    MODPACK("horizon-packs"),
    RESOURCE_PACK("resourcepacks"),
    SHADER("shaderpacks");

    companion object {
        fun from(value: String): HorizonKind = when (value) {
            "native-mod" -> NATIVE_MOD
            "modpack" -> MODPACK
            "resourcepack" -> RESOURCE_PACK
            "shader" -> SHADER
            else -> MOD
        }
    }
}
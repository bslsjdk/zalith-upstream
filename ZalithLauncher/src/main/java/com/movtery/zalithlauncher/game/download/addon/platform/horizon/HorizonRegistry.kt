package com.movtery.zalithlauncher.game.download.addon.platform.horizon

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Core adapter for Horizon's public mod registry — the logic layer that is
 * independent of ZL2's download UI types.
 *
 * Reads https://horizon-rust.github.io/horizon-mods/index.json (schema 1,
 * CORS-enabled, 269 projects as of 2026-11-22) and exposes search/filter/
 * project/version lookups. A future HorizonSearcher can map these results
 * onto ZL2's PlatformProject/PlatformVersion/PlatformSearchResult interfaces
 * to reuse the existing mod-download screens verbatim.
 */
object HorizonRegistry {
    const val INDEX_URL = "https://horizon-rust.github.io/horizon-mods/index.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    @Volatile
    private var cached: HorizonIndex? = null
    private var cachedAt = 0L
    private const val CACHE_MS = 10 * 60 * 1000L

    suspend fun index(force: Boolean = false): HorizonIndex {
        val now = System.currentTimeMillis()
        val hit = cached
        if (!force && hit != null && now - cachedAt < CACHE_MS) return hit
        val text = client.get(INDEX_URL).bodyAsText()
        val parsed = json.decodeFromString<HorizonIndex>(text)
        cached = parsed
        cachedAt = now
        return parsed
    }

    data class SearchHit(val project: HorizonProject, val latest: HorizonVersion?)

    fun search(
        index: HorizonIndex,
        query: String,
        kind: HorizonKind? = null,
        gameVersion: String? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): List<SearchHit> {
        val q = query.trim().lowercase()
        val revokedIds = index.revoked.map { it.id }.toSet()
        return index.projects.asSequence()
            .filter { it.id !in revokedIds }
            .filter { kind == null || HorizonKind.from(it.kind) == kind }
            .filter {
                q.isEmpty() || it.id.contains(q) || it.name.lowercase().contains(q) ||
                    it.summary?.lowercase()?.contains(q) == true
            }
            .map { p -> SearchHit(p, p.versions.firstOrNull { v ->
                gameVersion == null || v.game == null || v.game == gameVersion
            } ?: p.versions.firstOrNull()) }
            .filter { it.latest != null }
            .sortedByDescending { it.project.downloads }
            .drop(offset)
            .take(limit)
            .toList()
    }

    fun project(index: HorizonIndex, id: String): HorizonProject? =
        index.projects.firstOrNull { it.id == id }

    fun versions(index: HorizonIndex, id: String): List<HorizonVersion> =
        project(index, id)?.versions ?: emptyList()

    fun isRevoked(index: HorizonIndex, sha256: String): Boolean =
        index.revoked.any { it.sha256.equals(sha256, ignoreCase = true) }
}
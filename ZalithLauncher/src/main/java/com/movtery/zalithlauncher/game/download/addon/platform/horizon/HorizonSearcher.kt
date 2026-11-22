package com.movtery.zalithlauncher.game.download.addon.platform.horizon

import com.movtery.zalithlauncher.game.download.addon.platform.AbstractPlatformSearcher
import com.movtery.zalithlauncher.game.download.addon.platform.Platform
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformClasses
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformDependencyType
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformDisplayLabel
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformFilterCode
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformProject
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformReleaseType
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformSearchData
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformSearchFilter
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformSearchResult
import com.movtery.zalithlauncher.game.download.addon.platform.PlatformVersion
import com.movtery.zalithlauncher.ui.screens.content.download.addon.elements.AddonPage
import org.jackhuang.hmcl.addon.AddonProjectInfo
import java.io.File
import java.time.Instant

/** Maps Horizon's public mod index onto ZL2's platform abstractions. */
class HorizonSearcher(
    val api: String = HorizonRegistry.INDEX_URL,
    source: String = "Horizon Registry"
) : AbstractPlatformSearcher(platform = Platform.HORIZON, source = source) {

    private fun PlatformClasses.forHorizon(): HorizonKind? = when (this) {
        PlatformClasses.MOD -> HorizonKind.MOD
        PlatformClasses.MOD_PACK -> HorizonKind.MODPACK
        PlatformClasses.RESOURCE_PACK -> HorizonKind.RESOURCE_PACK
        PlatformClasses.SHADERS -> HorizonKind.SHADER
        PlatformClasses.SAVES -> null
    }

    private fun HorizonKind.toPlatformClasses(): PlatformClasses = when (this) {
        HorizonKind.MOD, HorizonKind.NATIVE_MOD -> PlatformClasses.MOD
        HorizonKind.MODPACK -> PlatformClasses.MOD_PACK
        HorizonKind.RESOURCE_PACK -> PlatformClasses.RESOURCE_PACK
        HorizonKind.DATAPACK -> PlatformClasses.SAVES
        HorizonKind.SHADER -> PlatformClasses.SHADERS
    }

    override suspend fun searchAddons(
        query: String,
        searchFilter: PlatformSearchFilter,
        platformClasses: PlatformClasses
    ): PlatformSearchResult {
        val index = HorizonRegistry.index()
        val hits = HorizonRegistry.search(
            index = index,
            query = query,
            kind = platformClasses.forHorizon(),
            gameVersion = searchFilter.gameVersion.takeIf { it.isNotBlank() },
            limit = searchFilter.limit,
            offset = searchFilter.index,
        )
        return HorizonSearchResult(
            hits = hits.map { HorizonData(index, it.project) },
            offset = searchFilter.index,
            limit = searchFilter.limit,
            totalHits = index.projects.size,
        )
    }

    override suspend fun getProject(projectID: String): PlatformProject {
        val index = HorizonRegistry.index()
        val p = HorizonRegistry.project(index, projectID)
            ?: error("Horizon project not found: $projectID")
        return HorizonData(index, p)
    }

    override suspend fun getVersions(
        projectID: String,
        pageCallback: (chunk: Int, page: Int) -> Unit
    ): List<PlatformVersion> {
        val index = HorizonRegistry.index()
        return HorizonRegistry.versions(index, projectID).map {
            HorizonVersionView(projectID, it)
        }.also { pageCallback(0, 0) }
    }

    override suspend fun getVersionByLocalFile(file: File, sha1: String): PlatformVersion? {
        val index = HorizonRegistry.index()
        val hit = index.projects.asSequence().flatMap { p ->
            p.versions.map { v -> Pair(p, v) }
        }.firstOrNull { (_, v) ->
            v.file.name.equals(file.name, ignoreCase = true) ||
                v.file.sha256.equals(sha1, ignoreCase = true)
        } ?: return null
        return HorizonVersionView(hit.first.id, hit.second)
    }

    private inner class HorizonData(
        private val index: HorizonIndex,
        private val project: HorizonProject,
    ) : PlatformSearchData, PlatformProject {
        private val kind = HorizonKind.from(project.kind)
        override fun platform(): Platform = Platform.HORIZON
        override fun platformId(): String = project.id
        override fun platformSlug(): String = project.id
        override fun platformTitle(): String = project.name
        override fun platformSummary(): String = project.summary ?: project.description.orEmpty()
        override fun platformDescription(): String = platformSummary()
        override fun platformAuthors(): List<String>? = project.authors.takeIf { it.isNotEmpty() }
        override fun platformIconUrl(): String? = project.icon
        override fun platformDownloadCount(): Long = project.downloads
        override fun platformFollows(): Long? = null
        override fun platformModLoaders(): List<PlatformDisplayLabel>? = null
        override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? = null
        override fun platformClasses(defaultClasses: PlatformClasses): PlatformClasses = kind.toPlatformClasses()
        override fun platformAvailable(): Boolean = index.revoked.none { it.id == project.id }
        override fun checkClasses() {}
        override fun platformUrls(defaultClasses: PlatformClasses): PlatformProject.Urls =
            PlatformProject.Urls(
                projectUrl = "https://github.com/horizon-rust/horizon-mods",
                sourceUrl = project.source,
            )
        override fun platformScreenshots(): List<PlatformProject.Screenshot> = emptyList()
    }

    private inner class HorizonVersionView(
        private val projectId: String,
        private val v: HorizonVersion,
    ) : PlatformVersion {
        override suspend fun initFile(currentProjectId: String): Boolean = true
        override fun platform(): Platform = Platform.HORIZON
        override fun platformId(): String = v.version
        override fun platformProjectId(): String = projectId
        override fun platformDisplayName(): String = "${v.version} (${v.game ?: "Horizon"})"
        override fun platformFileName(): String = v.file.name
        override fun platformGameVersion(): Array<String> = v.game?.let { arrayOf(it) } ?: emptyArray()
        override fun platformLoaders(): List<PlatformDisplayLabel> = emptyList()
        override fun platformReleaseType(): PlatformReleaseType = PlatformReleaseType.RELEASE
        override fun platformDependencies(): List<PlatformVersion.PlatformDependency> =
            v.requires?.map {
                PlatformVersion.PlatformDependency(
                    platform = Platform.HORIZON,
                    projectId = it.id,
                    versionId = it.version,
                    type = PlatformDependencyType.REQUIRED,
                )
            } ?: emptyList()
        override fun platformDownloadCount(): Long = 0
        override fun platformDownloadUrl(): String = v.file.url
        override fun platformDatePublished(): Instant =
            runCatching { Instant.parse(v.published) }.getOrDefault(Instant.EPOCH)
        override fun platformSha1(): String? = v.file.sha256
        override fun platformFileSize(): Long = v.file.size
        override fun platformVersion(): String = v.version
    }

    private class HorizonSearchResult(
        val hits: List<PlatformSearchData>,
        val offset: Int,
        val limit: Int,
        val totalHits: Int,
    ) : PlatformSearchResult {
        private val pageData: List<Pair<PlatformSearchData, AddonProjectInfo?>> =
            hits.map { it to null }

        override fun getAddonPage(classes: PlatformClasses): AddonPage = AddonPage(
            pageNumber = offset / limit + 1,
            pageIndex = offset,
            totalPage = (totalHits + limit - 1) / limit,
            isLastPage = (offset + limit) >= totalHits,
            data = pageData,
        )
        override fun processChineseSearchResults(
            searchFilter: String,
            classes: PlatformClasses
        ): PlatformSearchResult = this
    }
}
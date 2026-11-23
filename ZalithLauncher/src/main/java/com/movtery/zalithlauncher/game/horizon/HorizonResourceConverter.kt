/*
 * RCL - Horizon resource converter (pure Kotlin, on-device).
 * Converts vanilla Minecraft Java Edition 26.3 into a resources pack that the
 * official Starling/Horizon engine accepts via its ACTION_VIEW import flow.
 *
 * Verified against starling-client/starling source (2026-11-23):
 *  - crates/horizon-client/src/import.rs: a zip containing "horizon-resources.json"
 *    is classified as Kind::Resources; install_resources() checks that manifest's
 *    "data_version" equals horizon_world::DATA_VERSION, which for MC 26.3 is 5023.
 *  - The Activity copies the shared zip into import-inbox, then imports + starts the game.
 *
 * No JVM / network is required: copyrighted assets come from the player's own
 * 26.3.jar with java.util.zip (available on Android since API 1).
 */
package com.movtery.zalithlauncher.game.horizon

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Pure-Kotlin Java 26.3 -> Horizon 26.3 resource converter + packer, running on the phone. */
object HorizonResourceConverter {

    data class ConvertResult(
        val success: Boolean,
        val outputDir: File,
        val packFile: File?,
        val extracted: Int,
        val message: String,
    )

    data class CheckResult(val ready: Boolean, val message: String)

    fun interface Progress {
        fun onProgress(done: Int, total: Int, phase: String)
    }

    private const val MANIFEST = "horizon-resources.json"
    /** horizon_world::DATA_VERSION / horizon_core::version::DATA_VERSION for MC 26.3. */
    const val DATA_VERSION = 5023

    /** Locate the client jar in a vanilla install. */
    fun findClientJar(minecraftDir: File, version: String): File? = listOf(
        File(minecraftDir, "versions/$version/$version.jar"),
        File(minecraftDir, "versions/$version/$version-client.jar"),
        File(minecraftDir, "client/$version.jar"),
    ).firstOrNull { it.isFile }

    /** Quick readiness check, mirrors the engine's "manifest exists" rule. */
    fun check(outputDir: File): CheckResult {
        val manifest = File(outputDir, MANIFEST)
        return if (manifest.isFile) {
            CheckResult(true, "资源已就绪：${outputDir.path}")
        } else {
            CheckResult(false, "缺少 $MANIFEST，需要转换：${outputDir.path}")
        }
    }

    /**
     * Convert [minecraftDir] into a Horizon resources folder at [outputDir], then
     * pack it into a zip the official engine imports through ACTION_VIEW.
     */
    fun convert(
        minecraftDir: File,
        outputDir: File,
        version: String = "26.3",
        overwrite: Boolean = false,
        progress: Progress? = null,
    ): ConvertResult = try {
        if (!minecraftDir.isDirectory) {
            ConvertResult(false, outputDir, null, 0, "原版目录不存在：${minecraftDir.path}")
        } else {
            val jar = findClientJar(minecraftDir, version)
                ?: return ConvertResult(
                    false, outputDir, null, 0,
                    "找不到 $version.jar，请确认原版目录含 versions/$version/$version.jar"
                )
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                return ConvertResult(false, outputDir, null, 0, "无法创建输出目录：${outputDir.path}")
            }
            if (overwrite) {
                File(outputDir, "assets").deleteRecursively()
                File(outputDir, "data").deleteRecursively()
            }

            progress?.onProgress(0, 4, "正在解压 $version.jar 的资源…")
            val extracted = extractJar(jar, outputDir, progress)

            progress?.onProgress(2, 4, "正在复制启动器资源对象…")
            copyLauncherAssets(minecraftDir, outputDir)

            progress?.onProgress(3, 4, "正在写入清单…")
            writeManifest(outputDir, version)

            progress?.onProgress(4, 4, "正在打包资源…")
            val pack = File(outputDir.parentFile, "horizon-resources-$version.zip")
            packResources(outputDir, pack, progress)

            ConvertResult(
                true, outputDir, pack, extracted,
                "转换完成：提取 $extracted 个文件，资源包 ${pack.path}"
            )
        }
    } catch (e: IOException) {
        ConvertResult(false, outputDir, null, 0, "转换失败：${e.message ?: e::class.java.simpleName}")
    } catch (e: SecurityException) {
        ConvertResult(false, outputDir, null, 0, "权限不足：${e.message ?: e::class.java.simpleName}")
    }

    /** Extract assets/minecraft and data from the client jar with zip-slip protection. */
    private fun extractJar(jar: File, outputDir: File, progress: Progress?): Int {
        val root = outputDir.canonicalFile
        var count = 0
        ZipFile(jar).use { zip ->
            val entries = zip.entries()
            val total = zip.size()
            var done = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                done++
                if (done % 200 == 0) progress?.onProgress(1, total.coerceAtLeast(1), "解压资源 $done/$total")
                if (entry.isDirectory) continue
                val name = entry.name
                if (!(name.startsWith("assets/") || name.startsWith("data/") || name == "pack.mcmeta")) continue

                val dest = File(outputDir, name)
                if (!dest.canonicalFile.toPath().startsWith(root.toPath())) continue
                dest.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    dest.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
                }
                count++
            }
        }
        return count
    }

    /** Best-effort copy of launcher asset objects (sounds etc.). */
    private fun copyLauncherAssets(minecraftDir: File, outputDir: File) {
        val objects = File(minecraftDir, "assets/objects")
        if (!objects.isDirectory) return
        val target = File(outputDir, "launcher-assets/objects")
        target.mkdirs()
        objects.listFiles()?.forEach { bucket ->
            if (bucket.isDirectory) {
                val destBucket = File(target, bucket.name)
                destBucket.mkdirs()
                bucket.listFiles()?.forEach { obj ->
                    if (obj.isFile && !File(destBucket, obj.name).exists()) {
                        obj.copyTo(File(destBucket, obj.name), overwrite = false)
                    }
                }
            }
        }
    }

    /** Write the manifest the official import checks (data_version MUST match 5023). */
    private fun writeManifest(outputDir: File, version: String) {
        File(outputDir, MANIFEST).writeText(
            "{\"version\":\"$version\",\"data_version\":$DATA_VERSION}\n",
            Charsets.UTF_8,
        )
        File(outputDir, "pack.mcmeta").writeText(
            "{\"pack\":{\"pack_format\":1,\"description\":\"RCL Horizon resources $version\"}}\n",
            Charsets.UTF_8,
        )
        File(outputDir, "rcl-convert-info.json").writeText(
            "{\"version\":\"$version\",\"data_version\":$DATA_VERSION,\"converter\":\"RCL HorizonResourceConverter\",\"on_device\":true}\n",
            Charsets.UTF_8,
        )
    }

    /**
     * Zip the resources folder at the archive ROOT (no extra top folder), which is
     * the layout import.rs's marker_prefix("horizon-resources.json") expects.
     */
    private fun packResources(outputDir: File, pack: File, progress: Progress?) {
        if (pack.exists() && !pack.delete()) throw IOException("无法覆盖旧资源包：${pack.path}")
        ZipOutputStream(pack.outputStream().buffered()).use { zos ->
            val files = outputDir.walkTopDown().filter { it.isFile }.toList()
            val total = files.size.coerceAtLeast(1)
            var done = 0
            for (f in files) {
                val rel = f.relativeTo(outputDir).path.replace(File.separatorChar, '/')
                zos.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zos, 64 * 1024) }
                zos.closeEntry()
                done++
                if (done % 200 == 0) progress?.onProgress(4, total, "打包 $done/$total")
            }
        }
    }

    /** Convert Java game dir into a STANDARD Horizon game folder that any launcher recognizes.
     *  Copies versions/, assets/, libraries/ so ZL2 lists versions and lets the user edit mods,
     *  then writes converted Horizon resources under resources/ for the built-in engine. */
    fun convertToGameDir(
        srcDir: File,
        gameDir: File,
        version: String = "26.3",
        progress: Progress? = null,
    ): ConvertResult = try {
        progress?.onProgress(0, 5, "Preparing game folder")
        if (!srcDir.isDirectory) return ConvertResult(false, gameDir, null, 0, "Source not found: " + srcDir.path)
        if (!gameDir.exists() && !gameDir.mkdirs()) return ConvertResult(false, gameDir, null, 0, "Cannot create game folder")
        // Copy the standard Minecraft directory structure so ZL2 recognizes versions and mods.
        val jar = findClientJar(srcDir, version)
            ?: return ConvertResult(false, gameDir, null, 0, "Cannot find " + version + ".jar")
        progress?.onProgress(1, 5, "Copying versions")
        File(srcDir, "versions").copyStandard(File(gameDir, "versions"))
        progress?.onProgress(2, 5, "Copying assets and libraries")
        File(srcDir, "assets").copyStandard(File(gameDir, "assets"))
        File(srcDir, "libraries").copyStandard(File(gameDir, "libraries"))
        val cfg = File(srcDir, "zalith-game.cfg")
        if (cfg.isFile) cfg.copyTo(File(gameDir, "zalith-game.cfg"), overwrite = true)
        progress?.onProgress(3, 5, "Extracting Horizon resources")
        val resDir = File(gameDir, "resources")
        if (!resDir.exists()) resDir.mkdirs()
        val extracted = extractJar(jar, resDir, progress)
        copyLauncherAssets(srcDir, resDir)
        writeManifest(resDir, version)
        File(gameDir, ".nomedia").createNewFile()
        progress?.onProgress(5, 5, "Done")
        ConvertResult(true, gameDir, null, extracted, "Horizon game folder ready: " + gameDir.path)
    } catch (e: Exception) {
        ConvertResult(false, gameDir, null, 0, "Convert failed: " + (e.message ?: e::class.java.simpleName))
    }

    /** Copy a folder recursively, keeping existing files (no overwrite). */
    private fun File.copyStandard(dest: File) {
        if (!this.isDirectory) return
        this.copyRecursively(dest, overwrite = false) { _, _ -> kotlin.io.OnErrorAction.SKIP }
    }
}
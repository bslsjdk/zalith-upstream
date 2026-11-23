/*
 * RCL - Horizon resource converter (pure Kotlin, on-device).
 * Converts vanilla Minecraft Java Edition 26.3 into the resources folder the
 * Horizon (Rust) engine reads. No JVM / prepare binary / network required:
 * the copyrighted resources are extracted from the player's own 26.3.jar with
 * java.util.zip, available on every Android device since API 1.
 */
package com.movtery.zalithlauncher.game.horizon

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Pure-Kotlin Java 26.3 -> Horizon 26.3 resource converter, running on the phone. */
object HorizonResourceConverter {

    data class ConvertResult(
        val success: Boolean,
        val outputDir: File,
        val extracted: Int,
        val message: String,
    )

    data class CheckResult(val ready: Boolean, val message: String)

    fun interface Progress {
        fun onProgress(done: Int, total: Int, phase: String)
    }

    private const val MANIFEST = "horizon-resources.json"

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
     * Convert [minecraftDir] into a Horizon resources folder at [outputDir].
     * Existing output is reused unless [overwrite] is true.
     */
    fun convert(
        minecraftDir: File,
        outputDir: File,
        version: String = "26.3",
        overwrite: Boolean = false,
        progress: Progress? = null,
    ): ConvertResult = try {
        if (!minecraftDir.isDirectory) {
            ConvertResult(false, outputDir, 0, "原版目录不存在：${minecraftDir.path}")
        } else {
            val jar = findClientJar(minecraftDir, version)
                ?: return ConvertResult(
                    false, outputDir, 0,
                    "找不到 $version.jar，请确认原版目录含 versions/$version/$version.jar"
                )
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                return ConvertResult(false, outputDir, 0, "无法创建输出目录：${outputDir.path}")
            }
            if (overwrite) {
                File(outputDir, "assets").deleteRecursively()
                File(outputDir, "data").deleteRecursively()
            }

            progress?.onProgress(0, 3, "正在解压 $version.jar 的资源…")
            val extracted = extractJar(jar, outputDir, progress)

            progress?.onProgress(2, 3, "正在复制启动器资源对象…")
            copyLauncherAssets(minecraftDir, outputDir)

            progress?.onProgress(3, 3, "正在写入清单…")
            writeManifest(outputDir, version)

            ConvertResult(
                true, outputDir, extracted,
                "转换完成：提取 $extracted 个文件到 ${outputDir.path}"
            )
        }
    } catch (e: IOException) {
        ConvertResult(false, outputDir, 0, "转换失败：${e.message ?: e::class.java.simpleName}")
    } catch (e: SecurityException) {
        ConvertResult(false, outputDir, 0, "权限不足：${e.message ?: e::class.java.simpleName}")
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

    /** Write the manifest the engine checks for, plus our own metadata. */
    private fun writeManifest(outputDir: File, version: String) {
        File(outputDir, MANIFEST).writeText("{\"version\":\"$version\"}\n", Charsets.UTF_8)
        File(outputDir, "pack.mcmeta").writeText(
            "{\"pack\":{\"pack_format\":1,\"description\":\"RCL Horizon resources $version\"}}\n",
            Charsets.UTF_8,
        )
        File(outputDir, "rcl-convert-info.json").writeText(
            "{\"version\":\"$version\",\"converter\":\"RCL HorizonResourceConverter\",\"on_device\":true}\n",
            Charsets.UTF_8,
        )
    }
}

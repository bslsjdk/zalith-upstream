/*
 * RCL - Horizon convert screen (pure Kotlin / Compose, on-device).
 * ZL2-native-looking screen: convert the player's own vanilla Java 26.3 install
 * into a resources zip, then launch the built-in Horizon engine (no third-party app).
 */
package com.movtery.zalithlauncher.ui.screens.content

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.game.horizon.HorizonResourceConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Convert + launch: enter the vanilla install path, convert to a resources zip,
 * then "Launch Horizon" starts the RCL-internal engine with the converted resources.
 */
@Composable
fun HorizonConvertScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourcePath by remember { mutableStateOf("/storage/emulated/0/26.3") }
    var outputPath by remember { mutableStateOf("/storage/emulated/0/26.3Rust/resources") }
    var version by remember { mutableStateOf("26.3") }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("准备就绪") }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(1) }
    var packPath by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Java 26.3 → Horizon 26.3 转换",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "本小姐在你的手机上直接转换：从原版 26.3.jar 提取资源，打包成 Horizon 引擎认识的资源包，然后一键启动 Horizon 游戏。引擎已内置，不用电脑、不用 Java、也不用装任何第三方启动器。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = sourcePath,
            onValueChange = { sourcePath = it },
            label = { Text("原版 Java 目录") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = outputPath,
            onValueChange = { outputPath = it },
            label = { Text("Horizon 资源输出目录") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = version,
            onValueChange = { version = it },
            label = { Text("游戏版本") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )

        if (busy) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(text = status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                busy = true
                status = "开始转换…"
                done = 0
                total = 1
                packPath = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        HorizonResourceConverter.convert(
                            minecraftDir = File(sourcePath),
                            outputDir = File(outputPath),
                            version = version.trim().ifEmpty { "26.3" },
                            progress = HorizonResourceConverter.Progress { d, t, phase ->
                                done = d
                                total = t
                                status = phase
                            },
                        )
                    }
                    status = result.message
                    packPath = result.packFile?.path
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "转换中…" else "开始转换")
        }

        Button(
            onClick = {
                val p = packPath
                if (p == null) {
                    status = "请先转换成功，生成资源包"
                } else {
                    launchHorizon(context, File(p))
                }
            },
            enabled = !busy && packPath != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("启动 Horizon")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Horizon 引擎已内置在 RCL 中，无需安装任何第三方启动器。转换产物是标准资源包。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Launch the RCL-internal Horizon engine (no third-party app needed). */
private fun launchHorizon(context: Context, zip: File) {
    val intent = Intent(context, Class.forName("io.github.juanluci.starling.StarlingActivity"))
    intent.putExtra("io.github.juanluci.starling.GAME_DIR", zip.parentFile?.path)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Throwable) {
        android.util.Log.e("HorizonConvert", "launch engine failed", e)
    }
}
/*
 * RCL - Horizon convert screen (pure Kotlin / Compose, on-device).
 * A ZL2-native-looking screen that lets the player convert their own vanilla
 * Java 26.3 install into Horizon resources, entirely on the phone.
 */
package com.movtery.zalithlauncher.ui.screens.content

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.game.horizon.HorizonResourceConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Convert screen: enter the vanilla install path and the output path, then tap
 * convert. All work runs on Dispatchers.IO with progress reported to the UI.
 */
@Composable
fun HorizonConvertScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    var sourcePath by remember { mutableStateOf("/storage/emulated/0/26.3") }
    var outputPath by remember { mutableStateOf("/storage/emulated/0/26.3Rust/resources") }
    var version by remember { mutableStateOf("26.3") }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("准备就绪") }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(1) }

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
            text = "本小姐会在你的手机上直接转换：从原版 26.3.jar 提取资源，生成 Horizon 引擎认识的资源目录。不需要电脑，不需要 Java 运行环境。",
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
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "转换中…" else "开始转换")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "转换完成后，Horizon 资源位于输出目录。把该目录作为游戏目录即可被引擎读取（引擎通过 horizon-resources.json 识别）。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
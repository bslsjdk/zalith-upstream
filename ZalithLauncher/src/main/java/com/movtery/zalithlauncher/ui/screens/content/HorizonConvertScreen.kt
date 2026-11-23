/*
 * RCL - Horizon one-stop screen (pure Kotlin / Compose, on-device).
 * Zero manual paths: auto-detects the vanilla Java 26.3 install, converts it
 * to Horizon resources at <external>/26.3Rust, then launches the built-in engine.
 */
package com.movtery.zalithlauncher.ui.screens.content

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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

private const val GAME_DIR = "/storage/emulated/0/26.3Rust"
private const val SOURCE_DIR = "/storage/emulated/0/26.3"

@Composable
fun HorizonConvertScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("准备就绪") }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(1) }
    var ready by remember { mutableStateOf(HorizonResourceConverter.check(File(GAME_DIR, "resources")).ready) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Horizon 26.3",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "一键把手机里的原版 Java 26.3 转成 Horizon 并启动。
不用填路径、不用电脑、不用第三方启动器。",
            style = MaterialTheme.typography.bodyMedium,
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
                status = "正在查找原版 26.3…"
                done = 0
                total = 1
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        HorizonResourceConverter.convert(
                            minecraftDir = File(SOURCE_DIR),
                            outputDir = File(GAME_DIR, "resources"),
                            version = "26.3",
                            overwrite = false,
                            progress = HorizonResourceConverter.Progress { d, t, phase ->
                                done = d
                                total = t
                                status = phase
                            },
                        )
                    }
                    status = result.message
                    ready = result.success || HorizonResourceConverter.check(File(GAME_DIR, "resources")).ready
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "转换中…" else if (ready) "重新转换资源" else "转换并安装 Horizon")
        }

        Button(
            onClick = {
                if (!ready) {
                    status = "请先完成转换"
                } else {
                    launchHorizon(context, File(GAME_DIR))
                }
            },
            enabled = !busy && ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("启动 Horizon")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "游戏目录：$GAME_DIR
引擎会自动读取该目录下的 resources/，不用手动添加或选择。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Launch the RCL-internal Horizon engine with the converted game directory. */
private fun launchHorizon(context: Context, gameDir: File) {
    val intent = Intent(context, Class.forName("io.github.juanluci.starling.StarlingActivity"))
    intent.putExtra("io.github.juanluci.starling.GAME_DIR", gameDir.path)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Throwable) {
        android.util.Log.e("HorizonConvert", "launch engine failed", e)
    }
}

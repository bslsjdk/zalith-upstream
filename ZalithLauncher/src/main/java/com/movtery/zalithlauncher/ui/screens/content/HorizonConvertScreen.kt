/*
 * RCL Horizon one-stop screen: convert Java 26.3 into a standard game folder.
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
    var status by remember { mutableStateOf("Ready") }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(1) }
    var ready by remember { mutableStateOf(File(GAME_DIR, "versions/26.3/26.3.jar").isFile) }
    Column(modifier = modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Horizon 26.3", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(text = "Convert Java 26.3 into a standard game folder with versions and mods.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        if (busy) { LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth()) }
        Text(text = status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            busy = true; status = "Converting"; done = 0; total = 1
            scope.launch {
                val result = withContext(Dispatchers.IO) { HorizonResourceConverter.convertToGameDir(File(SOURCE_DIR), File(GAME_DIR), "26.3") { d, t, phase -> done = d; total = t; status = phase } }
                status = result.message; ready = result.success; busy = false
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Converting" else "Convert and install Horizon") }
        Button(onClick = { if (ready) launchHorizon(context) else status = "Convert first" }, enabled = !busy && ready, modifier = Modifier.fillMaxWidth()) { Text("Launch Horizon") }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Game folder: " + GAME_DIR, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
    }
}

private fun launchHorizon(context: Context) {
    val intent = Intent(context, Class.forName("io.github.juanluci.starling.StarlingActivity"))
    intent.putExtra("io.github.juanluci.starling.GAME_DIR", GAME_DIR)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (e: Throwable) { android.util.Log.e("HorizonConvert", "launch failed", e) }
}


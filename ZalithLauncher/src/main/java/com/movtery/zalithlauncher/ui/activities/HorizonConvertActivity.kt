/*
 * RCL - Standalone entry point for the on-device Java -> Horizon converter.
 * Registered in AndroidManifest with a launcher icon so the player can open it
 * directly. Kept separate from ZL2's main UI to avoid touching existing code.
 */
package com.movtery.zalithlauncher.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.movtery.zalithlauncher.ui.screens.content.HorizonConvertScreen

class HorizonConvertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { HorizonConvertScreen() }
            }
        }
    }
}
package com.aura.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.chat.data.AppSettings
import com.aura.chat.ui.AppNav
import com.aura.chat.ui.components.AuraBackground
import com.aura.chat.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = container.settings.flow
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
            val s = settings
            if (s != null) {
                AuraTheme(s) {
                    Box(Modifier.fillMaxSize()) {
                        AuraBackground()
                        AppNav()
                    }
                }
            }
        }
    }
}

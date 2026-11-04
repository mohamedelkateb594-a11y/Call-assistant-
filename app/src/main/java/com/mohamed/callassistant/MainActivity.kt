package com.mohamed.callassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        Notif.channels(this)
        Store.load(this)
        if (Prefs.apiKey(this).isNotBlank()) Jobs.scan(this, 1)
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        Store.load(this)
        tick++
    }
}

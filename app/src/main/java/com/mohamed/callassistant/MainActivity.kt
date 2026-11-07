package com.mohamed.callassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var locked by mutableStateOf(false)
    private var asking = false
    private var bgAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        Notif.channels(this)
        Store.load(this)
        TaskOps.rescheduleAll(this)
        if (XPrefs.morning(this)) MorningJob.schedule(this)
        if (Prefs.apiKey(this).isNotBlank()) Jobs.scan(this, 1)
        locked = XPrefs.lock(this) && AppLock.available(this)
        setContent {
            if (locked) LockScreen { unlock() } else CallAssistantApp()
        }
        if (locked) unlock()
    }

    private fun unlock() {
        if (asking) return
        asking = true
        AppLock.prompt(
            this,
            { asking = false; locked = false },
            { code ->
                asking = false
                // لو الجهاز ما فيهوش بصمة/قفل نفتح عشان ما يبقاش مقفول للأبد
                if (code == 1 || code == 11 || code == 12 || code == 14) locked = false
            }
        )
    }

    override fun onStop() {
        super.onStop()
        bgAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // لو رجعت للبرنامج بعد أكتر من 20 ثانية نقفله تاني
        if (bgAt > 0 && XPrefs.lock(this) && AppLock.available(this) &&
            System.currentTimeMillis() - bgAt > 20_000L && !locked
        ) {
            locked = true
            unlock()
        }
    }

    override fun onResume() {
        super.onResume()
        Store.load(this)
        tick++
    }
}

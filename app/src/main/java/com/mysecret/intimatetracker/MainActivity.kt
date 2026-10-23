package com.mysecret.intimatetracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mysecret.intimatetracker.data.db.SettingsDao
import com.mysecret.intimatetracker.data.sync.SyncScheduler
import com.mysecret.intimatetracker.ui.lock.AppLockGate
import com.mysecret.intimatetracker.ui.lock.AppLockViewModel
import com.mysecret.intimatetracker.ui.navigation.AppNav
import com.mysecret.intimatetracker.ui.theme.ThemedApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settings: SettingsDao

    @Inject
    lateinit var syncScheduler: SyncScheduler

    private var backgroundAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            if (settings.get(AppLockViewModel.KEY_ENABLED) == "true") {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        setContent {
            ThemedApp {
                AppLockGate(content = { AppNav() })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundAt > 0 && System.currentTimeMillis() - backgroundAt > RELOCK_AFTER_MS) {
            backgroundAt = 0L
            recreate()
        }
        // 打开 App（冷启动或从后台回来）时机会性同步，内部有 15 分钟节流
        syncScheduler.maybeSyncOnOpen()
    }
}

private const val RELOCK_AFTER_MS = 5 * 60 * 1000L

package de.limad.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS does not allow changing the default home app.
 * This service brings LiMaD-TV to the front when the Amazon home screen appears
 * or the Home key is pressed.
 *
 * Fire OS 8 hosts its settings inside com.amazon.tv.launcher, so the window class name
 * cannot tell "home" from "settings". Therefore, whenever LiMaD-TV itself opens a system
 * screen, it calls [allowSystemUi]; redirects are then paused until the user presses Home
 * or LiMaD-TV comes back to the front.
 */
class HomeButtonService : AccessibilityService() {

    private var lastLaunch = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != AMAZON_LAUNCHER) return
        val cls = event.className?.toString() ?: ""
        Log.d(TAG, "window $cls (systemUiAllowed=${systemUiAllowed()})")
        if (systemUiAllowed()) return
        if (isAmazonHome(cls)) goHome()
    }

    private fun isAmazonHome(cls: String): Boolean {
        val c = cls.lowercase()
        if (c.contains("setting") || c.contains("dialog") || c.contains("search") || c.contains("profile")) return false
        return c.contains("homeactivity") || c.endsWith(".home") || c.contains("launcheractivity") || c.contains("mainactivity")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) {
            allowUntil = 0L
            goHome()
        }
        return true
    }

    private fun goHome() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 700) return
        lastLaunch = now
        val intent = Intent(this, MainActivity::class.java)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "LimadHome"
        private const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"

        /** Same process as MainActivity, so a static flag is enough. */
        @Volatile
        private var allowUntil = 0L

        /** Call before opening any Fire OS screen; pauses redirects for up to 30 minutes. */
        fun allowSystemUi() {
            allowUntil = SystemClock.elapsedRealtime() + 30 * 60_000L
        }

        /** Call when LiMaD-TV is in front again. */
        fun endSystemUi() {
            allowUntil = 0L
        }

        private fun systemUiAllowed() = SystemClock.elapsedRealtime() < allowUntil
    }
}

package com.socialitefree.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Optional Device Admin receiver (registered only in nativeBlocker edition)
 * to prevent accidental or impulsive uninstallation while Uninstall Guard is active.
 */
class SocialLiteAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "SocialLite Uninstall Guard is active. Disable it inside SocialLite Settings if you want to uninstall."
    }
}

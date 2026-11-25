package com.socialitefree.app.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.socialitefree.app.BuildConfig
import com.socialitefree.app.receiver.SocialLiteAdminReceiver
import com.socialitefree.app.service.NativeReelsBlockerService

data class InstalledAppSummary(
    val packageName: String,
    val appName: String
)

object NativeAppHelper {

    fun findInstalledPackage(context: Context, platform: Platform): String? {
        val pm = context.packageManager
        for (pkg in platform.nativePackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                if (pm.getLaunchIntentForPackage(pkg) != null) {
                    return pkg
                }
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return null
    }

    fun launchNativeApp(context: Context, platform: Platform): Boolean {
        val pkg = findInstalledPackage(context, platform) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getInstalledLaunchableApps(context: Context): List<InstalledAppSummary> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return try {
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            resolveInfos
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
                    InstalledAppSummary(packageName = pkg, appName = label)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (!BuildConfig.SUPPORTS_NATIVE_BLOCKER) return false
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val expectedComponent = ComponentName(context, NativeReelsBlockerService::class.java)
        val expectedFlat = expectedComponent.flattenToString()
        val expectedShort = expectedComponent.flattenToShortString()

        if (am != null) {
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (service in enabledServices) {
                val id = service.id ?: continue
                if (id.equals(expectedFlat, ignoreCase = true) ||
                    id.equals(expectedShort, ignoreCase = true) ||
                    id.contains(NativeReelsBlockerService::class.java.simpleName)
                ) {
                    return true
                }
            }
        }

        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledSetting.split(':').any { entry ->
            entry.equals(expectedFlat, ignoreCase = true) ||
                entry.equals(expectedShort, ignoreCase = true) ||
                entry.contains(NativeReelsBlockerService::class.java.simpleName)
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        return true // Primary blocker overlays use Accessibility TYPE_ACCESSIBILITY_OVERLAY cleanly
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun isUninstallGuardActive(context: Context): Boolean {
        if (!BuildConfig.SUPPORTS_NATIVE_BLOCKER) return false
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: return false
            val admin = ComponentName(context, SocialLiteAdminReceiver::class.java)
            dpm.isAdminActive(admin)
        } catch (_: Exception) {
            false
        }
    }

    fun enableUninstallGuard(context: Context) {
        if (!BuildConfig.SUPPORTS_NATIVE_BLOCKER) return
        val admin = ComponentName(context, SocialLiteAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Prevents accidental or impulsive uninstallation of SocialLite while keeping focus active."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun disableUninstallGuard(context: Context) {
        if (!BuildConfig.SUPPORTS_NATIVE_BLOCKER) return
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: return
            val admin = ComponentName(context, SocialLiteAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.removeActiveAdmin(admin)
            }
        } catch (_: Exception) {
        }
    }
}

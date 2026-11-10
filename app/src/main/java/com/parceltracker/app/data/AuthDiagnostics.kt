package com.parceltracker.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.security.MessageDigest

object AuthDiagnostics {

    fun signingSha1(context: Context): String? = runCatching {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return null

        MessageDigest.getInstance("SHA-1")
            .digest(signatureBytes)
            .joinToString(":") { byte -> "%02X".format(byte) }
    }.getOrNull()

    fun playServicesStatus(context: Context): String {
        val code = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        return when (code) {
            ConnectionResult.SUCCESS -> {
                val version = runCatching {
                    context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName
                }.getOrNull()
                if (version.isNullOrBlank()) "Available" else "Available (version $version)"
            }

            ConnectionResult.SERVICE_MISSING -> "Missing (use a device or emulator with Google Play)"
            ConnectionResult.SERVICE_UPDATING -> "Updating"
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "Update required"
            ConnectionResult.SERVICE_DISABLED -> "Disabled"
            ConnectionResult.SERVICE_INVALID -> "Invalid installation"
            else -> "Unavailable (code $code)"
        }
    }

    fun playServicesAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}

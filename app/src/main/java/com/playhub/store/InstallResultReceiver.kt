package com.playhub.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

object InstallCallbackHolder {
    var onStatus: ((packageName: String, status: String, message: String) -> Unit)? = null
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        val targetPackage = intent.getStringExtra("target_package") ?: ""

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                    InstallCallbackHolder.onStatus?.invoke(targetPackage, "installing", "Tap Install to confirm")
                } else {
                    InstallCallbackHolder.onStatus?.invoke(targetPackage, "error", "Could not open installer prompt")
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                InstallCallbackHolder.onStatus?.invoke(targetPackage, "success", "Installed successfully")
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                InstallCallbackHolder.onStatus?.invoke(targetPackage, "cancelled", "Installation cancelled")
            }

            else -> {
                val msg = statusMessage.ifBlank { "Install failed (code $status)" }
                InstallCallbackHolder.onStatus?.invoke(targetPackage, "error", msg)
            }
        }
    }
}

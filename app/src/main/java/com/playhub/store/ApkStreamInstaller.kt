package com.playhub.store

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ApkStreamInstaller(
    private val context: Context,
    private val onProgress: (packageName: String, percent: Int) -> Unit,
    private val onStatus: (packageName: String, status: String, message: String) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()

    fun install(downloadUrl: String, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            onStatus(packageName, "error", "Enable 'Allow from this source' for PlayHub, then tap Install again.")
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return
        }

        executor.execute {
            var session: PackageInstaller.Session? = null
            var connection: HttpURLConnection? = null

            try {
                onStatus(packageName, "downloading", "Connecting...")
                onProgress(packageName, 0)

                val activeConnection = openConnectionWithRedirects(downloadUrl)
                connection = activeConnection

                val responseCode = activeConnection.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("Download failed (HTTP $responseCode)")
                }

                val totalBytes = activeConnection.contentLengthLong
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }

                val sessionId = installer.createSession(params)
                val activeSession = installer.openSession(sessionId)
                session = activeSession

                val sizeHint = if (totalBytes > 0L) totalBytes else -1L
                activeSession.openWrite("app.apk", 0, sizeHint).use { out ->
                    activeConnection.inputStream.use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var downloaded = 0L
                        var lastPercent = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalBytes > 0L) {
                                val percent = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(packageName, percent)
                                }
                            }
                        }
                        activeSession.fsync(out)
                    }
                }

                onProgress(packageName, 100)
                onStatus(packageName, "installing", "Preparing installer...")

                val callbackIntent = Intent(context, InstallResultReceiver::class.java).apply {
                    putExtra("target_package", packageName)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
                activeSession.commit(pendingIntent.intentSender)
                activeSession.close()
                session = null
            } catch (e: Exception) {
                try {
                    session?.abandon()
                    session?.close()
                } catch (_: Exception) {
                }
                onStatus(packageName, "error", e.message ?: "Download or install failed")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun openConnectionWithRedirects(startUrl: String, maxRedirects: Int = 5): HttpURLConnection {
        var currentUrl = URL(startUrl)
        var redirects = 0

        while (true) {
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "PlayHub-Android/1.0")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank() || redirects >= maxRedirects) {
                    throw IOException("Too many redirects")
                }
                currentUrl = URL(currentUrl, location)
                redirects++
            } else {
                return conn
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}

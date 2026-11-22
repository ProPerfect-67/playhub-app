package com.playhub.store

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    companion object {
        // Paste your hosted index.html link here (Netlify or GitHub Pages)
        private const val STORE_URL = "https://properfect-67.github.io/playhub-app/"
    }

    private lateinit var webView: WebView
    private lateinit var installer: ApkStreamInstaller
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#01875F")

        webView = WebView(this)
        setContentView(webView)

        installer = ApkStreamInstaller(
            context = applicationContext,
            onProgress = { pkg, percent -> sendProgressToWeb(pkg, percent) },
            onStatus = { pkg, status, msg -> sendStatusToWeb(pkg, status, msg) }
        )

        InstallCallbackHolder.onStatus = { pkg, status, msg ->
            sendStatusToWeb(pkg, status, msg)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "$userAgentString PlayHubApp/1.0"
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    val offlineHtml = """
                        <html>
                        <body style="font-family:sans-serif;text-align:center;padding:60px 24px;background:#111;color:#fff;">
                            <h2>No Internet Connection</h2>
                            <p style="color:#aaa;">Connect to the internet to browse PlayHub.</p>
                            <button onclick="window.location.href='$STORE_URL'"
                                style="background:#01875F;color:#fff;border:none;padding:12px 24px;border-radius:8px;font-size:16px;">
                                Retry
                            </button>
                        </body>
                        </html>
                    """.trimIndent()
                    view?.loadDataWithBaseURL(null, offlineHtml, "text/html", "UTF-8", null)
                }
            }
        }

        webView.addJavascriptInterface(StoreBridge(), "StoreBridge")
        webView.loadUrl(STORE_URL)
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post {
            webView.evaluateJavascript(
                "window.refreshAppStates && window.refreshAppStates();",
                null
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        InstallCallbackHolder.onStatus = null
        installer.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    private fun sendProgressToWeb(packageName: String, percent: Int) {
        val safePkg = escapeJs(packageName)
        mainHandler.post {
            webView.evaluateJavascript(
                "window.onInstallProgress && window.onInstallProgress('$safePkg', $percent);",
                null
            )
        }
    }

    private fun sendStatusToWeb(packageName: String, status: String, message: String) {
        val safePkg = escapeJs(packageName)
        val safeStatus = escapeJs(status)
        val safeMsg = escapeJs(message)
        mainHandler.post {
            webView.evaluateJavascript(
                "window.onInstallStatus && window.onInstallStatus('$safePkg', '$safeStatus', '$safeMsg');",
                null
            )
        }
    }

    private fun escapeJs(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", " ")
            .replace("\n", " ")
    }

    inner class StoreBridge {

        @JavascriptInterface
        fun isInstalled(packageName: String): Boolean {
            return try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        @JavascriptInterface
        fun getInstalledVersionCode(packageName: String): Long {
            return try {
                val info = packageManager.getPackageInfo(packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            } catch (_: PackageManager.NameNotFoundException) {
                -1L
            }
        }

        @JavascriptInterface
        fun installApp(downloadUrl: String, packageName: String) {
            installer.install(downloadUrl, packageName)
        }

        @JavascriptInterface
        fun openApp(packageName: String): Boolean {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return true
        }
    }
}

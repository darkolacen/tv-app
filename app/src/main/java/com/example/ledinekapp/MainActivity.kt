package com.example.ledinekapp

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var deviceIdView: TextView
    private lateinit var pollingStatusView: TextView
    private lateinit var endpointEdit: EditText
    private lateinit var endpointSaveBtn: Button
    private var isEndpointEditMode = false
    private var pollingExecutor: ScheduledExecutorService? = null
    private var pollingScheduled = false

    data class RemoteConfig(val url: String?, val refreshRateMs: Long, val jsZoom: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.web_view)
        deviceIdView = findViewById(R.id.device_id_view)
        pollingStatusView = findViewById(R.id.polling_status_view)
        endpointEdit = findViewById(R.id.endpoint_edit)
        endpointSaveBtn = findViewById(R.id.endpoint_save_btn)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                applyTvZoom(view)
                super.onPageFinished(view, url)
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({ applyTvZoom(webView) }, 1500)
                handler.postDelayed({ applyTvZoom(webView) }, 3000)
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.settings.javaScriptEnabled = true

        val deviceId = getOrCreateDeviceId()
        deviceIdView.text = deviceId

        endpointSaveBtn.setOnClickListener { onEndpointButtonClick() }

        val savedUrl = getPrefs().getString(KEY_LAST_URL, null)
        if (!savedUrl.isNullOrBlank()) {
            showWebView(savedUrl)
        } else {
            showDeviceIdOnly()
        }
    }

    private fun showDeviceIdOnly() {
        deviceIdView.visibility = View.VISIBLE
        pollingStatusView.visibility = View.VISIBLE
        findViewById<View>(R.id.endpoint_label).visibility = View.VISIBLE
        endpointEdit.visibility = View.VISIBLE
        endpointSaveBtn.visibility = View.VISIBLE
        endpointEdit.setText(getEndpointBase())
        setEndpointViewMode()
        webView.visibility = View.GONE
    }

    private fun onEndpointButtonClick() {
        if (isEndpointEditMode) {
            saveEndpointAndRestartPolling()
            setEndpointViewMode()
        } else {
            setEndpointEditMode()
        }
    }

    private fun setEndpointViewMode() {
        isEndpointEditMode = false
        endpointEdit.isFocusable = false
        endpointEdit.isFocusableInTouchMode = false
        endpointEdit.isCursorVisible = false
        endpointSaveBtn.text = "EDIT ENDPOINT"
    }

    private fun setEndpointEditMode() {
        isEndpointEditMode = true
        endpointEdit.isFocusable = true
        endpointEdit.isFocusableInTouchMode = true
        endpointEdit.isCursorVisible = true
        endpointEdit.requestFocus()
        endpointSaveBtn.text = "Save"
    }

    private fun saveEndpointAndRestartPolling() {
        val base = endpointEdit.text?.toString()?.trim() ?: return
        if (base.isBlank()) return
        getPrefs().edit().putString(KEY_ENDPOINT_BASE, base).apply()
        stopPolling()
        startPolling()
        Toast.makeText(this, "Endpoint updated", Toast.LENGTH_SHORT).show()
    }

    private fun updatePollingStatus(success: Boolean, timestampMillis: Long) {
        if (webView.visibility == View.VISIBLE) return
        pollingStatusView.visibility = View.VISIBLE
        findViewById<View>(R.id.endpoint_label).visibility = View.VISIBLE
        endpointEdit.visibility = View.VISIBLE
        endpointSaveBtn.visibility = View.VISIBLE
        if (!isEndpointEditMode) {
            endpointEdit.setText(getEndpointBase())
            setEndpointViewMode()
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
        val status = if (success) "successful" else "failed"
        pollingStatusView.text = "Polling: $status\nLast request: $time"
    }

    private fun showWebView(url: String) {
        deviceIdView.visibility = View.GONE
        pollingStatusView.visibility = View.GONE
        findViewById<View>(R.id.endpoint_label).visibility = View.GONE
        endpointEdit.visibility = View.GONE
        endpointSaveBtn.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    override fun onStart() {
        super.onStart()
        startPolling()
    }

    override fun onStop() {
        super.onStop()
        stopPolling()
    }

    private fun startPolling() {
        if (pollingScheduled) return
        pollingScheduled = true
        schedulePoll(0)
    }

    private fun schedulePoll(delayMs: Long = getRefreshRateMs()) {
        if (pollingExecutor == null) {
            pollingExecutor = Executors.newSingleThreadScheduledExecutor()
        }
        val deviceId = getOrCreateDeviceId()
        pollingExecutor?.schedule(
            {
                val (success, config) = fetchRemoteConfig(deviceId)
                val timestamp = System.currentTimeMillis()
                runOnUiThread {
                    if (config != null) {
                        val currentSaved = getPrefs().getString(KEY_LAST_URL, null)
                        getPrefs().edit()
                            .putString(KEY_LAST_URL, config.url)
                            .putLong(KEY_REFRESH_RATE, config.refreshRateMs)
                            .putString(KEY_JS_ZOOM, config.jsZoom)
                            .apply()
                        if (!config.url.isNullOrBlank() && config.url != currentSaved) {
                            showWebView(config.url)
                        }
                    } else {
                        updatePollingStatus(success, timestamp)
                    }
                }
                schedulePoll()
            },
            delayMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopPolling() {
        pollingScheduled = false
        pollingExecutor?.shutdownNow()
        pollingExecutor = null
    }

    private fun getOrCreateDeviceId(): String {
        val prefs = getPrefs()
        val existingId = prefs.getString(KEY_DEVICE_ID, null)
        if (!existingId.isNullOrBlank()) return existingId

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    private fun getEndpointBase(): String =
        getPrefs().getString(KEY_ENDPOINT_BASE, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_ENDPOINT_BASE

    /** @return Pair(success, config with url/refreshRate/jsZoom or null on failure) */
    private fun fetchRemoteConfig(deviceId: String): Pair<Boolean, RemoteConfig?> {
        val base = getEndpointBase().trimEnd('/')
        val endpoint = "$base/$deviceId"
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            true to extractConfig(body)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch config from $endpoint", e)
            false to null
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractConfig(responseBody: String): RemoteConfig? {
        val trimmed = responseBody.trim()
        if (!trimmed.startsWith("{")) return null
        return try {
            val json = JSONObject(trimmed)
            val url = normalizeValidUrl(json.optString("url", "").trim().takeIf { it.isNotBlank() })
            val refreshRateMs = json.optLong("refreshRate", 5000L).coerceIn(1000L, 300_000L)
            val jsZoom = json.optString("jsZoom", "").trim().takeIf { it.isNotBlank() }
            RemoteConfig(url, refreshRateMs, jsZoom)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeValidUrl(candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        val value = candidate.trim()
        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun getPrefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getRefreshRateMs(): Long =
        getPrefs().getLong(KEY_REFRESH_RATE, DEFAULT_REFRESH_RATE_MS)

    private fun getJsZoom(): String? =
        getPrefs().getString(KEY_JS_ZOOM, null)?.takeIf { it.isNotBlank() }

    /** Applies the TV zoom script from API (jsZoom) or default. */
    private fun applyTvZoom(view: WebView?) {
        val script = getJsZoom() ?: DEFAULT_JS_ZOOM
        view?.loadUrl(script)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "ledinek_app_prefs"
        private const val KEY_DEVICE_ID = "unique_device_id"
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_REFRESH_RATE = "refresh_rate_ms"
        private const val KEY_JS_ZOOM = "js_zoom"
        private const val DEFAULT_REFRESH_RATE_MS = 5000L
        private const val DEFAULT_JS_ZOOM = "javascript:document.body.style.zoom=1/window.devicePixelRatio;"
        private const val KEY_ENDPOINT_BASE = "endpoint_base"
        private const val DEFAULT_ENDPOINT_BASE = "http://192.168.252.163:8081/ledinek_app/administracija/tv"
    }
}

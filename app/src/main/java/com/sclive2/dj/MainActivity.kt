package com.sclive2.dj

import android.Manifest
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.ContentValues
import android.os.PowerManager
import android.os.SystemClock
import android.util.Base64
import android.content.IntentFilter
import android.provider.Settings
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.BatteryManager
import android.provider.MediaStore
import java.io.ByteArrayInputStream
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import androidx.media3.common.util.UnstableApi

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var playbackService: ScLivePlaybackService? = null
    private var playbackBound = false
    private val playbackConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) {
            playbackService = (binder as? ScLivePlaybackService.LocalBinder)?.service
        }
        override fun onServiceDisconnected(name: ComponentName?) { playbackService = null }
    }
    private fun startPlaybackForeground() {
        val intent = Intent(this, ScLivePlaybackService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } catch (_: Exception) { }
    }
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var browserDialog: Dialog? = null
    private var browserWebView: WebView? = null
    private var browserTargetDeck = 0
    // Deck que mantiene activa la sesión del navegador mientras se usa la biblioteca local.
    private var browserPinnedDeck: Int? = null
    private var networkLabel: TextView? = null
    private var batteryLabel: TextView? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var powerStateReceiver: BroadcastReceiver? = null
    private var lastCpuTime = android.os.Process.getElapsedCpuTime()
    private var lastCpuWall = SystemClock.elapsedRealtime()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val networkUpdater = object : Runnable {
        override fun run() {
            networkLabel?.let { label ->
                val state = networkState()
                label.text = state.first
                label.setTextColor(state.second)
            }
            if (browserDialog?.isShowing == true) uiHandler.postDelayed(this, 2000)
        }
    }

    private val nativeDspPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == RESULT_OK) result.data?.data else null
        if (uri == null) return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val title = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: "Audio DSP"
        } catch (_: Exception) { "Audio DSP" }
        startPlaybackForeground()
        val ok = playbackService?.player?.play(uri.toString(), title) == true
        webView.evaluateJavascript("window.onNativeDspStart && window.onNativeDspStart($ok);", null)
    }

    private val browserAudioPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == RESULT_OK) result.data?.data else null
        if (uri == null) return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val title = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: "Audio seleccionado"
        } catch (_: Exception) { "Audio seleccionado" }
        browserDialog?.hide()
        val js = "window.loadNativeTrack && window.loadNativeTrack({uri:${JSONObject.quote(uri.toString())},title:${JSONObject.quote(title)}},$browserTargetDeck);"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private fun audioPermission(): String =
        if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifyAudioPermission(granted) }

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult
        if (result.resultCode != RESULT_OK) {
            callback.onReceiveValue(null)
            return@registerForActivityResult
        }
        val data = result.data
        val uris = mutableListOf<Uri>()
        data?.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) uris.add(clip.getItemAt(index).uri)
        }
        if (uris.isEmpty()) data?.data?.let { uris.add(it) }
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
        }
        callback.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    private fun notifyAudioPermission(granted: Boolean) {
        if (!::webView.isInitialized) return
        webView.post {
            webView.evaluateJavascript("window.onNativeAudioPermission && window.onNativeAudioPermission($granted);", null)
        }
    }

    inner class AudioBridge {
        @JavascriptInterface
        fun playNativeDsp(uri: String, title: String) {
            runOnUiThread {
                startPlaybackForeground()
                val ok = playbackService?.player?.play(uri, title) == true
                webView.evaluateJavascript("window.onNativeDspStart && window.onNativeDspStart($ok);", null)
            }
        }
        @JavascriptInterface fun openNativeDspPicker() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                try { nativeDspPicker.launch(intent) } catch (_: Exception) {
                    webView.evaluateJavascript("window.onNativeDspStart && window.onNativeDspStart(false);", null)
                }
            }
        }
        @JavascriptInterface fun pauseNativeDsp() { runOnUiThread { playbackService?.player?.pause() } }
        @JavascriptInterface fun resumeNativeDsp() { runOnUiThread { playbackService?.player?.resume() } }
        @JavascriptInterface fun stopNativeDsp() { runOnUiThread { playbackService?.let { it.player.stop(); it.stopPlaybackForeground() } } }
        @JavascriptInterface fun seekNativeDspBy(deltaMs: Long) { runOnUiThread { playbackService?.player?.seekBy(deltaMs) } }
        @JavascriptInterface fun seekNativeDspTo(positionMs: Long) { runOnUiThread { playbackService?.player?.seekTo(positionMs) } }
        @JavascriptInterface fun getNativeDspStatus(): String = playbackService?.player?.status() ?: JSONObject().put("state", "UNAVAILABLE").toString()
        @JavascriptInterface
        fun setNativeDspSettings(json: String) {
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
            val current = playbackService?.player?.dsp?.currentSettings() ?: return
            val gainsJson = obj.optJSONArray("outputGains")
            val gains = List(6) { index ->
                if (gainsJson != null && index < gainsJson.length()) {
                    gainsJson.optDouble(index, 0.0).toFloat()
                } else {
                    current.outputGainsDb.getOrElse(index) { 0f }
                }
            }
            playbackService?.player?.dsp?.updateSettings(current.copy(
                enabled = obj.optBoolean("enabled", current.enabled),
                outputGainsDb = gains,
                masterDb = obj.optDouble("masterDb", current.masterDb.toDouble()).toFloat(),
                limiterEnabled = obj.optBoolean("limiterEnabled", current.limiterEnabled)
            ))
        }

        @JavascriptInterface
        fun confirmRecording() {
            runOnUiThread {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("SC LIVE 2 · REC")
                    .setMessage("¿Deseas grabar tu sesión en vivo? Se guardará únicamente el audio de los dos decks.")
                    .setPositiveButton("Sí") { _, _ ->
                        webView.evaluateJavascript("window.startRecordingConfirmed && window.startRecordingConfirmed();", null)
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        }

        @JavascriptInterface
        fun requestAudioPermission() {
            runOnUiThread {
                if (checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED) {
                    notifyAudioPermission(true)
                } else {
                    audioPermissionLauncher.launch(audioPermission())
                }
            }
        }

        @JavascriptInterface
        fun getAudioTracks(): String {
            if (checkSelfPermission(audioPermission()) != PackageManager.PERMISSION_GRANTED) return "[]"
            val tracks = JSONArray()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION
            )
            try {
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val item = JSONObject()
                        item.put("uri", Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()).toString())
                        item.put("title", cursor.getString(titleCol) ?: "Audio sin título")
                        item.put("artist", cursor.getString(artistCol) ?: "")
                        item.put("album", cursor.getString(albumCol) ?: "")
                        item.put("duration", cursor.getLong(durationCol))
                        tracks.put(item)
                    }
                }
            } catch (_: Exception) { }
            return tracks.toString()
        }

        @JavascriptInterface
        fun getPlayableUrl(uriText: String): String =
            "https://sc-audio.local/audio?uri=" + Uri.encode(uriText)


        @JavascriptInterface
        fun beginRecording(fileName: String): Boolean = synchronized(recordingLock) {
            try {
                closeRecordingLocked(deleteIncomplete = true)
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").let {
                    if (it.endsWith(".webm", ignoreCase = true)) it else "$it.webm"
                }
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, safeName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/webm")
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SC LIVE 2/Grabaciones")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                }
                val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return@synchronized false
                val stream = contentResolver.openOutputStream(uri, "w")
                if (stream == null) {
                    contentResolver.delete(uri, null, null)
                    return@synchronized false
                }
                recordingUri = uri
                recordingStream = stream
                true
            } catch (_: Exception) {
                closeRecordingLocked(deleteIncomplete = true)
                false
            }
        }

        @JavascriptInterface
        fun appendRecordingChunk(base64Chunk: String): Boolean = synchronized(recordingLock) {
            try {
                val stream = recordingStream ?: return@synchronized false
                val bytes = Base64.decode(base64Chunk, Base64.DEFAULT)
                stream.write(bytes)
                true
            } catch (_: Exception) {
                closeRecordingLocked(deleteIncomplete = true)
                false
            }
        }

        @JavascriptInterface
        fun finishRecording(): Boolean = synchronized(recordingLock) {
            val uri = recordingUri ?: return@synchronized false
            try {
                recordingStream?.flush()
                recordingStream?.close()
                recordingStream = null
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val ready = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                    contentResolver.update(uri, ready, null, null)
                }
                recordingUri = null
                true
            } catch (_: Exception) {
                closeRecordingLocked(deleteIncomplete = true)
                false
            }
        }

        @JavascriptInterface
        fun cancelRecording() = synchronized(recordingLock) {
            closeRecordingLocked(deleteIncomplete = true)
        }

        private val recordingLock = Any()
        private var recordingUri: Uri? = null
        private var recordingStream: java.io.OutputStream? = null

        private fun closeRecordingLocked(deleteIncomplete: Boolean) {
            try { recordingStream?.close() } catch (_: Exception) { }
            recordingStream = null
            val uri = recordingUri
            recordingUri = null
            if (deleteIncomplete && uri != null) {
                try { contentResolver.delete(uri, null, null) } catch (_: Exception) { }
            }
        }

        @JavascriptInterface
        fun getRankDeviceStatus(): String =
            RankDeviceAssessment.assess(this@MainActivity)

        @JavascriptInterface
        fun getDeviceStats(): String {
            val nowCpu = android.os.Process.getElapsedCpuTime()
            val nowWall = SystemClock.elapsedRealtime()
            val elapsedCpu = (nowCpu - lastCpuTime).coerceAtLeast(0)
            val elapsedWall = (nowWall - lastCpuWall).coerceAtLeast(1)
            lastCpuTime = nowCpu
            lastCpuWall = nowWall
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val cpu = ((elapsedCpu.toDouble() / elapsedWall / cores) * 100.0).toInt().coerceIn(0, 100)
            val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                when (pm.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> "CRÍTICA"
                    PowerManager.THERMAL_STATUS_SEVERE -> "ALTA"
                    PowerManager.THERMAL_STATUS_MODERATE -> "MODERADA"
                    else -> "NORMAL"
                }
            } else "No disponible en esta versión de Android"
            return JSONObject().put("cpu", cpu).put("thermal", thermal).toString()
        }

        @JavascriptInterface fun openWifiSettings() { runOnUiThread { try { if (android.os.Build.VERSION.SDK_INT >= 29) startActivity(Intent(Settings.Panel.ACTION_WIFI)) else startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } } }
        @JavascriptInterface fun openOnlineBrowser() { runOnUiThread { showOnlineBrowser() } }
        @JavascriptInterface fun refreshOnlineBrowser() { runOnUiThread { refreshOnlineBrowserScreen() } }
        @JavascriptInterface fun chooseBrowserAudio(deck: Int) { runOnUiThread { openBrowserAudioPicker(deck.coerceIn(0, 1)) } }
        @JavascriptInterface fun exitApp() { runOnUiThread { finish() } }
    }


    private fun refreshOnlineBrowserScreen() {
        val browser = browserWebView
        if (browserDialog?.isShowing != true || browser == null) {
            showOnlineBrowser()
            return
        }
        browser.animate().cancel()
        browser.animate().alpha(0.35f).setDuration(90).withEndAction {
            browser.animate().alpha(1f).setDuration(150).start()
        }.start()
        browser.reload()
    }

    private fun openBrowserAudioPicker(deck: Int) {
        browserTargetDeck = deck
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { browserAudioPicker.launch(intent) } catch (_: Exception) { }
    }

    private fun addBrowserButton(row: LinearLayout, label: String, action: () -> Unit, accent: Int = Color.rgb(48, 52, 61)) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundColor(accent)
            setOnClickListener { action() }
        }
        row.addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showOnlineBrowser() {
        if (browserDialog != null && browserWebView != null) {
            browserDialog?.show()
            uiHandler.removeCallbacks(networkUpdater)
            uiHandler.post(networkUpdater)
            return
        }
        val dialog = Dialog(this)
        dialog.setCancelable(false)
        browserDialog = dialog
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 13, 16))
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "GOOGLE · SC LIVE 2"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, dp(5), 0)
        }
        top.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        networkLabel = TextView(this).apply { textSize = 11f; setPadding(dp(4), 0, dp(4), 0) }
        top.addView(networkLabel)
        batteryLabel = TextView(this).apply {
            text = "🔋 --%"
            setTextColor(Color.rgb(110, 230, 145))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), 0, dp(8), 0)
        }
        top.addView(batteryLabel)
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val percent = (level * 100f / scale).toInt().coerceIn(0, 100)
                    batteryLabel?.text = "🔋 $percent%"
                    batteryLabel?.setTextColor(when {
                        percent <= 15 -> Color.rgb(245, 75, 75)
                        percent <= 30 -> Color.rgb(245, 190, 65)
                        else -> Color.rgb(110, 230, 145)
                    })
                }
            }
        }
        val stickyBattery = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        stickyBattery?.let { batteryReceiver?.onReceive(this, it) }
        root.addView(top)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addBrowserButton(actions, "← ATRÁS", {
            val current = browserWebView
            if (current?.canGoBack() == true) current.goBack() else {
                if (browserPinnedDeck != null) dialog.hide() else dialog.dismiss()
            }
        })
        addBrowserButton(actions, "DECK 1", {
            browserPinnedDeck = 0
            dialog.hide()
        }, Color.rgb(28, 125, 76))
        addBrowserButton(actions, "DECK 2", {
            browserPinnedDeck = 1
            dialog.hide()
        }, Color.rgb(28, 125, 76))
        addBrowserButton(actions, "BIBLIOTECA", {
            if (browserPinnedDeck != null) dialog.hide() else dialog.dismiss()
            webView.evaluateJavascript("window.openLibrary && window.openLibrary(0);", null)
        })
        addBrowserButton(actions, "CERRAR", {
            browserPinnedDeck = null
            dialog.dismiss()
        }, Color.rgb(105, 39, 43))
        root.addView(actions)
        val browser = WebView(this).apply {
            setBackgroundColor(Color.rgb(17, 18, 22))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        browserWebView = browser
        root.addView(browser, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        dialog.setContentView(root)
        dialog.setOnDismissListener {
            uiHandler.removeCallbacks(networkUpdater)
            batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) { } }
            batteryReceiver = null
            batteryLabel = null
            browser.stopLoading()
            browser.destroy()
            browserWebView = null
            networkLabel = null
            browserDialog = null
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.62f).toInt())
            setGravity(Gravity.BOTTOM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        browser.loadUrl("https://www.google.com/")
        uiHandler.post(networkUpdater)
    }

    private fun networkState(): Pair<String, Int> {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        if (!connected) return "SIN CONEXIÓN" to Color.LTGRAY
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            @Suppress("DEPRECATION")
            val rssi = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).connectionInfo.rssi
            val bars = when {
                rssi >= -60 -> "▮▮▮▮"
                rssi >= -70 -> "▮▮▮"
                rssi >= -80 -> "▮▮"
                rssi > -100 -> "▮"
                else -> "—"
            }
            val color = if (!validated || rssi < -80) Color.rgb(235, 75, 68) else if (rssi < -70) Color.rgb(242, 190, 55) else Color.rgb(68, 205, 115)
            return "WIFI $bars" to color
        }
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
            return (if (validated) "DATOS MÓVILES ▮▮▮" else "DATOS · SIN INTERNET") to
                (if (validated) Color.rgb(68, 205, 115) else Color.rgb(235, 75, 68))
        }
        return (if (validated) "INTERNET ACTIVO" else "RED LIMITADA") to
            (if (validated) Color.rgb(68, 205, 115) else Color.rgb(242, 190, 55))
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackBound = bindService(Intent(this, ScLivePlaybackService::class.java), playbackConnection, Context.BIND_AUTO_CREATE)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(14, 15, 18))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.setSupportZoom(false)
            addJavascriptInterface(AudioBridge(), "NativeAudio")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url ?: return null
                    if (url.host == "sc-audio.local") {
                        val uri = try { Uri.parse(url.getQueryParameter("uri") ?: return null) } catch (_: Exception) { return null }
                        if (uri.scheme != "content") {
                            return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        }
                        return try {
                            val stream = contentResolver.openInputStream(uri)
                                ?: return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            val mime = contentResolver.getType(uri) ?: "audio/mpeg"
                            WebResourceResponse(mime, null, 200, "OK", mapOf(
                                "Access-Control-Allow-Origin" to "*",
                                "Cache-Control" to "no-store"
                            ), stream)
                        } catch (_: Exception) {
                            WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return !url.startsWith("file:///android_asset/") &&
                        !url.startsWith("https://fonts.googleapis.com/") &&
                        !url.startsWith("https://fonts.gstatic.com/")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    view: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                    return try {
                        audioPicker.launch(intent)
                        true
                    } catch (_: Exception) {
                        this@MainActivity.filePathCallback = null
                        filePathCallback?.onReceiveValue(null)
                        false
                    }
                }
            }
        }
        setContentView(webView)
        powerStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val connected = when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> true
                    Intent.ACTION_POWER_DISCONNECTED -> false
                    else -> return
                }
                if (::webView.isInitialized) {
                    webView.post {
                        webView.evaluateJavascript(
                            "window.onPowerConnectionChanged && window.onPowerConnectionChanged($connected);",
                            null
                        )
                    }
                }
            }
        }
        registerReceiver(
            powerStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        )
        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        AlertDialog.Builder(this).setMessage("¿Estás seguro de que deseas salir de SC LIVE 2?")
            .setPositiveButton("Sí") { _, _ ->
                if (::webView.isInitialized) {
                    webView.evaluateJavascript("window.prepareAudioForExit && window.prepareAudioForExit();", null)
                    webView.postDelayed({ finish() }, 140)
                } else {
                    finish()
                }
            }
            .setNegativeButton("No", null).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        powerStateReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) { } }
        powerStateReceiver = null
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        browserDialog?.dismiss()
        uiHandler.removeCallbacks(networkUpdater)
        webView.stopLoading()
        webView.destroy()
        if (playbackBound) { try { unbindService(playbackConnection) } catch (_: Exception) { } }
        playbackBound = false
        playbackService = null
        super.onDestroy()
    }
}

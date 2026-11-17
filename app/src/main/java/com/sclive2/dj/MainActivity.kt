package com.sclive2.dj

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

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

        @JavascriptInterface fun openWifiSettings() { runOnUiThread { try { if (android.os.Build.VERSION.SDK_INT >= 29) startActivity(Intent(Settings.Panel.ACTION_WIFI)) else startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } } }
        @JavascriptInterface fun openOnlineBrowser() { runOnUiThread { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=descargar+musica"))) } catch (_: Exception) { } } }
        @JavascriptInterface fun exitApp() { runOnUiThread { finish() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        AlertDialog.Builder(this).setMessage("¿Estás seguro de que deseas salir de SC LIVE 2?")
            .setPositiveButton("Sí") { _, _ -> finish() }
            .setNegativeButton("No", null).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}

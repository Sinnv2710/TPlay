package app.tplay.ui.browse

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowseScreen(onMagnet: (String) -> Unit, viewModel: BrowseViewModel = hiltViewModel()) {
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var web by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) { web?.goBack() }
    DisposableEffect(Unit) {
        onDispose {
            web?.let {
                viewModel.lastUrl = it.url ?: viewModel.lastUrl
                it.stopLoading()
                it.destroy()
            }
            web = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { web?.goBack() }, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = { web?.loadUrl(viewModel.startPage()) }) {
                Icon(Icons.Filled.Home, contentDescription = "Home")
            }
            IconButton(onClick = { web?.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
            }
            Text(host, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
        }
        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    if (app.tplay.BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    addJavascriptInterface(MagnetBridge(onMagnet), JS_BRIDGE_NAME)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            Log.d("TPlayBrowse", "navigate scheme=${request.url.scheme} gesture=${request.hasGesture()}")
                            if (isMagnet(url)) {
                                onMagnet(url)
                                return true
                            }
                            return !isWebUrl(url)
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
                            view.evaluateJavascript(MAGNET_HOOK_JS, null)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript(MAGNET_HOOK_JS, null)
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            canGoBack = view.canGoBack()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress / 100f
                        }

                        /** Pop-up ads: open nothing, but still catch a magnet link a pop-up would carry. */
                        override fun onCreateWindow(
                            view: WebView,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message,
                        ): Boolean {
                            val probe = WebView(view.context)
                            probe.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                    val url = r.url.toString()
                                    if (isMagnet(url)) onMagnet(url)
                                    v.destroy()
                                    return true
                                }
                            }
                            (resultMsg.obj as WebView.WebViewTransport).webView = probe
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    loadUrl(viewModel.lastUrl)
                    web = this
                }
            },
        )
    }
}

/** Called from page JavaScript. Only a valid magnet link is passed on; anything else is ignored. */
private class MagnetBridge(private val onMagnet: (String) -> Unit) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun magnet(link: String?) {
        val value = link?.trim().orEmpty()
        if (value.length > MAX_LINK || !isMagnet(value)) return
        main.post { onMagnet(value) }
    }

    private companion object {
        const val MAX_LINK = 4_000
    }
}

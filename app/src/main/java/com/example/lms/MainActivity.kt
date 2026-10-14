package com.example.lms

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import com.example.lms.ui.theme.LMSTheme

private const val LMS_URL = "https://lms-iota-seven.vercel.app/"

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LMSTheme {
                LMSWebView(
                    url = LMS_URL,
                    savedState = savedInstanceState,
                    onWebViewCreated = { webView = it }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        webView?.resumeTimers()
    }

    override fun onPause() {
        webView?.onPause()
        webView?.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LMSWebView(
    url: String,
    savedState: Bundle?,
    onWebViewCreated: (WebView) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = currentWebView?.canGoBack() == true) {
        currentWebView?.goBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    currentWebView = this
                    onWebViewCreated(this)

                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.apply {
                        javaScriptEnabled = true // Required by the LMS web application.
                        domStorageEnabled = true
                        loadsImagesAutomatically = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        allowFileAccess = false
                        allowContentAccess = true
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                            loading = true
                            loadError = false
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            loading = false
                            progress = 100
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                loading = false
                                loadError = true
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri: Uri = request?.url ?: return false
                            // Keep the LMS site and its subdomains in-app. Open unrelated links externally.
                            val host = uri.host.orEmpty().lowercase()
                            val isLmsHost = host == "lms-iota-seven.vercel.app" ||
                                host.endsWith(".vercel.app") ||
                                host == "juet.ac.in" || host.endsWith(".juet.ac.in")
                            if (uri.scheme == "https" && isLmsHost) return false
                            if (uri.scheme == "http" || uri.scheme == "https") {
                                return try {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                                    true
                                } catch (_: android.content.ActivityNotFoundException) {
                                    false
                                }
                            }
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            loading = newProgress < 100
                        }
                    }

                    val restored = savedState?.let { restoreState(it) } != null
                    if (!restored) loadUrl(url)
                }
            },
            update = { view -> currentWebView = view }
        )

        if (loading && !loadError) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(progress = { (progress.coerceIn(0, 100) / 100f).coerceAtLeast(0.08f) })
                Text(
                    text = if (progress > 0) "Loading LMS… $progress%" else "Connecting to LMS…",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (loadError) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Unable to load LMS. Check your internet connection and try again.")
                Button(onClick = {
                    loadError = false
                    loading = true
                    progress = 0
                    currentWebView?.reload() ?: currentWebView?.loadUrl(url)
                }) {
                    Text("Retry")
                }
            }
        }
    }
}

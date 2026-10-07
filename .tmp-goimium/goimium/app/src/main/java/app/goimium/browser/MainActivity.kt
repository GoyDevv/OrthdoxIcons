package app.goimium.browser

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Goimium Lite - a desktop-identity browser on the system WebView.
 *
 * The WebView engine IS Chromium, so this is genuinely a Chromium browser; what it lacks
 * versus Goimium Full is the ability to change engine internals, which is why the identity
 * work here is layered rather than compiled in. See docs/IDENTITY.md.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: GoimiumWebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.url_bar)
        progress = findViewById(R.id.progress)

        configureEngine()
        installIdentityScript()
        installInputBridge()
        wireChrome()

        web.loadUrlAsDesktop(savedInstanceState?.getString("url") ?: DesktopIdentity.HOME_PAGE)
    }

    private fun configureEngine() = with(web.settings) {
        userAgentString = DesktopIdentity.USER_AGENT
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(false)
        mediaPlaybackRequiresUserGesture = false
        useWideViewPort = true
        loadWithOverviewMode = true
        layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        textZoom = 100
        builtInZoomControls = true
        displayZoomControls = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        allowFileAccess = false
        allowContentAccess = false
        cacheMode = WebSettings.LOAD_DEFAULT
    }

    private fun installIdentityScript() {
        val js = assets.open("desktop_identity.js").bufferedReader().use { it.readText() }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, js, setOf("*"))
        } else {
            web.webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, url: String, icon: android.graphics.Bitmap?) {
                    v.evaluateJavascript(js, null)
                }
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.settings, true)
        }
    }

    private fun WebView.loadUrlAsDesktop(url: String) {
        loadUrl(normalize(url), DesktopIdentity.CLIENT_HINTS)
    }

    private fun normalize(input: String): String {
        val s = input.trim()
        return when {
            s.startsWith("http://") || s.startsWith("https://") || s.startsWith("about:") -> s
            s.contains('.') && !s.contains(' ') -> "https://$s"
            else -> "https://www.google.com/search?q=" + android.net.Uri.encode(s)
        }
    }

    private fun installInputBridge() {
        web.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun requestPointerCapture(): Boolean {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !web.hasRecentUserGesture()) {
                    return false
                }
                web.post { web.enablePointerLock() }
                return true
            }

            @android.webkit.JavascriptInterface
            fun releasePointerCapture() {
                web.post { web.disablePointerLock() }
            }
        }, "GoimiumInput")

        val js = assets.open("desktop_input.js").bufferedReader().use { it.readText() }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, js, setOf("*"))
        } else {
            web.webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, url: String, icon: android.graphics.Bitmap?) {
                    v.evaluateJavascript(js, null)
                }
            }
        }
    }

    private fun wireChrome() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                if (!req.isForMainFrame) return false
                val scheme = req.url.scheme ?: return false
                if (scheme != "http" && scheme != "https") return false
                v.loadUrl(req.url.toString(), DesktopIdentity.CLIENT_HINTS)
                return true
            }

            override fun doUpdateVisitedHistory(v: WebView, url: String, reload: Boolean) {
                urlBar.setText(url)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        urlBar.setOnEditorActionListener { _, _, _ ->
            web.loadUrlAsDesktop(urlBar.text.toString())
            urlBar.clearFocus()
            true
        }

        findViewById<View>(R.id.btn_back).setOnClickListener {
            if (web.canGoBack()) web.goBack()
        }
        findViewById<View>(R.id.btn_forward).setOnClickListener {
            if (web.canGoForward()) web.goForward()
        }
        findViewById<View>(R.id.btn_reload).setOnClickListener { web.reload() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && web.pointerLockActive) {
            web.disablePointerLock(notifyPage = true)
            return true
        }

        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_R -> { web.reload(); return true }
                KeyEvent.KEYCODE_L -> { urlBar.requestFocus(); urlBar.selectAll(); return true }
                KeyEvent.KEYCODE_LEFT_BRACKET -> {
                    if (web.canGoBack()) web.goBack()
                    return true
                }
                KeyEvent.KEYCODE_RIGHT_BRACKET -> {
                    if (web.canGoForward()) web.goForward()
                    return true
                }
                KeyEvent.KEYCODE_MINUS -> { web.zoomOut(); return true }
                KeyEvent.KEYCODE_EQUALS -> { web.zoomIn(); return true }
                KeyEvent.KEYCODE_0 -> { web.settings.textZoom = 100; return true }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_F5) {
            web.reload()
            return true
        }
        if (event.isAltPressed && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (web.canGoBack()) web.goBack()
            return true
        }
        if (event.isAltPressed && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (web.canGoForward()) web.goForward()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::web.isInitialized) web.disablePointerLock(notifyPage = true)
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.disablePointerLock(notifyPage = false)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("url", web.url)
    }
}

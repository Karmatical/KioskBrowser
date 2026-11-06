package com.arthursacomori.kioskbrowser

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.arthursacomori.kioskbrowser.data.SettingsManager

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var btnAdmin: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var layoutError: View
    private lateinit var tvErrorTitle: TextView
    private lateinit var tvErrorDetail: TextView
    private lateinit var btnRetry: Button
    private lateinit var btnErrorAdmin: Button
    private lateinit var settingsManager: SettingsManager

    private var volumeDownPressCount = 0
    private var lastVolumeDownTime = 0L
    private var hasError = false

    private val closeSystemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            @Suppress("DEPRECATION")
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                hideSystemUI()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        applyLockscreenFlags()

        setContentView(R.layout.activity_main)

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility: Int ->
            if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                hideSystemUI()
            }
        }

        settingsManager = SettingsManager(this)

        webView = findViewById(R.id.webView)
        val topTouchInterceptor = findViewById<View>(R.id.topTouchInterceptor)
        topTouchInterceptor.setOnTouchListener { _, _ -> true }
        btnAdmin = findViewById(R.id.btnAdmin)
        btnRefresh = findViewById(R.id.btnRefresh)
        layoutError = findViewById(R.id.layoutError)
        tvErrorTitle = findViewById(R.id.tvErrorTitle)
        tvErrorDetail = findViewById(R.id.tvErrorDetail)
        btnRetry = findViewById(R.id.btnRetry)
        btnErrorAdmin = findViewById(R.id.btnErrorAdmin)

        setupWebView()

        btnAdmin.setOnClickListener {
            showAdminAuthDialog()
        }

        btnRefresh.setOnClickListener {
            reloadWebView()
        }

        btnRetry.setOnClickListener {
            reloadWebView()
        }

        btnErrorAdmin.setOnClickListener {
            showAdminAuthDialog()
        }

        handleReloadIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReloadIntent(intent)
    }

    private fun handleReloadIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("EXTRA_RELOAD", false) == true) {
            intent.removeExtra("EXTRA_RELOAD")
            hasError = false
            layoutError.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(settingsManager.targetUrl)
        }
    }

    private fun reloadWebView() {
        hasError = false
        layoutError.visibility = View.GONE
        webView.visibility = View.VISIBLE
        if (!webView.url.isNullOrEmpty()) {
            webView.reload()
        } else {
            webView.loadUrl(settingsManager.targetUrl)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyLockscreenFlags()
    }

    private fun applyLockscreenFlags() {
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }

        try {
            @Suppress("DEPRECATION")
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            @Suppress("DEPRECATION")
            val keyguardLock = keyguardManager?.newKeyguardLock("KioskBrowser")
            @Suppress("DEPRECATION")
            keyguardLock?.disableKeyguard()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        @Suppress("DEPRECATION")
        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeSystemDialogsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeSystemDialogsReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(closeSystemDialogsReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        val targetUrl = settingsManager.targetUrl
        if (intent?.getBooleanExtra("EXTRA_RELOAD", false) == true) {
            intent?.removeExtra("EXTRA_RELOAD")
            hasError = false
            layoutError.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(targetUrl)
        } else if (webView.url != targetUrl && webView.url.isNullOrEmpty()) {
            webView.loadUrl(targetUrl)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url ?: "")
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hasError = false
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    val detail = buildErrorDetail(errorCode, description)
                    showCustomError(detail)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (request?.isForMainFrame == true) {
                        val errorCode = error?.errorCode ?: 0
                        val errorDescription = error?.description?.toString() ?: ""
                        val detail = buildErrorDetail(errorCode, errorDescription)
                        showCustomError(detail)
                    }
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (request?.isForMainFrame == true) {
                        val statusCode = errorResponse?.statusCode ?: 0
                        if (statusCode >= 400) {
                            val reason = errorResponse?.reasonPhrase ?: "HTTP Error $statusCode"
                            showCustomError("Server returned error $statusCode: $reason")
                        }
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (hasError) {
                    webView.visibility = View.GONE
                    layoutError.visibility = View.VISIBLE
                } else {
                    layoutError.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
            }
        }
        webView.loadUrl(settingsManager.targetUrl)
    }

    private fun buildErrorDetail(errorCode: Int, description: String?): String {
        val desc = description ?: ""
        val troubleshootingMsg =
            "Connection Timed Out. If connecting to a local server on your PC, use your PC's IP address (e.g. http://192.168.1.X:port or http://10.0.2.2:port on emulator) instead of localhost."

        val isTimeoutOrNetworkError = errorCode == WebViewClient.ERROR_TIMEOUT ||
                errorCode == WebViewClient.ERROR_CONNECT ||
                errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ||
                desc.contains("ERR_CONNECTION_TIMED_OUT", ignoreCase = true) ||
                desc.contains("timed out", ignoreCase = true) ||
                desc.contains("connection", ignoreCase = true) ||
                desc.contains("net::", ignoreCase = true)

        return if (isTimeoutOrNetworkError) {
            if (desc.isNotEmpty()) {
                "$desc\n\n$troubleshootingMsg"
            } else {
                troubleshootingMsg
            }
        } else {
            if (desc.isNotEmpty()) desc else troubleshootingMsg
        }
    }

    private fun showCustomError(detail: String) {
        hasError = true
        tvErrorDetail.text = detail
        webView.visibility = View.GONE
        layoutError.visibility = View.VISIBLE
    }

    private fun hideSystemUI() {
        val decorView = window.decorView
        @Suppress("DEPRECATION")
        var uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            @Suppress("DEPRECATION")
            uiOptions = uiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = uiOptions
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        } else {
            @Suppress("DEPRECATION")
            val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeDialogs)
        }
    }

    private fun showAdminAuthDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_admin_auth, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPasswordInput)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            etPassword.showSoftInputOnFocus = false
        }
        etPassword.isFocusable = false
        etPassword.isClickable = false
        etPassword.disableCopyPasteAndSuggestions()

        val allKeyIds = listOf(
            R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3, R.id.btnKey4,
            R.id.btnKey5, R.id.btnKey6, R.id.btnKey7, R.id.btnKey8, R.id.btnKey9,
            R.id.btnKeyQ, R.id.btnKeyW, R.id.btnKeyE, R.id.btnKeyR, R.id.btnKeyT,
            R.id.btnKeyY, R.id.btnKeyU, R.id.btnKeyI, R.id.btnKeyO, R.id.btnKeyP,
            R.id.btnKeyA, R.id.btnKeyS, R.id.btnKeyD, R.id.btnKeyF, R.id.btnKeyG,
            R.id.btnKeyH, R.id.btnKeyJ, R.id.btnKeyK, R.id.btnKeyL,
            R.id.btnKeyZ, R.id.btnKeyX, R.id.btnKeyC, R.id.btnKeyV,
            R.id.btnKeyB, R.id.btnKeyN, R.id.btnKeyM
        )

        val keyClickListener = View.OnClickListener { v ->
            val charStr = (v as Button).text.toString()
            etPassword.append(charStr)
        }

        for (id in allKeyIds) {
            dialogView.findViewById<Button>(id)?.setOnClickListener(keyClickListener)
        }

        dialogView.findViewById<Button>(R.id.btnKeyClear)?.setOnClickListener {
            etPassword.text?.clear()
        }

        dialogView.findViewById<Button>(R.id.btnKeyDelete)?.setOnClickListener {
            val length = etPassword.text?.length ?: 0
            if (length > 0) {
                etPassword.text?.delete(length - 1, length)
            }
        }

        var dialogInstance: AlertDialog? = null

        val dialog = AlertDialog.Builder(this)
            .setTitle("Admin Authentication")
            .setView(dialogView)
            .setPositiveButton("Authenticate") { _, _ ->
                verifyPassword(etPassword.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialogInstance = dialog

        dialogView.findViewById<Button>(R.id.btnKeyOk)?.setOnClickListener {
            verifyPassword(etPassword.text.toString())
            dialogInstance?.dismiss()
        }

        @Suppress("DEPRECATION")
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        dialog.show()

        @Suppress("DEPRECATION")
        dialog.window?.decorView?.systemUiVisibility = window.decorView.systemUiVisibility
        @Suppress("DEPRECATION")
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    private fun verifyPassword(password: String) {
        if (password == settingsManager.adminPassword || password.equals(settingsManager.adminPassword, ignoreCase = true)) {
            val intent = Intent(this, AdminActivity::class.java)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun EditText.disableCopyPasteAndSuggestions() {
        this.isLongClickable = false
        this.setTextIsSelectable(false)
        this.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode?) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.customInsertionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: ActionMode?) {}
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Completely empty to block back navigation / exit
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true // Consume event, preventing any back navigation or exit
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVolumeDownTime > 2000) {
                volumeDownPressCount = 1
            } else {
                volumeDownPressCount++
            }
            lastVolumeDownTime = currentTime

            if (volumeDownPressCount >= 5) {
                volumeDownPressCount = 0
                showAdminAuthDialog()
            }
            return true // Block volume down change
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true // Block volume up change
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

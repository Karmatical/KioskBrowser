package com.arthursacomori.kioskbrowser

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthursacomori.kioskbrowser.data.SettingsManager

class AdminActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var etTargetUrl: EditText
    private lateinit var etAdminPassword: EditText
    private lateinit var btnEnableAdmin: Button
    private lateinit var btnReloadPage: Button
    private lateinit var btnSetHomeApp: Button

    private val closeSystemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            @Suppress("DEPRECATION")
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                hideSystemUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        applyLockscreenFlags()

        setContentView(R.layout.activity_admin)

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility: Int ->
            if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                hideSystemUI()
            }
        }

        settingsManager = SettingsManager(this)

        etTargetUrl = findViewById(R.id.etTargetUrl)
        etAdminPassword = findViewById(R.id.etAdminPassword)
        btnEnableAdmin = findViewById(R.id.btnEnableAdmin)
        btnReloadPage = findViewById(R.id.btnReloadPage)
        btnSetHomeApp = findViewById(R.id.btnSetHomeApp)

        etTargetUrl.disableCopyPasteAndSuggestions()
        etAdminPassword.disableCopyPasteAndSuggestions()

        etTargetUrl.setText(settingsManager.targetUrl)
        etAdminPassword.setText(settingsManager.adminPassword)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val url = etTargetUrl.text.toString().trim()
            val password = etAdminPassword.text.toString().trim()

            if (url.isEmpty()) {
                Toast.makeText(this, "Target URL cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Admin Password cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            settingsManager.targetUrl = url
            settingsManager.adminPassword = password
            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnReloadPage.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_RELOAD", true)
            }
            startActivity(intent)
            finish()
        }

        btnSetHomeApp.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    @SuppressLint("InlinedApi")
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    startActivity(intent)
                } else {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(Intent.createChooser(intent, "Select Home Launcher"))
                }
            } catch (_: Exception) {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(Intent.createChooser(intent, "Select Home Launcher"))
            }
        }

        btnEnableAdmin.setOnClickListener {
            val adminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(adminComponent)) {
                Toast.makeText(this, "Device Administrator is already enabled", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activate Device Administrator to secure Kiosk Mode and lock device.")
                }
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btnExitKiosk).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                finishAffinity()
            } else {
                finish()
            }
            System.exit(0)
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
            val keyguardLock = keyguardManager?.newKeyguardLock("KioskBrowserAdmin")
            @Suppress("DEPRECATION")
            keyguardLock?.disableKeyguard()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        val adminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isAdminActive(adminComponent)) {
            btnEnableAdmin.text = "Device Administrator Enabled"
        } else {
            btnEnableAdmin.text = "Enable Device Administrator"
        }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true
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

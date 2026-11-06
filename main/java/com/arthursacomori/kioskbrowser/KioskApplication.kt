package com.arthursacomori.kioskbrowser

import android.content.Context
import androidx.multidex.MultiDexApplication

class KioskApplication : MultiDexApplication() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        androidx.multidex.MultiDex.install(this)
    }
}

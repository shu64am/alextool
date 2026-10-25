package com.alexmodzofc.tool.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.alexmodzofc.tool.util.LocaleHelper
import com.alexmodzofc.tool.util.loadMeasurementSystemPreference
import com.alexmodzofc.tool.BuildConfig
import java.lang.ref.WeakReference

class AlexToolApplication : Application() {

    private var _currentActivity: WeakReference<Activity>? = null
    val currentActivity: Activity? get() = _currentActivity?.get()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        enforceSecurity()
        applyNightMode()
        loadMeasurementSystemPreference(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                _currentActivity = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (_currentActivity?.get() === activity) _currentActivity = null
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (_currentActivity?.get() === activity) _currentActivity = null
            }
        })
    }

    /** Basic anti-tamper protection: on a protected (release) build the app refuses to run
     *  when a debugger is attached, making live reverse engineering of the running process
     *  significantly harder. */
    private fun enforceSecurity() {
        if (BuildConfig.IS_PROTECTED_BUILD && android.os.Debug.isDebuggerConnected()) {
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
    }

    fun applyNightMode() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_theme", "dark") ?: "dark"
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}

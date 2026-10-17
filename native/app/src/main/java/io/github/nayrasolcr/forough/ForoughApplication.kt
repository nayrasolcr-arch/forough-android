package io.github.nayrasolcr.forough

import android.app.Application
import android.content.Context
import io.github.nayrasolcr.forough.tunnel.SettingsStore

class ForoughApplication : Application() {

    override fun attachBaseContext(base: Context) {
        val pref = SettingsStore(base).language
        super.attachBaseContext(LocaleHelper.wrap(base, pref))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile var instance: ForoughApplication? = null
            private set
    }
}

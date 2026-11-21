package br.com.monitor2008

import android.app.Application
import br.com.monitor2008.data.CrashLog

/** Primeira coisa que roda no app (antes das telas e dos serviços): liga o registro de erros. */
class MonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

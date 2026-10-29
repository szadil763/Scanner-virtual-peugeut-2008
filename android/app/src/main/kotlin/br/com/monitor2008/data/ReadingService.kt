package br.com.monitor2008.data

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.monitor2008.MainActivity
import br.com.monitor2008.ui.MonitorEngine

/**
 * Mantém o app vivo lendo o carro quando outro app ocupa a tela toda (mapa, música): sem isso o
 * Android pode encerrar o Monitor e a viagem não é gravada. Mostra uma notificação discreta.
 *
 * A leitura em si continua no [MonitorEngine]; o serviço só impede o Android de fechar o processo.
 * Com o carro desligado o multimídia desliga junto, então não há consumo de bateria parado.
 */
class ReadingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MonitorEngine.get(application as Application) // garante o motor ligado
        createChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Monitor 2008")
            .setContentText("Lendo o carro em segundo plano")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            // Sem a permissão de Bluetooth o Android não deixa: segue sem o serviço.
            stopSelf()
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Leitura do carro", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        private const val CHANNEL = "leitura"
        private const val NOTIFICATION_ID = 1

        /** Liga o serviço quando o adaptador real está em uso; desliga na simulação. Chamar com o app na tela. */
        fun update(context: Context, enabled: Boolean) {
            val intent = Intent(context, ReadingService::class.java)
            try {
                if (enabled) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
            } catch (e: Exception) {
                // Android pode recusar iniciar em segundo plano; tenta de novo quando o app abrir.
            }
        }
    }
}

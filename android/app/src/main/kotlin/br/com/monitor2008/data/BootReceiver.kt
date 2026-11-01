package br.com.monitor2008.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import br.com.monitor2008.MainActivity

/**
 * O multimídia liga junto com o carro: se o dono ativou "Abrir sozinho ao ligar o carro", abre o app.
 * Desde o Android 10 um app só abre a própria tela a partir do segundo plano com a permissão
 * "sobrepor a outros apps", pedida em Ajustes. Multimídias que só "dormem" com a ignição desligada
 * não reiniciam: neles o app continua aberto como estava.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        if (!store.autoOpen || !Settings.canDrawOverlays(context)) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (e: Exception) {
            // O Android recusou: o app abre quando o dono tocar no ícone, já na mesma aba.
        }
    }
}

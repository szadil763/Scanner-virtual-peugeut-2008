package br.com.monitor2008.data

import android.companion.CompanionDeviceService
import androidx.annotation.RequiresApi

/**
 * O Android chama este serviço quando o adaptador (associado em [AutoConnect]) aparece ou some,
 * mesmo com o app fechado. Aparecer = carro ligado: liga a leitura em segundo plano. Sumir =
 * carro desligado ou longe: desliga o serviço para não gastar a bateria do celular.
 */
@RequiresApi(31)
class AdapterPresenceService : CompanionDeviceService() {
    @Deprecated("No Android 13+ o sistema repassa para esta versão.")
    override fun onDeviceAppeared(address: String) {
        ReadingService.update(this, enabled = true)
    }

    @Deprecated("No Android 13+ o sistema repassa para esta versão.")
    override fun onDeviceDisappeared(address: String) {
        ReadingService.update(this, enabled = false)
    }
}

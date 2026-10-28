package br.com.monitor2008.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entrada do Android Auto. Com o app instalado no celular, ele aparece na tela do carro quando o
 * Android Auto conecta (com "Fontes desconhecidas" ligado nas configurações de desenvolvedor do
 * Android Auto, porque o app não vem da Play Store). Ver docs/ANDROID-AUTO.md.
 */
class MonitorCarAppService : CarAppService() {

    // Instalado por APK, fora da Play Store: aceita qualquer host do Android Auto.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = PainelCarScreen(carContext)
    }
}

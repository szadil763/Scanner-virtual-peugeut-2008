package br.com.monitor2008.data

import br.com.monitor2008.BuildConfig
import br.com.monitor2008.domain.UpdateRules

/** O que muda entre os dois APKs (productFlavors em build.gradle.kts). */
object Features {
    /**
     * Ver e controlar a música dos outros apps (acesso a notificações): só no APK do multimídia.
     * No celular o Play Protect bloqueia a instalação de fora da loja de apps que pedem esse acesso.
     */
    val mediaControl: Boolean = BuildConfig.FLAVOR == "multimidia"

    /**
     * Janela flutuante própria (precisa de "sobrepor a outros apps", declarada só no APK do
     * multimídia). No celular fica a janela flutuante do Android.
     */
    val ownFloatingWindow: Boolean = mediaControl

    /**
     * Palavra "Monitor" (escuta contínua): PAUSADA a pedido do dono (0.1.61) — o som do carro e
     * qualquer barulho acionavam. Fica só o botão 🎤 Voz até haver uma solução melhor. O código
     * continua (MicService, WakeModel); para voltar, trocar para true.
     */
    const val wakeWord: Boolean = false

    /** APK deste aparelho na página de Releases (a atualização baixa o do mesmo tipo). */
    val apkName: String = UpdateRules.apkName(phone = !mediaControl)
}

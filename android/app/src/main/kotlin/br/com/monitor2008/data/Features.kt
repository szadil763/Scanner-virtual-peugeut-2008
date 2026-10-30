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

    /** APK deste aparelho na página de Releases (a atualização baixa o do mesmo tipo). */
    val apkName: String = UpdateRules.apkName(phone = !mediaControl)
}

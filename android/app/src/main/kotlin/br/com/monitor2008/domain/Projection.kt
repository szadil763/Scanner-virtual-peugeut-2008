package br.com.monitor2008.domain

/**
 * Apps do multimídia que recebem o Android Auto (ou CarPlay) do celular: ZLink, AutoKit, CarLink, TLink etc. Com eles,
 * o mapa é o Google Maps ou o Waze do CELULAR (GPS, internet e trânsito do celular) mostrado na tela do multimídia.
 * Serve quando o GPS do multimídia não funciona. Cada fabricante dá um nome; procura pelo nome e pelo pacote.
 */
object Projection {
    private val WORDS = listOf(
        "android auto", "androidauto", "zlink", "z-link", "autokit", "auto kit", "carlink", "car link", "tlink",
        "carplay", "car play", "autolink", "ezlink", "carbit", "smartlink", "easyconnect", "phonelink", "boxlink",
    )

    /** O app do Android Auto do próprio celular (não recebe projeção; num multimídia não serve). */
    const val PHONE_ANDROID_AUTO = "com.google.android.projection.gearhead"

    fun isProjectionApp(pkg: String, label: String): Boolean {
        if (pkg == PHONE_ANDROID_AUTO) return false
        val t = "$pkg $label".lowercase()
        return WORDS.any { it in t }
    }
}

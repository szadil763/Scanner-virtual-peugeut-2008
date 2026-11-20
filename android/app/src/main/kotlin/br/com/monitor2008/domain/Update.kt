package br.com.monitor2008.domain

/** De onde vem a atualização: "teste" recebe todo APK novo; "estável" só o que o dono aprovou. */
enum class UpdateChannel(val tag: String, val label: String) {
    ESTAVEL("estavel", "Estável"),
    TESTE("latest", "Teste"),
}

/**
 * Versão publicada numa página de Releases do GitHub. [signingKey]: geração da chave que assina o APK
 * ("chave: N" nas notas; 1 quando não diz). Chave diferente da instalada não instala por cima.
 */
data class RemoteVersion(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val signingKey: Int = 1,
)

/**
 * Regras puras da atualização: ler a versão que o GitHub Actions escreve nas notas da release
 * ("Versão 0.1.31 ... versionCode: 31") e decidir se ela é mais nova que a instalada.
 */
object UpdateRules {
    /** APK do multimídia (completo) ou do celular (sem acesso a notificações). */
    fun apkName(phone: Boolean) = if (phone) "monitor-2008-celular.apk" else "monitor-2008.apk"

    /** versionCode da release: "versionCode: N" ou, em releases antigas, o N de "0.1.N". */
    fun versionCode(notes: String): Int? =
        Regex("""versionCode:\s*(\d+)""").find(notes)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b0\.1\.(\d+)\b""").find(notes)?.groupValues?.get(1)?.toIntOrNull()

    fun versionName(notes: String): String? = Regex("""\b(\d+\.\d+\.\d+)\b""").find(notes)?.groupValues?.get(1)

    /** Monta a versão a partir das notas e do link do APK; null se faltar algo. */
    fun parse(notes: String, apkUrl: String?): RemoteVersion? {
        val code = versionCode(notes) ?: return null
        val url = apkUrl?.takeIf { it.startsWith("https://") } ?: return null
        return RemoteVersion(code, versionName(notes) ?: "0.1.$code", url, notes, signingKey(notes))
    }

    /** Geração da chave de assinatura nas notas da release ("chave: 2"); sem a linha, é a chave 1 (a antiga). */
    fun signingKey(notes: String): Int =
        Regex("""\bchave:\s*(\d+)""").find(notes)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    /**
     * O APK novo é assinado com outra chave que a do app instalado: o Android recusa instalar por cima
     * ("conflito com pacote existente"). Precisa sincronizar, desinstalar e instalar de novo.
     */
    fun needsReinstall(remote: RemoteVersion?, installedKey: Int): Boolean =
        remote != null && remote.signingKey != installedKey

    /** Página da release no navegador (para baixar o APK depois de desinstalar o app). */
    fun releasePage(owner: String, repo: String, channel: UpdateChannel) =
        "https://github.com/$owner/$repo/releases/tag/${channel.tag}"

    fun isNewer(remote: RemoteVersion?, installedCode: Int): Boolean = remote != null && remote.versionCode > installedCode

    /** Endereço da API do GitHub para a release do canal. */
    fun releaseUrl(owner: String, repo: String, channel: UpdateChannel) =
        "https://api.github.com/repos/$owner/$repo/releases/tags/${channel.tag}"
}

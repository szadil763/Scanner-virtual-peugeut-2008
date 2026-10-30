package br.com.monitor2008.domain

/** De onde vem a atualização: "teste" recebe todo APK novo; "estável" só o que o dono aprovou. */
enum class UpdateChannel(val tag: String, val label: String) {
    ESTAVEL("estavel", "Estável"),
    TESTE("latest", "Teste"),
}

/** Versão publicada numa página de Releases do GitHub. */
data class RemoteVersion(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

/**
 * Regras puras da atualização: ler a versão que o GitHub Actions escreve nas notas da release
 * ("Versão 0.1.31 ... versionCode: 31") e decidir se ela é mais nova que a instalada.
 */
object UpdateRules {
    const val APK_NAME = "monitor-2008.apk"

    /** versionCode da release: "versionCode: N" ou, em releases antigas, o N de "0.1.N". */
    fun versionCode(notes: String): Int? =
        Regex("""versionCode:\s*(\d+)""").find(notes)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b0\.1\.(\d+)\b""").find(notes)?.groupValues?.get(1)?.toIntOrNull()

    fun versionName(notes: String): String? = Regex("""\b(\d+\.\d+\.\d+)\b""").find(notes)?.groupValues?.get(1)

    /** Monta a versão a partir das notas e do link do APK; null se faltar algo. */
    fun parse(notes: String, apkUrl: String?): RemoteVersion? {
        val code = versionCode(notes) ?: return null
        val url = apkUrl?.takeIf { it.startsWith("https://") } ?: return null
        return RemoteVersion(code, versionName(notes) ?: "0.1.$code", url, notes)
    }

    fun isNewer(remote: RemoteVersion?, installedCode: Int): Boolean = remote != null && remote.versionCode > installedCode

    /** Endereço da API do GitHub para a release do canal. */
    fun releaseUrl(owner: String, repo: String, channel: UpdateChannel) =
        "https://api.github.com/repos/$owner/$repo/releases/tags/${channel.tag}"
}

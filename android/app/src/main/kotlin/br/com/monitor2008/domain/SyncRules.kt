package br.com.monitor2008.domain

import java.util.Locale

/**
 * Regras puras de sincronização e validação de convites da nuvem.
 */
object SyncRules {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$")

    /** Normaliza o e-mail (sem espaços nas pontas e em minúsculas) para comparação e armazenamento. */
    fun normalizeEmail(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    /** Confere se o e-mail informado tem formato válido para convite na nuvem. */
    fun isValidEmail(raw: String): Boolean {
        val e = normalizeEmail(raw)
        if (e.length !in 5..254) return false
        if (!EMAIL_REGEX.matches(e)) return false
        val local = e.substringBefore('@')
        val domain = e.substringAfter('@')
        if (local.startsWith('.') || local.endsWith('.') || local.contains("..")) return false
        if (domain.startsWith('.') || domain.startsWith('-') || domain.contains("..")) return false
        return true
    }

    /**
     * Inclui [newEmail] na lista de convites já normalizada e sem duplicatas.
     * Retorna null quando o novo e-mail é inválido.
     */
    fun addInvite(existing: List<String>, newEmail: String): List<String>? {
        val candidate = normalizeEmail(newEmail)
        if (!isValidEmail(candidate)) return null
        val cleaned = existing.map(::normalizeEmail).filter(::isValidEmail)
        return (cleaned + candidate).distinct()
    }

    /** Há alterações locais ainda não confirmadas por um envio completo. */
    fun hasPendingChanges(currentRevision: Long, syncedRevision: Long): Boolean =
        currentRevision > syncedRevision

    /**
     * Calcula o próximo marcador de revisão sincronizada após um envio bem-sucedido que
     * capturou [sentRevision]. Nunca retrocede além de [prevSyncedRevision].
     */
    fun nextSyncedRevision(sentRevision: Long, prevSyncedRevision: Long): Long =
        maxOf(prevSyncedRevision, sentRevision)
}

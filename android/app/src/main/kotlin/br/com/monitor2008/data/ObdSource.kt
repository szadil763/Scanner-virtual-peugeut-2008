package br.com.monitor2008.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Fonte de leituras: o adaptador real ou a simulação. */
interface ObdSource {
    val name: String
    val info: StateFlow<ConnectionInfo>

    /** Fluxo contínuo de leituras. Cancelar a coleta encerra a conexão. */
    fun samples(): Flow<Sample>

    /** Conversa com o adaptador para diagnóstico (vazia na simulação). */
    fun diagnosticLog(): List<String> = emptyList()

    /** Falhas armazenadas (modo 03). Somente leitura. */
    suspend fun readDtcs(): List<String>

    /** Falhas pendentes (modo 07). Somente leitura. */
    suspend fun readPendingDtcs(): List<String> = emptyList()
}

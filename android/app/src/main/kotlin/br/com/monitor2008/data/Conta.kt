package br.com.monitor2008.data

import android.content.Context
import android.content.Intent
import br.com.monitor2008.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Conta Google do app (Firebase Auth). Cada conta tem os próprios dados na nuvem (backup).
 * Qualquer conta Google pode entrar; os dados ficam separados por conta.
 */
object Conta {
    fun intencaoEntrar(ctx: Context): Intent {
        val opcoes = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(ctx.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(ctx, opcoes).signInIntent
    }

    fun email(): String? = FirebaseAuth.getInstance().currentUser?.email

    fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /** Recebe o resultado da tela do Google e entra no Firebase. [depois] diz se deu certo e o motivo. */
    fun aoResultado(data: Intent?, depois: (Boolean, String) -> Unit) {
        try {
            val conta = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val token = conta.idToken
            if (token == null) {
                depois(false, "O Google não devolveu a chave de entrada. Confira a impressão digital SHA-1 no Firebase.")
                return
            }
            FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                .addOnSuccessListener { depois(true, "Conta conectada.") }
                .addOnFailureListener { depois(false, "Não foi possível entrar: ${it.message ?: "erro desconhecido"}") }
        } catch (e: ApiException) {
            depois(false, "Entrada cancelada ou não concluída (código ${e.statusCode}).")
        }
    }

    fun sair() {
        FirebaseAuth.getInstance().signOut()
    }
}

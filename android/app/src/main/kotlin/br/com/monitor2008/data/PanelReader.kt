package br.com.monitor2008.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Lê o texto de uma foto do painel (reconhecimento de texto do Google, no próprio aparelho, sem
 * internet). O número do hodômetro é escolhido em domain/OdometerRead.
 */
object PanelReader {
    /** Arquivo onde a câmera grava a foto (cache do app; apagado a cada foto nova). */
    fun photoUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "fotos").apply { mkdirs() }
        val file = File(dir, "painel.jpg")
        if (file.exists()) file.delete()
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
    }

    /** Linhas de texto da foto; vazia se não conseguiu ler. */
    suspend fun lines(ctx: Context, uri: Uri): List<String> = suspendCancellableCoroutine { cont ->
        val image = try { InputImage.fromFilePath(ctx, uri) } catch (e: Exception) { null }
        if (image == null) { cont.resume(emptyList()); return@suspendCancellableCoroutine }
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.process(image)
            .addOnSuccessListener { text ->
                val out = text.textBlocks.flatMap { b -> b.lines.map { it.text } }
                client.close()
                if (cont.isActive) cont.resume(out)
            }
            .addOnFailureListener {
                client.close()
                if (cont.isActive) cont.resume(emptyList())
            }
    }
}

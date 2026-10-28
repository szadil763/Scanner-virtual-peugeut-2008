package br.com.monitor2008

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import br.com.monitor2008.data.BtPermissions
import br.com.monitor2008.data.MediaWatcher
import br.com.monitor2008.ui.MonitorApp
import br.com.monitor2008.ui.MonitorEngine

class MainActivity : ComponentActivity() {

    private val vm by lazy { MonitorEngine.get(application) }

    // O Android exige permissão em tempo de execução para procurar e falar com o adaptador Bluetooth.
    private val askBluetooth = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it } && !vm.ui.value.simulated) vm.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val missing = BtPermissions.missing(this)
        if (missing.isNotEmpty()) askBluetooth.launch(missing.toTypedArray())

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    background = Color(0xFF0B0F14),
                    surface = Color(0xFF0B0F14),
                    surfaceVariant = Color(0xFF131A22),
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MonitorApp(vm)
                }
            }
        }
    }

    /** Ao tocar em Início: se ligado em Ajustes, continua numa janela flutuante sobre o mapa ou a música. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!vm.ui.value.pipOnLeave) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (_: Exception) {
            // Alguns multimídias não suportam janela flutuante: segue normal.
        }
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar da tela de "acesso a notificações", o mini player já começa a mostrar a música.
        MediaWatcher.get(application).start()
    }

    override fun onStop() {
        super.onStop()
        vm.saveNow()
    }
}

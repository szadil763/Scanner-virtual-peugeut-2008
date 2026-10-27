package br.com.monitor2008

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import br.com.monitor2008.data.BtPermissions
import br.com.monitor2008.ui.MonitorApp
import br.com.monitor2008.ui.MonitorViewModel

class MainActivity : ComponentActivity() {

    private val vm: MonitorViewModel by viewModels()

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
}

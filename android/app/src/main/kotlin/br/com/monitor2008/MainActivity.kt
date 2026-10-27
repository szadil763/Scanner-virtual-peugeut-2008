package br.com.monitor2008

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat
import br.com.monitor2008.ui.MonitorApp
import br.com.monitor2008.ui.MonitorViewModel

class MainActivity : ComponentActivity() {

    private val vm: MonitorViewModel by viewModels()

    // Android 12+ exige permissão em tempo de execução para falar com o adaptador Bluetooth.
    private val askBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && !vm.ui.value.simulated) vm.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

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

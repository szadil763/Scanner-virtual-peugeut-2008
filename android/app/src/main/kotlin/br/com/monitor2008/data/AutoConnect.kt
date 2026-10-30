package br.com.monitor2008.data

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcelable
import java.util.regex.Pattern

/**
 * Conexão automática no celular: o adaptador vira "dispositivo companheiro" do app e o Android
 * avisa quando ele fica ao alcance (carro ligado), mesmo com o app fechado. Aí o
 * [AdapterPresenceService] liga a leitura. Android 12 ou mais novo, com o Bluetooth do celular ligado.
 */
object AutoConnect {
    private val NAMES = Pattern.compile("(?i).*(OBD|ELM|V-?LINK).*")

    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 31 && context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    fun activeAddress(context: Context): String? = Store(context).autoConnectAddress

    /** Abre a busca do Android pelo adaptador; [onChooser] recebe a tela de escolha para o app abrir. */
    fun associate(context: Context, onChooser: (IntentSender) -> Unit, onError: (String) -> Unit) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return onError("Este aparelho não tem o recurso.")
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothLeDeviceFilter.Builder().setNamePattern(NAMES).build())
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setNamePattern(NAMES).build())
            .build()
        try {
            cdm.associate(request, object : CompanionDeviceManager.Callback() {
                @Deprecated("Chamado também no Android 13+ (onAssociationPending repassa para cá).")
                override fun onDeviceFound(chooserLauncher: IntentSender) = onChooser(chooserLauncher)
                override fun onFailure(error: CharSequence?) =
                    onError("Adaptador não encontrado. Ligue o carro (ignição) com o adaptador na tomada e tente de novo.")
            }, null)
        } catch (e: Exception) {
            onError("Não foi possível procurar o adaptador.")
        }
    }

    /** Depois que o usuário escolheu o adaptador: passa a observar quando ele aparece. Devolve o endereço. */
    fun onChosen(context: Context, data: Intent?): String? {
        val address = if (Build.VERSION.SDK_INT >= 33) {
            data?.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
                ?.deviceMacAddress?.toString()
        } else {
            @Suppress("DEPRECATION")
            when (val d = data?.getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)) {
                is BluetoothDevice -> d.address
                is ScanResult -> d.device.address
                else -> null
            }
        }?.uppercase() ?: return null
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= 31) cdm.startObservingDevicePresence(address)
            Store(context).autoConnectAddress = address
            address
        } catch (e: Exception) {
            null
        }
    }

    fun disable(context: Context) {
        val store = Store(context)
        val address = store.autoConnectAddress ?: return
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 31) cdm?.stopObservingDevicePresence(address)
            @Suppress("DEPRECATION")
            cdm?.disassociate(address)
        } catch (e: Exception) {
            // Associação já removida pelo sistema: só esquece o endereço.
        }
        store.autoConnectAddress = null
    }
}

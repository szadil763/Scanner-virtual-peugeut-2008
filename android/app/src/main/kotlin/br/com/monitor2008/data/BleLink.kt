package br.com.monitor2008.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Ligação com o ELM327 por Bluetooth de baixo consumo (BLE). Não precisa parear nem de PIN:
 * o app procura o adaptador pelo nome e conecta direto.
 *
 * Entrega o mesmo par de streams do Bluetooth clássico, então o [br.com.monitor2008.obd.Elm327Client]
 * funciona igual nos dois. As respostas chegam por notificação e entram na fila de [input];
 * o comando escrito em [output] é enviado em pedaços de 20 bytes no flush().
 *
 * NUNCA TESTADO NO CARRO.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_SCAN e BLUETOOTH_CONNECT são pedidas na MainActivity e em Ajustes
class BleLink private constructor(private val gatt: BluetoothGatt) {

    private val queue = ArrayDeque<Byte>()
    @Volatile private var closed = false
    private var writeChar: BluetoothGattCharacteristic? = null
    private val writeDone = Semaphore(0)

    val deviceName: String get() = gatt.device.name ?: gatt.device.address

    val input: InputStream = object : InputStream() {
        override fun available(): Int = synchronized(queue) { queue.size }
        override fun read(): Int = synchronized(queue) {
            if (queue.isEmpty()) -1 else queue.removeFirst().toInt() and 0xFF
        }
    }

    val output: OutputStream = object : OutputStream() {
        private val buffer = ArrayList<Byte>()
        override fun write(b: Int) { buffer.add(b.toByte()) }
        override fun flush() {
            if (closed) throw IOException("adaptador BLE desconectado")
            val data = buffer.toByteArray()
            buffer.clear()
            data.toList().chunked(CHUNK).forEach { send(it.toByteArray()) }
        }
    }

    @Suppress("DEPRECATION") // caminho antigo, só antes do Android 13
    private fun send(chunk: ByteArray) {
        val c = writeChar ?: throw IOException("sem canal de escrita")
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        writeDone.drainPermits()
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(c, chunk, type) == 0 // BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = type
            c.value = chunk
            gatt.writeCharacteristic(c)
        }
        if (!ok) throw IOException("falha ao enviar pelo BLE")
        writeDone.tryAcquire(1, TimeUnit.SECONDS) // espera a confirmação antes do próximo pedaço
    }

    private fun received(bytes: ByteArray?) {
        if (bytes == null) return
        synchronized(queue) { bytes.forEach { queue.addLast(it) } }
    }

    fun close() {
        closed = true
        try { gatt.disconnect() } catch (_: Exception) { }
        try { gatt.close() } catch (_: Exception) { }
    }

    companion object {
        private const val CHUNK = 20
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Pares (serviço, notificação, escrita) usados pelos adaptadores ELM327 BLE mais comuns. */
        private val KNOWN = listOf(
            Triple("0000fff0", "0000fff1", "0000fff2"),
            Triple("0000ffe0", "0000ffe1", "0000ffe1"),
            Triple("000018f0", "00002af0", "00002af1"),
            Triple("e7810a71", "bef8d6c9", "bef8d6c9"), // Vgate
        )

        /** Serviços padrão do BLE que nunca são o canal de dados. */
        private val STANDARD = setOf("00001800", "00001801", "0000180a", "0000180f")

        /**
         * Procura um adaptador BLE cujo nome satisfaça [matches] por até [scanMs]. Os nomes vistos
         * vão para [seen], para o registro mostrar se o adaptador chegou a aparecer.
         */
        fun find(
            adapter: BluetoothAdapter, matches: (String) -> Boolean, scanMs: Long = 15_000, seen: MutableSet<String>? = null,
        ): BluetoothDevice? {
            val scanner = adapter.bluetoothLeScanner ?: return null
            var found: BluetoothDevice? = null
            val latch = CountDownLatch(1)
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                    seen?.let { synchronized(it) { it.add(name) } }
                    if (found == null && matches(name)) {
                        found = result.device
                        latch.countDown()
                    }
                }
                override fun onScanFailed(errorCode: Int) { latch.countDown() }
            }
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(null, settings, callback)
            latch.await(scanMs, TimeUnit.MILLISECONDS)
            try { scanner.stopScan(callback) } catch (_: Exception) { }
            return found
        }

        @Suppress("DEPRECATION") // caminho antigo, só antes do Android 13
        /** Conecta, descobre o canal de dados e liga as notificações. Devolve null e o motivo em [log] se falhar. */
        fun open(context: Context, device: BluetoothDevice, log: (String) -> Unit): BleLink? {
            val connected = CountDownLatch(1)
            val discovered = CountDownLatch(1)
            val descriptorDone = CountDownLatch(1)
            var link: BleLink? = null
            var connectedOk = false

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        connectedOk = true
                        connected.countDown()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        // Status 133 = falha genérica do chip, comum com áudio Bluetooth ativo; vale tentar de novo.
                        log("BLE: desconectado (status $status)")
                        link?.closed = true
                        connected.countDown(); discovered.countDown(); descriptorDone.countDown()
                    }
                }
                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { discovered.countDown() }
                override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { descriptorDone.countDown() }
                override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                    link?.writeDone?.release()
                }
                override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
                    link?.received(value)
                }
                @Deprecated("Usado só antes do Android 13")
                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) link?.received(c.value)
                }
            }

            val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: return null.also { log("BLE: connectGatt falhou") }
            val l = BleLink(gatt)
            link = l

            if (!connected.await(10, TimeUnit.SECONDS) || !connectedOk) {
                log("BLE: sem resposta ao conectar")
                l.close(); return null
            }
            // Prioridade alta: respostas mais rápidas quando o chip divide o tempo com o áudio do celular.
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            gatt.discoverServices()
            if (!discovered.await(10, TimeUnit.SECONDS) || l.closed) {
                log("BLE: não achou os serviços")
                l.close(); return null
            }

            val (notify, write) = pickChannel(gatt) ?: run {
                log("BLE: serviços sem canal de dados: " + gatt.services.joinToString { it.uuid.toString().take(8) })
                l.close(); return null
            }
            log("BLE: notificação ${notify.uuid.toString().take(8)}, escrita ${write.uuid.toString().take(8)}")
            l.writeChar = write

            gatt.setCharacteristicNotification(notify, true)
            notify.getDescriptor(CCCD)?.let { d ->
                val value = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(d, value)
                } else {
                    d.value = value
                    gatt.writeDescriptor(d)
                }
                descriptorDone.await(3, TimeUnit.SECONDS)
            }
            return l
        }

        /** Escolhe o canal conhecido; se não houver, o primeiro serviço não padrão com notificação e escrita. */
        private fun pickChannel(gatt: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
            fun short(u: UUID) = u.toString().take(8)
            for ((s, n, w) in KNOWN) {
                val service = gatt.services.firstOrNull { short(it.uuid) == s } ?: continue
                val nc = service.characteristics.firstOrNull { short(it.uuid) == n && canNotify(it) } ?: continue
                val wc = service.characteristics.firstOrNull { short(it.uuid) == w && canWrite(it) } ?: continue
                return nc to wc
            }
            for (service in gatt.services) {
                if (short(service.uuid) in STANDARD) continue
                val nc = service.characteristics.firstOrNull { canNotify(it) } ?: continue
                val wc = service.characteristics.firstOrNull { canWrite(it) } ?: continue
                return nc to wc
            }
            return null
        }

        private fun canNotify(c: BluetoothGattCharacteristic) =
            c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

        private fun canWrite(c: BluetoothGattCharacteristic) =
            c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
    }
}

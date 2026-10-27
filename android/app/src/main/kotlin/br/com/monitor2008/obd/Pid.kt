package br.com.monitor2008.obd

/**
 * PIDs do modo 01 usados pelo app. As fórmulas seguem a SAE J1979 e devem ficar
 * idênticas às de tools/verify-formulas.js e prototype/index.html.
 *
 * `decode` recebe os bytes de dados (A, B, ...) já sem o cabeçalho "41 XX".
 */
enum class Pid(
    val code: Int,
    val label: String,
    val unit: String,
    val dataBytes: Int,
    val decode: (IntArray) -> Double,
) {
    LOAD(0x04, "Carga do motor", "%", 1, { it[0] * 100.0 / 255.0 }),
    COOLANT_TEMP(0x05, "Temp. do motor", "°C", 1, { it[0] - 40.0 }),
    MAP(0x0B, "Pressão do coletor", "kPa", 1, { it[0].toDouble() }),
    RPM(0x0C, "Rotação", "rpm", 2, { (256.0 * it[0] + it[1]) / 4.0 }),
    SPEED(0x0D, "Velocidade", "km/h", 1, { it[0].toDouble() }),
    INTAKE_TEMP(0x0F, "Ar de admissão", "°C", 1, { it[0] - 40.0 }),
    MAF(0x10, "Fluxo de ar (MAF)", "g/s", 2, { (256.0 * it[0] + it[1]) / 100.0 }),
    THROTTLE(0x11, "Acelerador", "%", 1, { it[0] * 100.0 / 255.0 }),
    FUEL_LEVEL(0x2F, "Nível de combustível", "%", 1, { it[0] * 100.0 / 255.0 }),
    MODULE_VOLTAGE(0x42, "Bateria", "V", 2, { (256.0 * it[0] + it[1]) / 1000.0 }),
    AMBIENT_TEMP(0x46, "Temp. ambiente", "°C", 1, { it[0] - 40.0 }),
    FUEL_RATE(0x5E, "Taxa de combustível", "L/h", 2, { (256.0 * it[0] + it[1]) / 20.0 }),
    ;

    /** Comando enviado ao adaptador, por exemplo "010C". */
    val command: String get() = "01" + hex2(code)

    /** Prefixo esperado na resposta, por exemplo "410C". */
    val responsePrefix: String get() = "41" + hex2(code)

    companion object {
        fun hex2(n: Int): String = n.toString(16).uppercase().padStart(2, '0')
    }
}

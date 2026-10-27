package br.com.monitor2008.domain

import br.com.monitor2008.data.Sample
import kotlin.math.max
import kotlin.math.min

/**
 * Mistura de combustível representada pela fração equivalente de etanol (0 a 1).
 * Gasolina comum E30 ≈ 0,30; etanol hidratado ≈ 0,95.
 * AFR e densidade são interpolados entre gasolina pura e etanol puro (aproximação; a calibração
 * por tanque cheio corrige o resto).
 */
data class FuelBlend(val ethanolFraction: Double) {
    val afr: Double get() = AFR_GASOLINE * (1 - ethanolFraction) + AFR_ETHANOL * ethanolFraction
    val densityGPerL: Double get() = DENSITY_GASOLINE * (1 - ethanolFraction) + DENSITY_ETHANOL * ethanolFraction

    companion object {
        const val AFR_GASOLINE = 14.7
        const val AFR_ETHANOL = 9.0
        const val DENSITY_GASOLINE = 745.0
        const val DENSITY_ETHANOL = 789.0

        val GASOLINA_E30 = FuelBlend(0.30)
        val GASOLINA_E32 = FuelBlend(0.32)
        val ETANOL = FuelBlend(0.95)
    }
}

/** Dados do motor usados no método MAP. 1.6 flex: 1,598 L; eficiência volumétrica é chute inicial. */
data class EngineConfig(
    val displacementL: Double = 1.598,
    val volumetricEfficiency: Double = 0.80,
)

enum class FlowMethod { FUEL_RATE, MAF, MAP }

data class FlowEstimate(val litersPerHour: Double, val method: FlowMethod)

/** Estado do ajuste por abastecimento (tanque cheio a tanque cheio). */
data class RefuelState(
    val baselineValid: Boolean = false,
    val uncalLiters: Double = 0.0,     // litros estimados sem calibração desde o último tanque cheio
    val pendingRealLiters: Double = 0.0, // abastecimentos parciais desde o último tanque cheio
    val kmSinceFull: Double = 0.0,
    val factor: Double = 1.0,
)

data class RefuelResult(val state: RefuelState, val realKmPerL: Double?)

object FuelCalc {
    private const val GAS_CONSTANT = 287.05 // J/(kg·K), ar seco

    /** L/h a partir do fluxo de ar, assumindo mistura estequiométrica. */
    fun litersPerHourFromMaf(mafGs: Double, blend: FuelBlend): Double =
        mafGs * 3600.0 / (blend.afr * blend.densityGPerL)

    /** Fluxo de ar estimado (g/s) por densidade-velocidade, quando o carro não tem MAF. */
    fun mafFromMap(mapKpa: Double, rpm: Double, intakeC: Double, engine: EngineConfig): Double {
        val rho = mapKpa * 1000.0 / (GAS_CONSTANT * (intakeC + 273.15)) // kg/m³
        val volumePerSecond = rpm / 120.0 * engine.displacementL / 1000.0 // m³/s (4 tempos)
        return rho * engine.volumetricEfficiency * volumePerSecond * 1000.0
    }

    /**
     * Melhor estimativa de vazão com o que o carro entregou: taxa de combustível (5E),
     * depois MAF, depois MAP + rotação. [calibration] vem dos abastecimentos (1.0 = sem ajuste).
     */
    fun estimate(s: Sample, blend: FuelBlend, engine: EngineConfig, calibration: Double = 1.0): FlowEstimate? {
        s.fuelRateLph?.let { return FlowEstimate(it * calibration, FlowMethod.FUEL_RATE) }
        s.mafGs?.let { return FlowEstimate(litersPerHourFromMaf(it, blend) * calibration, FlowMethod.MAF) }
        val map = s.mapKpa
        val rpm = s.rpm
        if (map != null && rpm != null) {
            val maf = mafFromMap(map, rpm, s.intakeC ?: 30.0, engine)
            return FlowEstimate(litersPerHourFromMaf(maf, blend) * calibration, FlowMethod.MAP)
        }
        return null
    }

    /** km/l instantâneo. Null parado ou sem consumo; limitado a 50 (corte de injeção em descida). */
    fun kmPerL(speedKmh: Double, litersPerHour: Double): Double? =
        if (litersPerHour > 0.05 && speedKmh >= 3.0) min(speedKmh / litersPerHour, 50.0) else null

    /** Fator = litros reais / litros estimados, limitado entre 0,5 e 1,5. */
    fun calibrationFactor(realLiters: Double, estimatedLiters: Double): Double =
        max(0.5, min(1.5, realLiters / estimatedLiters))

    /** Aplica um abastecimento ao estado de calibração. Só tanques cheios consecutivos recalibram. */
    fun applyRefuel(s: RefuelState, liters: Double, full: Boolean): RefuelResult {
        if (!full) return RefuelResult(s.copy(pendingRealLiters = s.pendingRealLiters + liters), null)
        val real = s.pendingRealLiters + liters
        val enoughData = s.baselineValid && s.uncalLiters > 1.0 && s.kmSinceFull > 20.0
        return if (enoughData) {
            val f = calibrationFactor(real, s.uncalLiters)
            RefuelResult(RefuelState(baselineValid = true, factor = f), s.kmSinceFull / real)
        } else {
            RefuelResult(RefuelState(baselineValid = true, factor = s.factor), null)
        }
    }
}

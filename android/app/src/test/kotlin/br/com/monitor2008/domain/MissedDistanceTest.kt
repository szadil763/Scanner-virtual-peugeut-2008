package br.com.monitor2008.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MissedDistanceTest {
    @Test fun tripWithoutTheAppIsFound() {
        // Na última leitura: contador 1000 km, app em 500 km. Depois o carro rodou 35 km sem o app.
        assertEquals(35.0, MissedDistance.missedKm(1000.0, 500.0, 1035.0, 500.0)!!, 1e-9)
    }

    @Test fun kmSeenByTheAppAreNotMissed() {
        // 40 km no contador, 38,6 vistos pelo app: diferença de arredondamento, nada perdido.
        assertNull(MissedDistance.missedKm(1000.0, 500.0, 1040.0, 538.6))
        // 50 km no contador, 20 vistos: 30 perdidos (parte da viagem sem conexão).
        assertEquals(30.0, MissedDistance.missedKm(1000.0, 500.0, 1050.0, 520.0)!!, 1e-9)
    }

    @Test fun counterResetOrFirstReadingIsIgnored() {
        assertNull(MissedDistance.missedKm(1000.0, 500.0, 12.0, 510.0)) // falhas apagadas
        assertNull(MissedDistance.missedKm(null, null, 1035.0, 500.0)) // primeira leitura
    }
}

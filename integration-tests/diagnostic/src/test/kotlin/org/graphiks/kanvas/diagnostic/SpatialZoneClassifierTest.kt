package org.graphiks.kanvas.diagnostic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpatialZoneClassifierTest {
    @Test
    fun `solid white image classifies all pixels as SOLID`() {
        val w = 64; val h = 64
        val white = ByteArray(w * h * 4) { 255.toByte() }
        val zones = SpatialZoneClassifier.classify(white, w, h)
        assertEquals(ZoneType.SOLID, zones[0])
        assertEquals(ZoneType.SOLID, zones[zones.size - 1])
    }

    @Test
    fun `sharp edge image classifies interior as EDGE at boundary`() {
        val w = 64; val h = 64
        val half = ByteArray(w * h * 4) { i ->
            val px = (i / 4) % w
            if (px < w / 2) 0.toByte() else 255.toByte()
        }
        val zones = SpatialZoneClassifier.classify(half, w, h)
        // The boundary column should be detected as EDGE
        val boundaryIdx = (h / 2) * w + (w / 2)
        assertEquals(ZoneType.EDGE, zones[boundaryIdx])
    }
}

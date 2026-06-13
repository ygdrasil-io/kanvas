package org.graphiks.kanvas.font.scaler

import kotlin.test.Test
import kotlin.test.assertEquals

class FontScalerSurfaceTest {
    @Test
    fun exposesScalerGeometryMetricsAndVariationValueObjects() {
        val bounds = GlyphBounds(
            left = 1.0,
            top = 2.0,
            right = 3.0,
            bottom = 4.0,
        )
        val metrics = GlyphMetrics(
            advanceX = 9.0,
            advanceY = 0.0,
            bounds = bounds,
        )
        val outline = GlyphOutline(
            glyphId = 42u,
            contours = emptyList(),
        )
        val position = VariationPosition(axes = mapOf("wght" to 700.0))

        assertEquals(2.0, bounds.top)
        assertEquals(bounds, metrics.bounds)
        assertEquals(42u, outline.glyphId)
        assertEquals(700.0, position.axes.getValue("wght"))
    }
}

package org.graphiks.kanvas.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.graphiks.kanvas.render.ir.RuntimeEffectAbi

class W6dRuntimeEffectCatalogTest {
    @Test fun `image opacity descriptor is additive and leaves w5h hash unchanged`() {
        val shaderBefore = assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1))
        val image = assertNotNull(RuntimeEffect.registered("kanvas.runtime.image-opacity", 1))

        assertEquals(RuntimeEffectAbi.SHADER, shaderBefore.kind)
        assertEquals(RuntimeEffectAbi.IMAGE_FILTER, image.kind)
        assertEquals("2c7646732d3484bdc2d99a813af1ae721872bbfeb3b2030e2ee4eb39be53d6c8", shaderBefore.abiHash)
    }
}

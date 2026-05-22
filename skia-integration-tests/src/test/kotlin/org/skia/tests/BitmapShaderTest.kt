package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("ALIAS: upstream BitmapShaderGM — partial coverage in ClippedBitmapShadersGM/BitmapTiledGM/BitmapSubsetShaderGM; full matrix TODO")
class BitmapShaderTest {

    @Test
    fun `BitmapShaderGM placeholder`() {
        val gm = BitmapShaderGM()
        TestUtils.runGmTest(gm)
    }
}

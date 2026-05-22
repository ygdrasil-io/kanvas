package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("ALIAS: upstream ConicalGradientsGM — already ported as ConicalGradients2pt{Inside,Outside,TileMode}GMs")
class ConicalGradientsTest {

    @Test
    fun `ConicalGradientsGM placeholder alias`() {
        val gm = ConicalGradientsGM()
        TestUtils.runGmTest(gm)
    }
}

package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.SURFACE_SNAPSHOT_SUBSET: SkSurface.makeImageSnapshot(SkIRect) not yet implemented")
class SurfaceUnderdrawTest {

    @Test
    fun `SurfaceUnderdrawGM surface_underdraw placeholder`() {
        TestUtils.runGmTest(SurfaceUnderdrawGM())
    }
}

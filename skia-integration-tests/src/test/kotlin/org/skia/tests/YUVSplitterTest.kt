package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.YUVA_SPLIT_KERNEL + STUB.GPU_YUVA_TEXTURE_PATH: requires MakeYUVAPlanesAsA8 and GPU texture assembly path")
class YUVSplitterTest {

    @Test
    fun `YUVSplitterGM placeholder`() {
        TestUtils.runGmTest(YUVSplitterGM())
    }
}

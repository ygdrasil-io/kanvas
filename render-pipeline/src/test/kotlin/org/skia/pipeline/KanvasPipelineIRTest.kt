package org.skia.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals

class KanvasPipelineIRTest {
    @Test
    fun dumpOrderingIsDeterministic() {
        val dump1 = KanvasPipelineIR.demoSolidRectIr(Rgba(1f, 0f, 0f, 1f)).dump()
        val dump2 = KanvasPipelineIR.demoSolidRectIr(Rgba(1f, 0f, 0f, 1f)).dump()

        assertEquals(dump1, dump2)
        assertEquals(
            """
            KanvasPipelineIR(v1)
            00 SeedDeviceCoords
            01 ConstantColor(1.0,0.0,0.0,1.0)
            02 ApplyCoverage(Full)
            03 LoadDst
            04 BlendMode(SrcOver)
            05 Store
            fallback=none
            """.trimIndent(),
            dump1,
        )
    }

    @Test
    fun appendUnsupportedDoesNotMutateBuilder() {
        val builder = KanvasPipelineIR.builder().append(PipelineOp.SeedDeviceCoords)

        val result = builder.appendTransactional { draft ->
            draft.append(PipelineOp.ConstantColor(Rgba(0f, 1f, 0f, 1f)))
            AppendResult.Unsupported("not supported")
        }

        assertEquals(AppendResult.Unsupported("not supported"), result)
        val dump = builder.build().dump()
        assertEquals(
            """
            KanvasPipelineIR(v1)
            00 SeedDeviceCoords
            fallback=none
            """.trimIndent(),
            dump,
        )
    }
}

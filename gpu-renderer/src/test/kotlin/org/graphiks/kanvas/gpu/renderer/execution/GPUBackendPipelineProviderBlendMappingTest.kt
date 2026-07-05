package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode as BackendGPUBlendMode
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode as ContractGPUBlendMode

class GPUBackendPipelineProviderBlendMappingTest {
    @Test
    fun `generic fullscreen blend modes map to backend GPU blend modes`() {
        listOf(
            ContractGPUBlendMode.Src to BackendGPUBlendMode.SRC,
            ContractGPUBlendMode.SrcOver to BackendGPUBlendMode.SRC_OVER,
            ContractGPUBlendMode.Multiply to BackendGPUBlendMode.MULTIPLY,
            ContractGPUBlendMode.Screen to BackendGPUBlendMode.SCREEN,
        ).forEach { (contractMode, backendMode) ->
            assertEquals(
                backendMode,
                contractMode.toGPUBackendBlendModeForFullscreen(),
                "$contractMode should map to $backendMode",
            )
        }
    }

    @Test
    fun `unsupported generic fullscreen blend modes fail explicitly`() {
        val dstOverFailure = assertFailsWith<IllegalStateException> {
            ContractGPUBlendMode.DstOver.toGPUBackendBlendModeForFullscreen()
        }
        assertContains(
            dstOverFailure.message.orEmpty(),
            "GPUBackendPipelineProvider does not support GPU blend mode DstOver yet",
        )

        val customFailure = assertFailsWith<IllegalStateException> {
            ContractGPUBlendMode.Custom.toGPUBackendBlendModeForFullscreen()
        }
        assertContains(
            customFailure.message.orEmpty(),
            "GPUBackendPipelineProvider does not support GPU blend mode Custom yet",
        )
    }

    @Test
    fun `representable legacy blend modes map to generic contract modes`() {
        listOf(
            BackendGPUBlendMode.SRC to ContractGPUBlendMode.Src,
            BackendGPUBlendMode.SRC_OVER to ContractGPUBlendMode.SrcOver,
            BackendGPUBlendMode.MULTIPLY to ContractGPUBlendMode.Multiply,
            BackendGPUBlendMode.SCREEN to ContractGPUBlendMode.Screen,
        ).forEach { (backendMode, contractMode) ->
            assertEquals(
                contractMode,
                backendMode.toContractBlendModeOrNull(),
                "$backendMode should map to $contractMode",
            )
        }
    }

    @Test
    fun `non representable legacy blend modes return null for fallback routing`() {
        assertNull(BackendGPUBlendMode.DST.toContractBlendModeOrNull())
        assertNull(BackendGPUBlendMode.PLUS.toContractBlendModeOrNull())
    }
}

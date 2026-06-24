package org.graphiks.kanvas.gpu.renderer.product

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity

data class GpuProductFlagConfig(
    val fillRRectEnabled: Boolean = true,
    val linearGradientEnabled: Boolean = true,
    val scissorEnabled: Boolean = true,
) {
    fun buildCapabilities(
        implementation: GPUImplementationIdentity = GpuProductFlagConfig.defaultImplementation(),
    ): GPUCapabilities {
        val facts = mutableListOf<GPUCapabilityFact>()
        if (fillRRectEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.fill_rrect.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:fillRRect",
            )
        }
        if (linearGradientEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.linear_gradient.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:linearGradient",
            )
        }
        if (scissorEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.scissor.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:scissor",
            )
        }
        return GPUCapabilities(
            implementation = implementation,
            facts = facts,
            snapshotId = "product-flags",
        )
    }

    companion object {
        const val FillRRectProperty: String = "kanvas.gpu.renderer.product.fillRRect"
        const val FillRRectDisableProperty: String = "kanvas.gpu.renderer.product.fillRRect.disable"
        const val LinearGradientProperty: String = "kanvas.gpu.renderer.product.linearGradient"
        const val LinearGradientDisableProperty: String = "kanvas.gpu.renderer.product.linearGradient.disable"
        const val ScissorProperty: String = "kanvas.gpu.renderer.product.scissor"
        const val ScissorDisableProperty: String = "kanvas.gpu.renderer.product.scissor.disable"

        fun defaultImplementation(): GPUImplementationIdentity =
            GPUImplementationIdentity(
                facadeName = "kanvas-gpu-renderer",
                implementationName = "product-flags",
                adapterName = "product-adapter",
                deviceName = "product-device",
            )

        fun fromSystemProperties(
            propertyReader: (String) -> String? = System::getProperty,
        ): GpuProductFlagConfig {
            val fillRRectDisabled = propertyReader(FillRRectDisableProperty).toBoolean()
            val linearGradientDisabled = propertyReader(LinearGradientDisableProperty).toBoolean()
            val scissorDisabled = propertyReader(ScissorDisableProperty).toBoolean()
            return GpuProductFlagConfig(
                fillRRectEnabled = !fillRRectDisabled,
                linearGradientEnabled = !linearGradientDisabled,
                scissorEnabled = !scissorDisabled,
            )
        }
    }
}

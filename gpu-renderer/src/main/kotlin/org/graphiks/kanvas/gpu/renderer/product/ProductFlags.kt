package org.graphiks.kanvas.gpu.renderer.product

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity

/** Product flag configuration controlling GPU renderer feature enablement. */
data class GpuProductFlagConfig(
    val fillRRectEnabled: Boolean = true,
    val linearGradientEnabled: Boolean = true,
    val scissorEnabled: Boolean = true,
    val radialGradientEnabled: Boolean = true,
    val sweepGradientEnabled: Boolean = true,
    val pathFillEnabled: Boolean = true,
    val saveLayerEnabled: Boolean = true,
    val dstReadEnabled: Boolean = true,
    val strokeEnabled: Boolean = true,
    val dashEnabled: Boolean = true,
    val boundedClipEnabled: Boolean = true,
    val bitmapRectEnabled: Boolean = true,
    val tileModesEnabled: Boolean = true,
    val blurFilterEnabled: Boolean = true,
    val colorMatrixFilterEnabled: Boolean = true,
    val textA8Enabled: Boolean = true,
    val textSDFEnabled: Boolean = true,
    val runtimeEffectsEnabled: Boolean = true,
    val performanceGatesEnabled: Boolean = true,
    val verticesEnabled: Boolean = true,
) {
    /** Builds a GPUCapabilities instance from the current flag configuration. */
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
        if (radialGradientEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.radial_gradient.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:radialGradient",
            )
        }
        if (sweepGradientEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.sweep_gradient.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:sweepGradient",
            )
        }
        if (pathFillEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.path_fill.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:pathFill",
            )
        }
        if (saveLayerEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.savelayer.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:saveLayer",
            )
        }
        if (dstReadEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.dst_read.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:dstRead",
            )
        }
        if (strokeEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.stroke.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:stroke",
            )
        }
        if (dashEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.dash.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:dash",
            )
        }
        if (boundedClipEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.bounded_clip.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:boundedClip",
            )
        }
        if (bitmapRectEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.bitmap_rect.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:bitmapRect",
            )
        }
        if (tileModesEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.tile_modes.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:tileModes",
            )
        }
        if (blurFilterEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.blur_filter.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:blurFilter",
            )
        }
        if (colorMatrixFilterEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.color_matrix_filter.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:colorMatrixFilter",
            )
        }
        if (textA8Enabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.text_a8_atlas.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:textA8",
            )
        }
        if (textSDFEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.text_sdf_atlas.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:textSDF",
            )
        }
        if (runtimeEffectsEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.runtime_effects.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:runtimeEffects",
            )
        }
        if (performanceGatesEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.performance_gates.native",
                source = "product-flags",
                value = "gated",
                affectsValidity = true,
                evidenceLabel = "product-flag:performanceGates",
            )
        }
        if (verticesEnabled) {
            facts += GPUCapabilityFact(
                name = "first_slice.vertices.native",
                source = "product-flags",
                value = "supported",
                affectsValidity = true,
                evidenceLabel = "product-flag:vertices",
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
        const val RadialGradientProperty: String = "kanvas.gpu.renderer.product.radialGradient"
        const val RadialGradientDisableProperty: String = "kanvas.gpu.renderer.product.radialGradient.disable"
        const val SweepGradientProperty: String = "kanvas.gpu.renderer.product.sweepGradient"
        const val SweepGradientDisableProperty: String = "kanvas.gpu.renderer.product.sweepGradient.disable"
        const val PathFillProperty: String = "kanvas.gpu.renderer.product.pathFill"
        const val PathFillDisableProperty: String = "kanvas.gpu.renderer.product.pathFill.disable"
        const val SaveLayerProperty: String = "kanvas.gpu.renderer.product.saveLayer"
        const val SaveLayerDisableProperty: String = "kanvas.gpu.renderer.product.saveLayer.disable"
        const val DstReadProperty: String = "kanvas.gpu.renderer.product.dstRead"
        const val DstReadDisableProperty: String = "kanvas.gpu.renderer.product.dstRead.disable"
        const val StrokeProperty: String = "kanvas.gpu.renderer.product.stroke"
        const val StrokeDisableProperty: String = "kanvas.gpu.renderer.product.stroke.disable"
        const val DashProperty: String = "kanvas.gpu.renderer.product.dash"
        const val DashDisableProperty: String = "kanvas.gpu.renderer.product.dash.disable"
        const val BoundedClipProperty: String = "kanvas.gpu.renderer.product.boundedClip"
        const val BoundedClipDisableProperty: String = "kanvas.gpu.renderer.product.boundedClip.disable"
        const val BitmapRectProperty: String = "kanvas.gpu.renderer.product.bitmapRect"
        const val BitmapRectDisableProperty: String = "kanvas.gpu.renderer.product.bitmapRect.disable"
        const val TileModesProperty: String = "kanvas.gpu.renderer.product.tileModes"
        const val TileModesDisableProperty: String = "kanvas.gpu.renderer.product.tileModes.disable"
        const val BlurFilterProperty: String = "kanvas.gpu.renderer.product.blurFilter"
        const val BlurFilterDisableProperty: String = "kanvas.gpu.renderer.product.blurFilter.disable"
        const val ColorMatrixFilterProperty: String = "kanvas.gpu.renderer.product.colorMatrixFilter"
        const val ColorMatrixFilterDisableProperty: String = "kanvas.gpu.renderer.product.colorMatrixFilter.disable"
        const val TextA8Property: String = "kanvas.gpu.renderer.product.textA8"
        const val TextA8DisableProperty: String = "kanvas.gpu.renderer.product.textA8.disable"
        const val TextSDFProperty: String = "kanvas.gpu.renderer.product.textSDF"
        const val TextSDFDisableProperty: String = "kanvas.gpu.renderer.product.textSDF.disable"
        const val RuntimeEffectsProperty: String = "kanvas.gpu.renderer.product.runtimeEffects"
        const val RuntimeEffectsDisableProperty: String = "kanvas.gpu.renderer.product.runtimeEffects.disable"
        const val PerformanceGatesProperty: String = "kanvas.gpu.renderer.product.performanceGates"
        const val PerformanceGatesDisableProperty: String = "kanvas.gpu.renderer.product.performanceGates.disable"
        const val VerticesProperty: String = "kanvas.gpu.renderer.product.vertices"
        const val VerticesDisableProperty: String = "kanvas.gpu.renderer.product.vertices.disable"

        /** Returns the default GPU implementation identity for product flags. */
        fun defaultImplementation(): GPUImplementationIdentity =
            GPUImplementationIdentity(
                facadeName = "kanvas-gpu-renderer",
                implementationName = "product-flags",
                adapterName = "product-adapter",
                deviceName = "product-device",
            )

        /** Creates a configuration from system properties with disable toggles. */
        fun fromSystemProperties(
            propertyReader: (String) -> String? = System::getProperty,
        ): GpuProductFlagConfig {
            val fillRRectDisabled = propertyReader(FillRRectDisableProperty).toBoolean()
            val linearGradientDisabled = propertyReader(LinearGradientDisableProperty).toBoolean()
            val scissorDisabled = propertyReader(ScissorDisableProperty).toBoolean()
            val radialGradientDisabled = propertyReader(RadialGradientDisableProperty).toBoolean()
            val sweepGradientDisabled = propertyReader(SweepGradientDisableProperty).toBoolean()
            val pathFillDisabled = propertyReader(PathFillDisableProperty).toBoolean()
            val saveLayerDisabled = propertyReader(SaveLayerDisableProperty).toBoolean()
            val dstReadDisabled = propertyReader(DstReadDisableProperty).toBoolean()
            val strokeDisabled = propertyReader(StrokeDisableProperty).toBoolean()
            val dashDisabled = propertyReader(DashDisableProperty).toBoolean()
            val boundedClipDisabled = propertyReader(BoundedClipDisableProperty).toBoolean()
            val bitmapRectDisabled = propertyReader(BitmapRectDisableProperty).toBoolean()
            val tileModesDisabled = propertyReader(TileModesDisableProperty).toBoolean()
            val blurFilterDisabled = propertyReader(BlurFilterDisableProperty).toBoolean()
            val colorMatrixFilterDisabled = propertyReader(ColorMatrixFilterDisableProperty).toBoolean()
            val textA8Disabled = propertyReader(TextA8DisableProperty).toBoolean()
            val textSDFDisabled = propertyReader(TextSDFDisableProperty).toBoolean()
            val runtimeEffectsDisabled = propertyReader(RuntimeEffectsDisableProperty).toBoolean()
            val performanceGatesDisabled = propertyReader(PerformanceGatesDisableProperty).toBoolean()
            val verticesDisabled = propertyReader(VerticesDisableProperty).toBoolean()
            return GpuProductFlagConfig(
                fillRRectEnabled = !fillRRectDisabled,
                linearGradientEnabled = !linearGradientDisabled,
                scissorEnabled = !scissorDisabled,
                radialGradientEnabled = !radialGradientDisabled,
                sweepGradientEnabled = !sweepGradientDisabled,
                pathFillEnabled = !pathFillDisabled,
                saveLayerEnabled = !saveLayerDisabled,
                dstReadEnabled = !dstReadDisabled,
                strokeEnabled = !strokeDisabled,
                dashEnabled = !dashDisabled,
                boundedClipEnabled = !boundedClipDisabled,
                bitmapRectEnabled = !bitmapRectDisabled,
                tileModesEnabled = !tileModesDisabled,
                blurFilterEnabled = !blurFilterDisabled,
                colorMatrixFilterEnabled = !colorMatrixFilterDisabled,
                textA8Enabled = !textA8Disabled,
                textSDFEnabled = !textSDFDisabled,
                runtimeEffectsEnabled = !runtimeEffectsDisabled,
                performanceGatesEnabled = !performanceGatesDisabled,
                verticesEnabled = !verticesDisabled,
            )
        }
    }
}

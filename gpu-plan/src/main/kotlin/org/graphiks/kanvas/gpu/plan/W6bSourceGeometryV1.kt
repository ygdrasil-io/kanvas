package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.LayerMappingF64

/** Sampling geometry shared by semantic recipe operands and their physical source bindings. */
internal interface W6bSourceGeometryV1 {
    val originDeviceI32: Point2I32
    val mapping: LayerMappingF64
    fun copyExtentI32(): SizeI32
    fun copyKnownContentDeviceI32(): RectI32?
    fun copyDesiredOutputDeviceI32(): RectI32?
    fun copyRequiredInputDeviceI32(): RectI32?
    fun copyProducedOutputDeviceI32(): RectI32?
    fun copyDeviceBoundsI32(): RectI32
}

internal data class W6bRecipeSymbolV1(val ordinalI32: Int)

internal class W6bRecipeSourceV1(
    val symbol: W6bRecipeSymbolV1,
    extent: SizeI32,
    override val originDeviceI32: Point2I32,
    override val mapping: LayerMappingF64,
    knownContentDeviceI32: RectI32? = null,
    desiredOutputDeviceI32: RectI32? = null,
    requiredInputDeviceI32: RectI32? = null,
    producedOutputDeviceI32: RectI32? = null,
) : W6bSourceGeometryV1 {
    private val extent = extent.copy()
    private val known = knownContentDeviceI32?.copy()
    private val desired = desiredOutputDeviceI32?.copy()
    private val required = requiredInputDeviceI32?.copy()
    private val produced = producedOutputDeviceI32?.copy()
    override fun copyExtentI32(): SizeI32 = extent.copy()
    override fun copyKnownContentDeviceI32(): RectI32? = known?.copy()
    override fun copyDesiredOutputDeviceI32(): RectI32? = desired?.copy()
    override fun copyRequiredInputDeviceI32(): RectI32? = required?.copy()
    override fun copyProducedOutputDeviceI32(): RectI32? = produced?.copy()
    override fun copyDeviceBoundsI32(): RectI32 = RectI32(originDeviceI32.x, originDeviceI32.y,
        Math.addExact(originDeviceI32.x, extent.width), Math.addExact(originDeviceI32.y, extent.height))
    fun withSymbol(
        symbol: W6bRecipeSymbolV1,
        extent: SizeI32 = this.extent,
        originDeviceI32: Point2I32 = this.originDeviceI32,
        knownContentDeviceI32: RectI32? = known,
        desiredOutputDeviceI32: RectI32? = desired,
        requiredInputDeviceI32: RectI32? = required,
        producedOutputDeviceI32: RectI32? = produced,
    ): W6bRecipeSourceV1 = W6bRecipeSourceV1(symbol, extent, originDeviceI32, mapping,
        knownContentDeviceI32, desiredOutputDeviceI32, requiredInputDeviceI32, producedOutputDeviceI32)
}

package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.TriangleMeshF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.render.ir.PreparedVerticesUploadPayloadV1

/** The existing prepared Vertices/Mesh geometry ABI, now retained before frame publication. */
public class W5bVerticesDraw internal constructor(
    override public val commandIndex: Int,
    override public val materialAuthority: PlanDrawMaterialAuthority,
    public val geometryF32: TriangleMeshF32,
    colorsRgba8: ByteArray?,
    public val transformF32: Matrix3x3F32,
    boundsI32: RectI32,
    scissorI32: RectI32,
    override public val blend: BlendPlan,
    public val primitiveBlend: BlendPlan?,
    uploadPayload: PreparedVerticesUploadPayloadV1? = null,
) : PlanDraw {
    private val colors = colorsRgba8?.copyOf()
    private val bounds = boundsI32.copy()
    private val scissor = scissorI32.copy()
    private val upload = uploadPayload
    override public val color: ColorF32 get() = error("Vertices carries the frame material reference only")
    override public val coverage: CoveragePlan = CoveragePlan.FullOrScissor
    override public val sample: SamplePlan = SamplePlan.SingleSample
    public val vertexStrideBytesI32: Int = 8 + (if (colors == null) 0 else 4) +
        (if (geometryF32.copyCoordinatesF32() == null) 0 else 8)
    public val indexElementBytesI32: Int? = geometryF32.maxIndexI32?.let { if (it <= 65535) 2 else 4 }
    public fun copyColorsRgba8(): ByteArray? = colors?.copyOf()
    public fun copyBoundsI32(): RectI32 = bounds.copy()
    public fun copyScissorI32(): RectI32 = scissor.copy()
    /** Present only after capability admission, before graph publication. */
    public fun sealedUploadPayloadOrNull(): PreparedVerticesUploadPayloadV1? = upload
    internal fun withSealedUpload(payload: PreparedVerticesUploadPayloadV1): W5bVerticesDraw {
        require(payload.vertexCountI32 == geometryF32.vertexCountI32 &&
            payload.indexCountI32 == geometryF32.indexCountI32 &&
            payload.vertexStrideBytesI32 == vertexStrideBytesI32 &&
            payload.indexElementBytesI32 == indexElementBytesI32)
        return W5bVerticesDraw(commandIndex, materialAuthority, geometryF32, colors, transformF32, bounds, scissor,
            blend, primitiveBlend, payload)
    }
    internal fun withMaterialRef(ref: MaterialPlanRef): W5bVerticesDraw = W5bVerticesDraw(commandIndex,
        PlanDrawMaterialAuthority.MaterialV5(ref), geometryF32, colors, transformF32, bounds, scissor, blend, primitiveBlend, upload)
    internal fun withBlend(value: BlendPlan): W5bVerticesDraw = W5bVerticesDraw(commandIndex,
        materialAuthority, geometryF32, colors, transformF32, bounds, scissor, value, primitiveBlend, upload)
}

package org.graphiks.kanvas.gpu.renderer.wgsl

import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.state.GPUSourceCoverageEncoding

data class GPUPreparedTextVertexAttribute(
    val location: Int,
    val offsetBytes: Long,
    val format: String,
) {
    init {
        require(location >= 0)
        require(offsetBytes >= 0L)
        require(format.isNotBlank())
    }
}

@ConsistentCopyVisibility
data class GPUPreparedTextVertexLayout private constructor(
    val arrayStrideBytes: Long,
    val stepMode: String,
    val attributes: List<GPUPreparedTextVertexAttribute>,
) {
    constructor(
        arrayStrideBytes: Long,
        stepMode: String,
        attributes: Collection<GPUPreparedTextVertexAttribute>,
    ) : this(
        arrayStrideBytes = arrayStrideBytes,
        stepMode = stepMode,
        attributes = immutableList(attributes),
    )

    init {
        require(arrayStrideBytes > 0L)
        require(stepMode.isNotBlank())
        require(attributes.map { it.location }.distinct().size == attributes.size)
        require(attributes.map { it.offsetBytes }.distinct().size == attributes.size)
    }
}

data class GPUPreparedTextVertexResult(
    val deviceX: Float,
    val deviceY: Float,
    val ndcX: Float,
    val ndcY: Float,
    val uvX: Float,
    val uvY: Float,
    val localX: Float,
    val localY: Float,
)

enum class GPUPreparedTextClipVariant {
    None,
    AnalyticRectHard,
    AnalyticRectAA,
    AnalyticRRectHard,
    AnalyticRRectAA,
    CoverageMask,
}

object PreparedTextA8Shader {
    val VertexLayout: GPUPreparedTextVertexLayout = GPUPreparedTextVertexLayout(
        arrayStrideBytes = 64L,
        stepMode = "Instance",
        attributes = listOf(
            GPUPreparedTextVertexAttribute(0, 0L, "Float32x2"),
            GPUPreparedTextVertexAttribute(1, 8L, "Float32x2"),
            GPUPreparedTextVertexAttribute(2, 16L, "Float32x2"),
            GPUPreparedTextVertexAttribute(3, 24L, "Float32x2"),
            GPUPreparedTextVertexAttribute(4, 32L, "Float32x4"),
        ),
    )

    val vertexWgsl: String = """
struct PreparedTextDrawUniforms {
    targetSizeAndPaintAlpha: vec4<f32>,
    deviceToLocalRow0: vec4<f32>,
    deviceToLocalRow1: vec4<f32>,
    clipBounds: vec4<f32>,
    clipRadiiAndPadding: vec4<f32>,
}

struct PreparedTextVertexInput {
    @location(0) deviceTL: vec2<f32>,
    @location(1) deviceTR: vec2<f32>,
    @location(2) deviceBR: vec2<f32>,
    @location(3) deviceBL: vec2<f32>,
    @location(4) uvLTRB: vec4<f32>,
}

struct PreparedTextVertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
    @location(1) localPosition: vec2<f32>,
}

@group(0) @binding(0) var<uniform> drawUniforms: PreparedTextDrawUniforms;

@vertex
fn vs_main(
    input: PreparedTextVertexInput,
    @builtin(vertex_index) vertexIndex: u32,
) -> PreparedTextVertexOutput {
    let deviceCorners = array<vec2<f32>, 4>(
        input.deviceTL,
        input.deviceTR,
        input.deviceBR,
        input.deviceBL,
    );
    let uvCorners = array<vec2<f32>, 4>(
${PREPARED_TEXT_UV_CORNERS.joinToString(",\n") { "        input.uvLTRB.${it.swizzle}" }},
    );
    let cornerIndices = array<u32, 6>(
${PREPARED_TEXT_CORNER_INDICES.joinToString(",\n") { "        ${it}u" }},
    );
    let cornerIndex = cornerIndices[vertexIndex];
    let devicePosition = deviceCorners[cornerIndex];
    let ndcPosition = vec2<f32>(
        devicePosition.x / drawUniforms.targetSizeAndPaintAlpha.x * 2.0 - 1.0,
        1.0 - devicePosition.y / drawUniforms.targetSizeAndPaintAlpha.y * 2.0,
    );
    let homogeneousDevicePosition = vec3<f32>(devicePosition, 1.0);

    var output: PreparedTextVertexOutput;
    output.position = vec4<f32>(ndcPosition, 0.0, 1.0);
    output.uv = uvCorners[cornerIndex];
    output.localPosition = vec2<f32>(
        dot(drawUniforms.deviceToLocalRow0.xyz, homogeneousDevicePosition),
        dot(drawUniforms.deviceToLocalRow1.xyz, homogeneousDevicePosition),
    );
    return output;
}
""".trimIndent()

    val fragmentWgsl: String
        get() = fragmentWgsl(
            GPUSourceCoverageEncoding.ModulateRGBA,
            GPUPreparedTextClipVariant.None,
        )

    fun fragmentWgsl(
        sourceCoverageEncoding: GPUSourceCoverageEncoding,
        clipVariant: GPUPreparedTextClipVariant = GPUPreparedTextClipVariant.None,
    ): String {
        val encodedSource = when (sourceCoverageEncoding) {
            GPUSourceCoverageEncoding.Coverage ->
                "return vec4<f32>(coverageFactor);"
            GPUSourceCoverageEncoding.ModulateRGBA ->
                "return coverageFactor * preparedSource;"
            GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceAlpha ->
                "return vec4<f32>(coverageFactor * (1.0 - preparedSource.a));"
            GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceRGBA ->
                "return coverageFactor * (vec4<f32>(1.0) - preparedSource);"
            else -> error(
                "Prepared text does not admit source coverage encoding " +
                    sourceCoverageEncoding.name,
            )
        }
        val clipCoverageDeclarations = analyticClipDeclarations(clipVariant)
        val clipCoverageExpression = when (clipVariant) {
            GPUPreparedTextClipVariant.None -> "1.0"
            GPUPreparedTextClipVariant.CoverageMask ->
                "prepared_text_mask_coverage(input.position.xy)"
            GPUPreparedTextClipVariant.AnalyticRectHard,
            GPUPreparedTextClipVariant.AnalyticRectAA,
            -> "prepared_text_rect_coverage(input.position.xy)"
            GPUPreparedTextClipVariant.AnalyticRRectHard,
            GPUPreparedTextClipVariant.AnalyticRRectAA,
            -> "prepared_text_rrect_coverage(input.position.xy)"
        }
        return """
@group(2) @binding(0) var textAtlas: texture_2d<f32>;
@group(2) @binding(1) var textSampler: sampler;
${if (clipVariant == GPUPreparedTextClipVariant.CoverageMask) {
            """
@group(3) @binding(0) var preparedTextCoverageMask: texture_2d<f32>;

fn prepared_text_mask_coverage(position: vec2<f32>) -> f32 {
    let storedSample = textureLoad(
        preparedTextCoverageMask,
        vec2<i32>(position),
        0,
    );
    return storedSample[0];
}
""".trimIndent()
        } else {
            ""
        }}

$clipCoverageDeclarations

@fragment
fn fs_main(input: PreparedTextVertexOutput) -> @location(0) vec4<f32> {
    let materialPremul = kanvas_evaluate_material(input.localPosition);
    let glyphCoverage = textureSample(textAtlas, textSampler, input.uv).r;
    let clipCoverage = $clipCoverageExpression;
    let coverageFactor = glyphCoverage * clipCoverage;
    let paintAlpha = clamp(drawUniforms.targetSizeAndPaintAlpha.z, 0.0, 1.0);
    let preparedSource = materialPremul * paintAlpha;
    $encodedSource
}
""".trimIndent()
    }

    private fun analyticClipDeclarations(clipVariant: GPUPreparedTextClipVariant): String {
        if (clipVariant == GPUPreparedTextClipVariant.None ||
            clipVariant == GPUPreparedTextClipVariant.CoverageMask
        ) return ""
        val coverageBody = when (clipVariant) {
            GPUPreparedTextClipVariant.AnalyticRectHard,
            GPUPreparedTextClipVariant.AnalyticRRectHard,
            -> "return select(0.0, 1.0, distance <= 0.0);"
            GPUPreparedTextClipVariant.AnalyticRectAA,
            GPUPreparedTextClipVariant.AnalyticRRectAA,
            -> "return clamp(0.5 - distance, 0.0, 1.0);"
            GPUPreparedTextClipVariant.None -> error("None has no analytic declarations")
            GPUPreparedTextClipVariant.CoverageMask ->
                error("CoverageMask has no analytic declarations")
        }
        val shapeFunction = when (clipVariant) {
            GPUPreparedTextClipVariant.AnalyticRectHard,
            GPUPreparedTextClipVariant.AnalyticRectAA,
            -> """
fn prepared_text_rect_coverage(position: vec2<f32>) -> f32 {
    let center = (drawUniforms.clipBounds.xy + drawUniforms.clipBounds.zw) * 0.5;
    let halfExtent = (drawUniforms.clipBounds.zw - drawUniforms.clipBounds.xy) * 0.5;
    let q = abs(position - center) - halfExtent;
    let distance = length(max(q, vec2<f32>(0.0))) + min(max(q.x, q.y), 0.0);
    return prepared_text_clip_coverage(distance);
}
""".trimIndent()
            GPUPreparedTextClipVariant.AnalyticRRectHard,
            GPUPreparedTextClipVariant.AnalyticRRectAA,
            -> """
fn prepared_text_rrect_coverage(position: vec2<f32>) -> f32 {
    let center = (drawUniforms.clipBounds.xy + drawUniforms.clipBounds.zw) * 0.5;
    let halfExtent = (drawUniforms.clipBounds.zw - drawUniforms.clipBounds.xy) * 0.5;
    let radius = max(drawUniforms.clipRadiiAndPadding.xy, vec2<f32>(0.0001));
    let outerQ = abs(position - center) - halfExtent;
    let cornerQ = outerQ + radius;
    let normalizedLength = length(cornerQ / radius);
    let safeNormalizedLength = max(normalizedLength, 0.000001);
    let implicitValue = normalizedLength - 1.0;
    let implicitGradient = cornerQ / (radius * radius * safeNormalizedLength);
    let cornerDistance = implicitValue / max(length(implicitGradient), 0.000001);
    let stripDistance = max(outerQ.x, outerQ.y);
    let distance = select(
        stripDistance,
        cornerDistance,
        cornerQ.x > 0.0 && cornerQ.y > 0.0,
    );
    return prepared_text_clip_coverage(distance);
}
""".trimIndent()
            GPUPreparedTextClipVariant.None -> error("None has no analytic declarations")
            GPUPreparedTextClipVariant.CoverageMask ->
                error("CoverageMask has no analytic declarations")
        }
        return """
fn prepared_text_clip_coverage(distance: f32) -> f32 {
    $coverageBody
}

$shapeFunction
""".trimIndent()
    }

    fun deviceToNdc(
        deviceX: Float,
        deviceY: Float,
        targetWidth: Float,
        targetHeight: Float,
    ): Pair<Float, Float> {
        require(
            deviceX.isFinite() &&
                deviceY.isFinite() &&
                targetWidth.isFinite() &&
                targetHeight.isFinite() &&
                targetWidth > 0f &&
                targetHeight > 0f,
        ) {
            "Prepared text NDC conversion requires finite coordinates and positive target size"
        }
        return (deviceX / targetWidth * 2f - 1f) to
            (1f - deviceY / targetHeight * 2f)
    }

    fun vertexOracle(
        deviceQuad: List<Float>,
        uvLTRB: List<Float>,
        targetWidth: Float,
        targetHeight: Float,
        deviceToLocal: List<Float>,
    ): List<GPUPreparedTextVertexResult> {
        require(deviceQuad.size == 8 && deviceQuad.all(Float::isFinite)) {
            "Prepared text device quad requires four finite xy corners"
        }
        require(uvLTRB.size == 4 && uvLTRB.all(Float::isFinite)) {
            "Prepared text UV payload requires finite LTRB values"
        }
        require(deviceToLocal.size == 6 && deviceToLocal.all(Float::isFinite)) {
            "Prepared text device-to-local transform requires two finite affine rows"
        }

        val deviceCorners = listOf(
            deviceQuad[0] to deviceQuad[1],
            deviceQuad[2] to deviceQuad[3],
            deviceQuad[4] to deviceQuad[5],
            deviceQuad[6] to deviceQuad[7],
        )
        val uvCorners = PREPARED_TEXT_UV_CORNERS.map { corner ->
            uvLTRB[corner.xIndex] to uvLTRB[corner.yIndex]
        }
        return immutableList(
            PREPARED_TEXT_CORNER_INDICES.map { cornerIndex ->
                val (deviceX, deviceY) = deviceCorners[cornerIndex]
                val (ndcX, ndcY) = deviceToNdc(
                    deviceX,
                    deviceY,
                    targetWidth,
                    targetHeight,
                )
                val (uvX, uvY) = uvCorners[cornerIndex]
                GPUPreparedTextVertexResult(
                    deviceX = deviceX,
                    deviceY = deviceY,
                    ndcX = ndcX,
                    ndcY = ndcY,
                    uvX = uvX,
                    uvY = uvY,
                    localX = deviceToLocal[0] * deviceX +
                        deviceToLocal[1] * deviceY +
                        deviceToLocal[2],
                    localY = deviceToLocal[3] * deviceX +
                        deviceToLocal[4] * deviceY +
                        deviceToLocal[5],
                )
            },
        )
    }
}

private data class PreparedTextUvCorner(
    val xIndex: Int,
    val yIndex: Int,
) {
    val swizzle: String
        get() = "${UV_LTRB_COMPONENTS[xIndex]}${UV_LTRB_COMPONENTS[yIndex]}"

    init {
        require(xIndex == 0 || xIndex == 2)
        require(yIndex == 1 || yIndex == 3)
    }
}

private val PREPARED_TEXT_CORNER_INDICES: List<Int> =
    immutableList(listOf(0, 1, 2, 0, 2, 3))
private val PREPARED_TEXT_UV_CORNERS: List<PreparedTextUvCorner> = immutableList(
    listOf(
        PreparedTextUvCorner(0, 1),
        PreparedTextUvCorner(2, 1),
        PreparedTextUvCorner(2, 3),
        PreparedTextUvCorner(0, 3),
    ),
)
private const val UV_LTRB_COMPONENTS = "xyzw"

package org.skia.kadre.runtime

import kotlin.math.floor
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint

internal const val M73_DEFAULT_SCENE_CONTRACT_ID = "m73-linear-gradient-rect-replay-v1"

private const val M72_SCENE_CONTRACT_ID = "m72-solid-rect-replay-v1"

internal data class ReplayColor(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) {
    fun toWgslVec3(): String = "vec3<f32>(${r.wgsl()}, ${g.wgsl()}, ${b.wgsl()})"
    fun toArgb(): Int {
        val ai = (a.coerceIn(0.0, 1.0) * 255.0).toInt()
        val ri = (r.coerceIn(0.0, 1.0) * 255.0).toInt()
        val gi = (g.coerceIn(0.0, 1.0) * 255.0).toInt()
        val bi = (b.coerceIn(0.0, 1.0) * 255.0).toInt()
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}

internal enum class ReplayBlendMode(val jsonName: String) {
    SrcOver("SrcOver"),
}

internal enum class ReplayBitmapSampler(val jsonName: String) {
    Nearest("nearest"),
    Linear("linear"),
}

internal data class ReplayBitmapFixture(
    val id: String,
    val width: Int,
    val height: Int,
    val colorSpace: String,
    val alphaType: String,
    val provenance: String,
    val pixels: List<ReplayColor>,
) {
    init {
        require(id.isNotBlank()) { "ReplayBitmapFixture id must be non-blank" }
        require(width > 0 && height > 0) { "ReplayBitmapFixture $id must have positive dimensions" }
        require(pixels.size == width * height) { "ReplayBitmapFixture $id pixel count must match dimensions" }
    }

    val pixelChecksum: Long
        get() {
            var checksum = 1469598103934665603L
            pixels.forEach { pixel ->
                checksum = (checksum xor pixel.toArgb().toLong()) * 1099511628211L
            }
            return checksum
        }

    fun sample(srcX: Double, srcY: Double, sampler: ReplayBitmapSampler): ReplayColor =
        when (sampler) {
            ReplayBitmapSampler.Nearest -> {
                val x = floor(srcX).toInt().coerceIn(0, width - 1)
                val y = floor(srcY).toInt().coerceIn(0, height - 1)
                pixel(x, y)
            }
            ReplayBitmapSampler.Linear -> {
                val x = srcX.coerceIn(0.0, (width - 1).toDouble())
                val y = srcY.coerceIn(0.0, (height - 1).toDouble())
                val x0 = floor(x).toInt().coerceIn(0, width - 1)
                val y0 = floor(y).toInt().coerceIn(0, height - 1)
                val x1 = (x0 + 1).coerceAtMost(width - 1)
                val y1 = (y0 + 1).coerceAtMost(height - 1)
                val tx = x - x0.toDouble()
                val ty = y - y0.toDouble()
                pixel(x0, y0).mix(pixel(x1, y0), tx).mix(pixel(x0, y1).mix(pixel(x1, y1), tx), ty)
            }
        }

    private fun pixel(x: Int, y: Int): ReplayColor = pixels[y * width + x]
}

internal object ReplayBitmapFixtures {
    val checker4x4 = ReplayBitmapFixture(
        id = "m79-fixture-checker-rgba8-4x4",
        width = 4,
        height = 4,
        colorSpace = "srgb",
        alphaType = "unpremul",
        provenance = "in-repo deterministic RGBA fixture for M79 nearest bitmap replay",
        pixels = listOf(
            ReplayColor(0.96, 0.68, 0.10), ReplayColor(0.12, 0.30, 0.78), ReplayColor(0.96, 0.68, 0.10), ReplayColor(0.12, 0.30, 0.78),
            ReplayColor(0.12, 0.30, 0.78), ReplayColor(0.92, 0.18, 0.16), ReplayColor(0.18, 0.70, 0.42), ReplayColor(0.96, 0.68, 0.10),
            ReplayColor(0.96, 0.68, 0.10), ReplayColor(0.18, 0.70, 0.42), ReplayColor(0.92, 0.18, 0.16), ReplayColor(0.12, 0.30, 0.78),
            ReplayColor(0.12, 0.30, 0.78), ReplayColor(0.96, 0.68, 0.10), ReplayColor(0.12, 0.30, 0.78), ReplayColor(0.96, 0.68, 0.10),
        ),
    )

    val alphaSwatch4x4 = ReplayBitmapFixture(
        id = "m79-fixture-alpha-swatch-rgba8-4x4",
        width = 4,
        height = 4,
        colorSpace = "srgb",
        alphaType = "unpremul",
        provenance = "in-repo deterministic RGBA fixture for M79 linear bitmap replay with alpha",
        pixels = listOf(
            ReplayColor(0.05, 0.78, 0.82, 0.30), ReplayColor(0.18, 0.55, 0.90, 0.45), ReplayColor(0.58, 0.25, 0.92, 0.58), ReplayColor(0.94, 0.18, 0.44, 0.70),
            ReplayColor(0.08, 0.70, 0.50, 0.38), ReplayColor(0.30, 0.62, 0.84, 0.52), ReplayColor(0.72, 0.30, 0.76, 0.64), ReplayColor(0.96, 0.32, 0.20, 0.76),
            ReplayColor(0.20, 0.64, 0.28, 0.46), ReplayColor(0.48, 0.66, 0.22, 0.58), ReplayColor(0.82, 0.52, 0.16, 0.70), ReplayColor(0.98, 0.62, 0.12, 0.82),
            ReplayColor(0.44, 0.46, 0.18, 0.54), ReplayColor(0.68, 0.58, 0.14, 0.66), ReplayColor(0.88, 0.74, 0.10, 0.78), ReplayColor(1.00, 0.88, 0.08, 0.90),
        ),
    )

    val allById: Map<String, ReplayBitmapFixture> = listOf(checker4x4, alphaSwatch4x4).associateBy { it.id }

    fun requireFixture(id: String): ReplayBitmapFixture =
        requireNotNull(allById[id]) { "Unknown replay bitmap fixture id: $id" }
}

internal enum class ReplayFillKind(val jsonName: String, val commandFamily: String) {
    Solid("solid", "fillRect"),
    LinearGradient("linearGradient", "linearGradientRect"),
    CheckerBitmap("checkerBitmap", "bitmapRectNearest"),
    ColorFilterPlus("colorFilterPlus", "linearGradientColorFilterPlus"),
}

internal enum class ReplayClipOp(val jsonName: String) {
    Intersect("intersect"),
}

internal sealed interface ReplayCommand {
    val label: String
    val family: String
    val supported: Boolean

    data class Clear(val color: ReplayColor) : ReplayCommand {
        override val label: String = "background-clear"
        override val family: String = "backgroundClear"
        override val supported: Boolean = true
    }

    data class FillRect(
        override val label: String,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val color: ReplayColor,
        val endColor: ReplayColor = color,
        val fillKind: ReplayFillKind = ReplayFillKind.Solid,
        val blendMode: ReplayBlendMode = ReplayBlendMode.SrcOver,
        val tintColor: ReplayColor? = null,
        val animated: Boolean = false,
        val dx: Double = 0.0,
    ) : ReplayCommand {
        override val family: String get() = if (animated) "animatedFillRect" else fillKind.commandFamily
        override val supported: Boolean = true

        fun toJson(indent: String): String = buildString {
            appendLine("{")
            appendLine("$indent  \"label\": ${label.json()},")
            appendLine("$indent  \"family\": ${family.json()},")
            appendLine("$indent  \"supported\": true,")
            appendLine("$indent  \"fillKind\": ${fillKind.jsonName.json()},")
            appendLine("$indent  \"blendMode\": ${blendMode.jsonName.json()},")
            appendLine("$indent  \"alpha\": ${color.a.formatJsonNumber()},")
            appendLine("$indent  \"endAlpha\": ${endColor.a.formatJsonNumber()},")
            appendLine("$indent  \"x\": ${x.formatJsonNumber()},")
            appendLine("$indent  \"y\": ${y.formatJsonNumber()},")
            appendLine("$indent  \"width\": ${width.formatJsonNumber()},")
            appendLine("$indent  \"height\": ${height.formatJsonNumber()},")
            appendLine("$indent  \"dx\": ${dx.formatJsonNumber()}")
            append("$indent}")
        }
    }

    data class ClipRect(
        override val label: String,
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val op: ReplayClipOp = ReplayClipOp.Intersect,
    ) : ReplayCommand {
        override val family: String = "clipRect"
        override val supported: Boolean = true

        fun toJson(indent: String): String = buildString {
            appendLine("{")
            appendLine("$indent  \"label\": ${label.json()},")
            appendLine("$indent  \"family\": ${family.json()},")
            appendLine("$indent  \"supported\": true,")
            appendLine("$indent  \"operation\": ${op.jsonName.json()},")
            appendLine("$indent  \"bounds\": {")
            appendLine("$indent    \"x\": ${x.formatJsonNumber()},")
            appendLine("$indent    \"y\": ${y.formatJsonNumber()},")
            appendLine("$indent    \"width\": ${width.formatJsonNumber()},")
            appendLine("$indent    \"height\": ${height.formatJsonNumber()},")
            appendLine("$indent    \"left\": ${x.formatJsonNumber()},")
            appendLine("$indent    \"top\": ${y.formatJsonNumber()},")
            appendLine("$indent    \"right\": ${(x + width).formatJsonNumber()},")
            appendLine("$indent    \"bottom\": ${(y + height).formatJsonNumber()}")
            appendLine("$indent  }")
            append("$indent}")
        }
    }

    data class BitmapRect(
        override val label: String,
        val fixtureId: String,
        val srcX: Double,
        val srcY: Double,
        val srcWidth: Double,
        val srcHeight: Double,
        val dstX: Double,
        val dstY: Double,
        val dstWidth: Double,
        val dstHeight: Double,
        val sampler: ReplayBitmapSampler,
        val alpha: Double = 1.0,
        val blendMode: ReplayBlendMode = ReplayBlendMode.SrcOver,
        val provenance: String,
    ) : ReplayCommand {
        override val family: String = "bitmapRect"
        override val supported: Boolean = true
        val fixture: ReplayBitmapFixture get() = ReplayBitmapFixtures.requireFixture(fixtureId)

        init {
            require(srcWidth > 0.0 && srcHeight > 0.0) { "BitmapRect $label source bounds must be positive" }
            require(dstWidth > 0.0 && dstHeight > 0.0) { "BitmapRect $label destination bounds must be positive" }
            ReplayBitmapFixtures.requireFixture(fixtureId)
        }

        fun toJson(indent: String): String = buildString {
            val bitmapFixture = fixture
            appendLine("{")
            appendLine("$indent  \"label\": ${label.json()},")
            appendLine("$indent  \"family\": ${family.json()},")
            appendLine("$indent  \"supported\": true,")
            appendLine("$indent  \"fixtureId\": ${fixtureId.json()},")
            appendLine("$indent  \"sampler\": ${sampler.jsonName.json()},")
            appendLine("$indent  \"blendMode\": ${blendMode.jsonName.json()},")
            appendLine("$indent  \"alpha\": ${alpha.formatJsonNumber()},")
            appendLine("$indent  \"sourceBounds\": {")
            appendLine("$indent    \"x\": ${srcX.formatJsonNumber()},")
            appendLine("$indent    \"y\": ${srcY.formatJsonNumber()},")
            appendLine("$indent    \"width\": ${srcWidth.formatJsonNumber()},")
            appendLine("$indent    \"height\": ${srcHeight.formatJsonNumber()},")
            appendLine("$indent    \"left\": ${srcX.formatJsonNumber()},")
            appendLine("$indent    \"top\": ${srcY.formatJsonNumber()},")
            appendLine("$indent    \"right\": ${(srcX + srcWidth).formatJsonNumber()},")
            appendLine("$indent    \"bottom\": ${(srcY + srcHeight).formatJsonNumber()}")
            appendLine("$indent  },")
            appendLine("$indent  \"destinationBounds\": {")
            appendLine("$indent    \"x\": ${dstX.formatJsonNumber()},")
            appendLine("$indent    \"y\": ${dstY.formatJsonNumber()},")
            appendLine("$indent    \"width\": ${dstWidth.formatJsonNumber()},")
            appendLine("$indent    \"height\": ${dstHeight.formatJsonNumber()},")
            appendLine("$indent    \"left\": ${dstX.formatJsonNumber()},")
            appendLine("$indent    \"top\": ${dstY.formatJsonNumber()},")
            appendLine("$indent    \"right\": ${(dstX + dstWidth).formatJsonNumber()},")
            appendLine("$indent    \"bottom\": ${(dstY + dstHeight).formatJsonNumber()}")
            appendLine("$indent  },")
            appendLine("$indent  \"fixture\": {")
            appendLine("$indent    \"width\": ${bitmapFixture.width},")
            appendLine("$indent    \"height\": ${bitmapFixture.height},")
            appendLine("$indent    \"colorSpace\": ${bitmapFixture.colorSpace.json()},")
            appendLine("$indent    \"alphaType\": ${bitmapFixture.alphaType.json()},")
            appendLine("$indent    \"pixelChecksum\": ${bitmapFixture.pixelChecksum}")
            appendLine("$indent  },")
            appendLine("$indent  \"provenance\": {")
            appendLine("$indent    \"owner\": \"kanvas\",")
            appendLine("$indent    \"storage\": \"in-repo-kotlin-fixture\",")
            appendLine("$indent    \"fixtureProvenance\": ${bitmapFixture.provenance.json()},")
            appendLine("$indent    \"commandProvenance\": ${provenance.json()}")
            appendLine("$indent  }")
            append("$indent}")
        }
    }

    data class ExpectedUnsupported(
        override val label: String,
        override val family: String,
        val reason: String,
    ) : ReplayCommand {
        override val supported: Boolean = false

        fun toJson(indent: String): String = buildString {
            appendLine("{")
            appendLine("$indent  \"label\": ${label.json()},")
            appendLine("$indent  \"family\": ${family.json()},")
            appendLine("$indent  \"supported\": false,")
            appendLine("$indent  \"reason\": ${reason.json()}")
            append("$indent}")
        }
    }
}

internal data class ReplaySceneEvidence(
    val id: String,
    val title: String,
    val source: String,
    val sourceSceneId: String,
    val version: Int,
    val commandSource: String,
    val commands: List<ReplayCommand>,
    val dashboardRow: String,
    val cpuRoute: String,
    val gpuRoute: String,
    val pipelineKey: String,
) {
    private val clearCommands = commands.filterIsInstance<ReplayCommand.Clear>()

    init {
        require(clearCommands.size == 1) {
            "ReplaySceneEvidence $id must contain exactly one ReplayCommand.Clear"
        }
    }

    val background: ReplayColor get() = clearCommands.single().color
    val rects: List<ReplayCommand.FillRect> get() = commands.filterIsInstance<ReplayCommand.FillRect>()
    val clipRects: List<ReplayCommand.ClipRect> get() = commands.filterIsInstance<ReplayCommand.ClipRect>()
    val bitmapRects: List<ReplayCommand.BitmapRect> get() = commands.filterIsInstance<ReplayCommand.BitmapRect>()
    val unsupportedCommands: List<String> get() = commands.filterIsInstance<ReplayCommand.ExpectedUnsupported>().map { it.reason }
    val totalCommandCount: Int get() = commands.size
    val supportedCommandCount: Int get() = commands.count { it.supported }
    val unsupportedCommandCount: Int get() = commands.count { !it.supported }
    val fillRectCount: Int get() = rects.count { !it.animated }
    val animatedFillRectCount: Int get() = rects.count { it.animated }
    val clipRectCommandCount: Int get() = clipRects.size
    val clipIntersectCommandCount: Int get() = clipRects.count { it.op == ReplayClipOp.Intersect }
    val srcOverCommandCount: Int
        get() = rects.count { it.blendMode == ReplayBlendMode.SrcOver } +
            bitmapRects.count { it.blendMode == ReplayBlendMode.SrcOver }
    val partialAlphaCommandCount: Int
        get() = rects.count { it.color.a < 1.0 || it.endColor.a < 1.0 || (it.tintColor?.a ?: 1.0) < 1.0 } +
            bitmapRects.count { bitmap ->
                bitmap.alpha < 1.0 || bitmap.fixture.pixels.any { it.a < 1.0 }
            }
    val bitmapCommandCount: Int get() = bitmapRects.size
    val fixtureBackedBitmapCommandCount: Int get() = bitmapRects.count { it.fixtureId in ReplayBitmapFixtures.allById }
    val nearestBitmapSamplerCommandCount: Int get() = bitmapRects.count { it.sampler == ReplayBitmapSampler.Nearest }
    val linearBitmapSamplerCommandCount: Int get() = bitmapRects.count { it.sampler == ReplayBitmapSampler.Linear }
    val unsupportedBitmapCommandCount: Int get() = commands.filterIsInstance<ReplayCommand.ExpectedUnsupported>().count { it.family.startsWith("bitmap") }
    val status: String get() = if (unsupportedCommandCount == 0) "renderable" else "expected-unsupported"
    val renderedByKadre: Boolean get() = unsupportedCommandCount == 0

    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"id\": ${id.json()},")
        appendLine("$indent  \"title\": ${title.json()},")
        appendLine("$indent  \"status\": ${status.json()},")
        appendLine("$indent  \"renderedByKadre\": $renderedByKadre,")
        appendLine("$indent  \"source\": ${source.json()},")
        appendLine("$indent  \"sourceSceneId\": ${sourceSceneId.json()},")
        appendLine("$indent  \"version\": $version,")
        appendLine("$indent  \"claimLevel\": \"single-scene-replay-contract\",")
        appendLine("$indent  \"commandSource\": ${commandSource.json()},")
        appendLine("$indent  \"gpuExecution\": \"wgsl-generated-from-kadre-replay-scene\",")
        appendLine("$indent  \"cpuReferenceSource\": \"typed Kadre replay CPU oracle for same selected commands\",")
        appendLine("$indent  \"fallbackPolicy\": \"refuse-unsupported-replay-command\",")
        appendLine("$indent  \"sourceEvidence\": {")
        appendLine("$indent    \"dashboardRow\": ${dashboardRow.json()},")
        appendLine("$indent    \"cpuRoute\": ${cpuRoute.json()},")
        appendLine("$indent    \"gpuRoute\": ${gpuRoute.json()},")
        appendLine("$indent    \"pipelineKey\": ${pipelineKey.json()}")
        appendLine("$indent  },")
        appendLine("$indent  \"commandCounters\": {")
        appendLine("$indent    \"total\": $totalCommandCount,")
        appendLine("$indent    \"supported\": $supportedCommandCount,")
        appendLine("$indent    \"unsupported\": $unsupportedCommandCount,")
        appendLine("$indent    \"backgroundClear\": ${commands.count { it is ReplayCommand.Clear }},")
        appendLine("$indent    \"fillRect\": $fillRectCount,")
        appendLine("$indent    \"animatedFillRect\": $animatedFillRectCount,")
        appendLine("$indent    \"clipRect\": $clipRectCommandCount,")
        appendLine("$indent    \"clipIntersect\": $clipIntersectCommandCount,")
        appendLine("$indent    \"srcOver\": $srcOverCommandCount,")
        appendLine("$indent    \"partialAlpha\": $partialAlphaCommandCount,")
        appendLine("$indent    \"bitmapRect\": $bitmapCommandCount,")
        appendLine("$indent    \"fixtureBackedBitmap\": $fixtureBackedBitmapCommandCount,")
        appendLine("$indent    \"bitmapSamplerNearest\": $nearestBitmapSamplerCommandCount,")
        appendLine("$indent    \"bitmapSamplerLinear\": $linearBitmapSamplerCommandCount,")
        appendLine("$indent    \"unsupportedBitmap\": $unsupportedBitmapCommandCount")
        appendLine("$indent  },")
        appendLine("$indent  \"unsupportedCommands\": [${unsupportedCommands.joinToString(", ") { it.json() }}],")
        appendLine("$indent  \"commands\": [")
        appendLine(commands.joinToString(",\n") { "$indent    ${it.toJson("$indent    ")}" })
        appendLine()
        appendLine("$indent  ]")
        append("$indent}")
    }
}

private val M72_REPLAY_SCENE = ReplaySceneEvidence(
    id = M72_SCENE_CONTRACT_ID,
    title = "M72 solid-rect replay",
    source = "kanvas-replay-data",
    sourceSceneId = "solid-rect",
    version = 1,
    commandSource = "typed-kadre-runtime-replay-contract",
    commands = listOf(
        ReplayCommand.Clear(ReplayColor(0.03, 0.035, 0.045)),
        ReplayCommand.FillRect("solid-rect", 0.20, 0.22, 0.58, 0.50, ReplayColor(0.17, 0.48, 0.90)),
    ),
    dashboardRow = "reports/wgsl-pipeline/scenes/generated/results.json#solid-rect",
    cpuRoute = "cpu.descriptor.coverage-plan.solid-rect",
    gpuRoute = "webgpu.coverage.analytic-rect",
    pipelineKey = "coverageKind=analyticRect",
)

internal val M73_REPLAY_SCENES: List<ReplaySceneEvidence> = listOf(
    M72_REPLAY_SCENE,
    ReplaySceneEvidence(
        id = M73_DEFAULT_SCENE_CONTRACT_ID,
        title = "M73 linear-gradient rect replay",
        source = "kanvas-replay-data",
        sourceSceneId = "linear-gradient-rect",
        version = 1,
        commandSource = "typed-kadre-runtime-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.025, 0.032, 0.045)),
            ReplayCommand.FillRect(
                label = "linear-gradient-rect",
                x = 0.12,
                y = 0.16,
                width = 0.76,
                height = 0.62,
                color = ReplayColor(0.08, 0.28, 0.72),
                endColor = ReplayColor(0.15, 0.82, 0.56),
                fillKind = ReplayFillKind.LinearGradient,
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/scenes/generated/results.json#linear-gradient-rect",
        cpuRoute = "cpu.shader.linear-gradient.rect",
        gpuRoute = "webgpu.generated.linear-gradient.rect",
        pipelineKey = "code=[entryPoint=fs_clamp,generatedPath=true,shaderFamily=linearGradient] state=[blendMode=kSrcOver]",
    ),
    ReplaySceneEvidence(
        id = "m73-bitmap-rect-nearest-replay-v1",
        title = "M73 bitmap-rect nearest replay",
        source = "kanvas-replay-data",
        sourceSceneId = "bitmap-rect-nearest",
        version = 1,
        commandSource = "typed-kadre-runtime-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.04, 0.04, 0.05)),
            ReplayCommand.FillRect(
                label = "bitmap-rect-nearest-checker",
                x = 0.16,
                y = 0.18,
                width = 0.68,
                height = 0.58,
                color = ReplayColor(0.95, 0.72, 0.18),
                endColor = ReplayColor(0.10, 0.30, 0.78),
                fillKind = ReplayFillKind.CheckerBitmap,
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/scenes/generated/results.json#bitmap-rect-nearest",
        cpuRoute = "cpu.image-rect.strict-nearest",
        gpuRoute = "webgpu.image-rect.strict-nearest",
        pipelineKey = "imageRect.strictNearest.promotedSmoke",
    ),
    ReplaySceneEvidence(
        id = "m73-gradient-color-filter-kplus-replay-v1",
        title = "M73 gradient color-filter kPlus replay",
        source = "kanvas-replay-data",
        sourceSceneId = "gradient-color-filter-linear-kplus",
        version = 1,
        commandSource = "typed-kadre-runtime-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.035, 0.035, 0.045)),
            ReplayCommand.FillRect(
                label = "linear-gradient-plus-red-filter",
                x = 0.12,
                y = 0.18,
                width = 0.74,
                height = 0.56,
                color = ReplayColor(0.05, 0.55, 0.20),
                endColor = ReplayColor(0.24, 0.90, 0.38),
                fillKind = ReplayFillKind.ColorFilterPlus,
                tintColor = ReplayColor(0.55, 0.12, 0.02),
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/scenes/generated/results.json#gradient-color-filter-linear-kplus",
        cpuRoute = "cpu.shader.linear-gradient.color-filter.blend-kplus-oracle",
        gpuRoute = "webgpu.generated.linear-gradient.color-filter.blend-kplus",
        pipelineKey = "shaderFamily=linearGradient colorFilter=Blend(red,kPlus) coverage=analyticRect state=[blendMode=kSrcOver]",
    ),
    ReplaySceneEvidence(
        id = "m73-nested-rrect-clip-refusal-v1",
        title = "M73 nested rrect clip refusal",
        source = "kanvas-replay-data",
        sourceSceneId = "m60-bounded-nested-rrect-clip",
        version = 1,
        commandSource = "typed-kadre-runtime-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.04, 0.04, 0.05)),
            ReplayCommand.ExpectedUnsupported(
                label = "nested-rrect-difference-clip",
                family = "clip",
                reason = "nested-rrect-difference-clip",
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json#m60-bounded-nested-rrect-clip",
        cpuRoute = "cpu.coverage.nested-rrect-clip-oracle",
        gpuRoute = "webgpu.coverage.nested-rrect-clip.expected-unsupported",
        pipelineKey = "clipDepth=3 clip=rect+rect+rrectOval op=intersect+intersect+difference budget=m60 source=BlurredClippedCircleGM status=expected-unsupported",
    ),
)

internal val M73_REPLAY_SCENES_BY_ID: Map<String, ReplaySceneEvidence> = M73_REPLAY_SCENES.associateBy { it.id }

internal fun replayScenesById(): Map<String, ReplaySceneEvidence> =
    (M73_REPLAY_SCENES + M77_BLEND_ALPHA_REPLAY_SCENES + M78_CLIP_REPLAY_SCENES + M79_BITMAP_REPLAY_SCENES).associateBy { it.id }

internal fun replayPackJson(mode: String, indent: String): String {
    if (mode != "demo") return "null"
    val renderable = M73_REPLAY_SCENES.count { it.renderedByKadre }
    val unsupported = M73_REPLAY_SCENES.count { !it.renderedByKadre }
    return buildString {
        appendLine("{")
        appendLine("$indent  \"id\": \"m73-kadre-replay-pack-v1\",")
        appendLine("$indent  \"claimLevel\": \"bounded-replay-pack-contracts\",")
        appendLine("$indent  \"sceneCount\": ${M73_REPLAY_SCENES.size},")
        appendLine("$indent  \"renderableSceneCount\": $renderable,")
        appendLine("$indent  \"unsupportedSceneCount\": $unsupported,")
        appendLine("$indent  \"source\": \"kanvas-replay-data\",")
        appendLine("$indent  \"sceneIds\": [${M73_REPLAY_SCENES.joinToString(", ") { it.id.json() }}],")
        appendLine("$indent  \"sourceSceneIds\": [${M73_REPLAY_SCENES.joinToString(", ") { it.sourceSceneId.json() }}],")
        appendLine("$indent  \"unsupportedSceneIds\": [${M73_REPLAY_SCENES.filterNot { it.renderedByKadre }.joinToString(", ") { it.id.json() }}],")
        appendLine("$indent  \"scenes\": [")
        appendLine(M73_REPLAY_SCENES.joinToString(",\n") { "$indent    ${it.toJson("$indent    ")}" })
        appendLine()
        appendLine("$indent  ]")
        append("$indent}")
    }
}

internal fun renderCpuReference(width: Int, height: Int, replayScene: ReplaySceneEvidence? = null): Pair<Long, Int> {
    val bitmap = SkBitmap(width, height)
    val background = replayScene?.background ?: ReplayColor(0.04, 0.05, 0.07)
    bitmap.eraseColor(background.toArgb())
    if (replayScene != null) {
        var activeClip = ReplayBounds(0.0, 0.0, 1.0, 1.0)
        replayScene.commands.forEach { command ->
            if (command is ReplayCommand.ClipRect) {
                activeClip = activeClip.intersect(command.bounds)
            }
            when (command) {
                is ReplayCommand.FillRect -> {
                    val rect = command
                    val drawBounds = rect.bounds.intersect(activeClip)
                    if (drawBounds.isEmpty) return@forEach
                    val left = (rect.x * width).toInt().coerceIn(0, width)
                    val top = (rect.y * height).toInt().coerceIn(0, height)
                    val right = ((rect.x + rect.width) * width).toInt().coerceIn(0, width)
                    val bottom = ((rect.y + rect.height) * height).toInt().coerceIn(0, height)
                    val clippedLeft = (drawBounds.left * width).toInt().coerceIn(left, right)
                    val clippedTop = (drawBounds.top * height).toInt().coerceIn(top, bottom)
                    val clippedRight = (drawBounds.right * width).toInt().coerceIn(left, right)
                    val clippedBottom = (drawBounds.bottom * height).toInt().coerceIn(top, bottom)
                    for (py in clippedTop until clippedBottom) {
                        for (px in clippedLeft until clippedRight) {
                            val u = (((px.toDouble() + 0.5) / width.toDouble()) - rect.x) / rect.width.coerceAtLeast(0.0001)
                            val v = (((py.toDouble() + 0.5) / height.toDouble()) - rect.y) / rect.height.coerceAtLeast(0.0001)
                            val src = rect.fillColorAt(u, v, phase = 0.0)
                            val dst = bitmap.getPixel(px, py).toReplayColor()
                            bitmap.setPixel(px, py, src.blendOver(dst).toArgb())
                        }
                    }
                }
                is ReplayCommand.BitmapRect -> {
                    val rect = command
                    val drawBounds = rect.bounds.intersect(activeClip)
                    if (drawBounds.isEmpty) return@forEach
                    val left = (rect.dstX * width).toInt().coerceIn(0, width)
                    val top = (rect.dstY * height).toInt().coerceIn(0, height)
                    val right = ((rect.dstX + rect.dstWidth) * width).toInt().coerceIn(0, width)
                    val bottom = ((rect.dstY + rect.dstHeight) * height).toInt().coerceIn(0, height)
                    val clippedLeft = (drawBounds.left * width).toInt().coerceIn(left, right)
                    val clippedTop = (drawBounds.top * height).toInt().coerceIn(top, bottom)
                    val clippedRight = (drawBounds.right * width).toInt().coerceIn(left, right)
                    val clippedBottom = (drawBounds.bottom * height).toInt().coerceIn(top, bottom)
                    val fixture = rect.fixture
                    for (py in clippedTop until clippedBottom) {
                        for (px in clippedLeft until clippedRight) {
                            val u = (((px.toDouble() + 0.5) / width.toDouble()) - rect.dstX) / rect.dstWidth.coerceAtLeast(0.0001)
                            val v = (((py.toDouble() + 0.5) / height.toDouble()) - rect.dstY) / rect.dstHeight.coerceAtLeast(0.0001)
                            val srcX = rect.srcX + u.coerceIn(0.0, 1.0) * rect.srcWidth
                            val srcY = rect.srcY + v.coerceIn(0.0, 1.0) * rect.srcHeight
                            val sampled = fixture.sample(srcX, srcY, rect.sampler).withAlphaMultiplier(rect.alpha)
                            val dst = bitmap.getPixel(px, py).toReplayColor()
                            bitmap.setPixel(px, py, sampled.blendOver(dst).toArgb())
                        }
                    }
                }
                else -> Unit
            }
        }
    } else {
        val canvas = SkCanvas(bitmap)
        val rects = listOf(
            ReplayCommand.FillRect("blue-panel", 0.06, 0.10, 0.62, 0.32, ReplayColor(0.17, 0.48, 0.90)),
            ReplayCommand.FillRect("green-panel", 0.38, 0.35, 0.46, 0.42, ReplayColor(0.22, 0.69, 0.00)),
            ReplayCommand.FillRect("red-panel", 0.12, 0.62, 0.32, 0.22, ReplayColor(0.91, 0.29, 0.37)),
        )
        rects.forEach { rect ->
            val paint = SkPaint().apply { color = rect.color.toArgb(); isAntiAlias = true }
            canvas.drawRect(
                SkRect.MakeXYWH(
                    (rect.x * width).toFloat(),
                    (rect.y * height).toFloat(),
                    (rect.width * width).toFloat(),
                    (rect.height * height).toFloat(),
                ),
                paint,
            )
        }
    }
    var checksum = 1469598103934665603L
    var nonTransparent = 0
    for (y in 0 until height step 7) {
        for (x in 0 until width step 7) {
            val argb = bitmap.getPixel(x, y)
            if ((argb ushr 24) != 0) nonTransparent++
            checksum = (checksum xor argb.toLong()) * 1099511628211L
        }
    }
    return checksum to nonTransparent
}

internal fun bitmapSampledPixels(width: Int, height: Int, replayScene: ReplaySceneEvidence): Int {
    var activeClip = ReplayBounds(0.0, 0.0, 1.0, 1.0)
    var sampled = 0
    replayScene.commands.forEach { command ->
        if (command is ReplayCommand.ClipRect) {
            activeClip = activeClip.intersect(command.bounds)
        }
        val bitmap = command as? ReplayCommand.BitmapRect ?: return@forEach
        val drawBounds = bitmap.bounds.intersect(activeClip)
        if (drawBounds.isEmpty) return@forEach
        val left = (drawBounds.left * width).toInt().coerceIn(0, width)
        val top = (drawBounds.top * height).toInt().coerceIn(0, height)
        val right = (drawBounds.right * width).toInt().coerceIn(left, width)
        val bottom = (drawBounds.bottom * height).toInt().coerceIn(top, height)
        sampled += (right - left) * (bottom - top)
    }
    return sampled
}

private data class ReplayBounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun intersect(other: ReplayBounds): ReplayBounds =
        ReplayBounds(
            left = maxOf(left, other.left),
            top = maxOf(top, other.top),
            right = minOf(right, other.right),
            bottom = minOf(bottom, other.bottom),
        )
}

private val ReplayCommand.FillRect.bounds: ReplayBounds
    get() = ReplayBounds(x, y, x + width, y + height)

private val ReplayCommand.ClipRect.bounds: ReplayBounds
    get() = ReplayBounds(x, y, x + width, y + height)

private val ReplayCommand.BitmapRect.bounds: ReplayBounds
    get() = ReplayBounds(dstX, dstY, dstX + dstWidth, dstY + dstHeight)

private fun ReplayCommand.FillRect.fillColorAt(u: Double, v: Double, phase: Double): ReplayColor =
    when (fillKind) {
        ReplayFillKind.Solid -> color
        ReplayFillKind.LinearGradient -> color.mix(endColor, u.coerceIn(0.0, 1.0))
        ReplayFillKind.CheckerBitmap -> {
            val checker = ((floor(u.coerceIn(0.0, 1.0) * 12.0) + floor(v.coerceIn(0.0, 1.0) * 8.0)).toInt() and 1) == 0
            if (checker) color else endColor
        }
        ReplayFillKind.ColorFilterPlus -> color.mix(endColor, u.coerceIn(0.0, 1.0)).plus(tintColor ?: ReplayColor(0.0, 0.0, 0.0))
    }

private fun ReplayColor.mix(other: ReplayColor, t: Double): ReplayColor {
    val clamped = t.coerceIn(0.0, 1.0)
    return ReplayColor(
        r = r + (other.r - r) * clamped,
        g = g + (other.g - g) * clamped,
        b = b + (other.b - b) * clamped,
        a = a + (other.a - a) * clamped,
    )
}

private fun ReplayColor.plus(other: ReplayColor): ReplayColor =
    ReplayColor(
        r = (r + other.r).coerceIn(0.0, 1.0),
        g = (g + other.g).coerceIn(0.0, 1.0),
        b = (b + other.b).coerceIn(0.0, 1.0),
        a = a,
    )

private fun ReplayColor.withAlphaMultiplier(alphaMultiplier: Double): ReplayColor =
    ReplayColor(r, g, b, a * alphaMultiplier.coerceIn(0.0, 1.0))

private fun Int.toReplayColor(): ReplayColor =
    ReplayColor(
        r = ((this ushr 16) and 0xFF).toDouble() / 255.0,
        g = ((this ushr 8) and 0xFF).toDouble() / 255.0,
        b = (this and 0xFF).toDouble() / 255.0,
        a = ((this ushr 24) and 0xFF).toDouble() / 255.0,
    )

private fun ReplayColor.blendOver(dst: ReplayColor): ReplayColor {
    val sa = a.coerceIn(0.0, 1.0)
    val da = dst.a.coerceIn(0.0, 1.0)
    val outA = sa + da * (1.0 - sa)
    if (outA <= 0.0) return ReplayColor(0.0, 0.0, 0.0, 0.0)
    return ReplayColor(
        r = ((r.coerceIn(0.0, 1.0) * sa) + (dst.r.coerceIn(0.0, 1.0) * da * (1.0 - sa))) / outA,
        g = ((g.coerceIn(0.0, 1.0) * sa) + (dst.g.coerceIn(0.0, 1.0) * da * (1.0 - sa))) / outA,
        b = ((b.coerceIn(0.0, 1.0) * sa) + (dst.b.coerceIn(0.0, 1.0) * da * (1.0 - sa))) / outA,
        a = outA,
    )
}

internal fun ReplaySceneEvidence.toWgsl(phase: Double): String {
    var activeClip = ReplayBounds(0.0, 0.0, 1.0, 1.0)
    var rectIndex = 0
    val rectTests = commands.mapNotNull { command ->
        if (command is ReplayCommand.ClipRect) {
            activeClip = activeClip.intersect(command.bounds)
            return@mapNotNull null
        }
        val bitmap = command as? ReplayCommand.BitmapRect
        if (bitmap != null) {
            val index = rectIndex++
            val x = bitmap.dstX.wgsl()
            val y = bitmap.dstY.wgsl()
            val width = bitmap.dstWidth.wgsl()
            val height = bitmap.dstHeight.wgsl()
            val u = "clamp((in.uv.x - $x) / $width, 0.0, 1.0)"
            val v = "clamp((in.uv.y - $y) / $height, 0.0, 1.0)"
            val fixture = bitmap.fixture
            val pixels = fixture.pixels.joinToString(", ") {
                "vec4<f32>(${it.r.wgsl()}, ${it.g.wgsl()}, ${it.b.wgsl()}, ${it.a.wgsl()})"
            }
            val sampleX = "clamp(${bitmap.srcX.wgsl()} + $u * ${bitmap.srcWidth.wgsl()}, 0.0, ${(fixture.width - 1).toDouble().wgsl()})"
            val sampleY = "clamp(${bitmap.srcY.wgsl()} + $v * ${bitmap.srcHeight.wgsl()}, 0.0, ${(fixture.height - 1).toDouble().wgsl()})"
            val sample = when (bitmap.sampler) {
                ReplayBitmapSampler.Nearest -> """
        let bitmapPixels$index = array<vec4<f32>, ${fixture.pixels.size}>($pixels);
        let sampleX$index = u32(floor($sampleX));
        let sampleY$index = u32(floor($sampleY));
        let bitmapColor$index = bitmapPixels$index[sampleY$index * ${fixture.width}u + sampleX$index];
                """.trimIndent()
                ReplayBitmapSampler.Linear -> """
        let bitmapPixels$index = array<vec4<f32>, ${fixture.pixels.size}>($pixels);
        let sampleXFloat$index = $sampleX;
        let sampleYFloat$index = $sampleY;
        let sampleX0$index = floor(sampleXFloat$index);
        let sampleY0$index = floor(sampleYFloat$index);
        let sampleTx$index = sampleXFloat$index - sampleX0$index;
        let sampleTy$index = sampleYFloat$index - sampleY0$index;
        let sampleX0u$index = u32(sampleX0$index);
        let sampleY0u$index = u32(sampleY0$index);
        let sampleX1u$index = u32(min(sampleX0$index + 1.0, ${(fixture.width - 1).toDouble().wgsl()}));
        let sampleY1u$index = u32(min(sampleY0$index + 1.0, ${(fixture.height - 1).toDouble().wgsl()}));
        let bitmapC00$index = bitmapPixels$index[sampleY0u$index * ${fixture.width}u + sampleX0u$index];
        let bitmapC10$index = bitmapPixels$index[sampleY0u$index * ${fixture.width}u + sampleX1u$index];
        let bitmapC01$index = bitmapPixels$index[sampleY1u$index * ${fixture.width}u + sampleX0u$index];
        let bitmapC11$index = bitmapPixels$index[sampleY1u$index * ${fixture.width}u + sampleX1u$index];
        let bitmapColor$index = mix(mix(bitmapC00$index, bitmapC10$index, sampleTx$index), mix(bitmapC01$index, bitmapC11$index, sampleTx$index), sampleTy$index);
                """.trimIndent()
            }
            return@mapNotNull """
        let rect$index = select(0.0, 1.0, in.uv.x >= $x && in.uv.x < ($x + $width) && in.uv.y >= $y && in.uv.y < ($y + $height) && in.uv.x >= ${activeClip.left.wgsl()} && in.uv.x < ${activeClip.right.wgsl()} && in.uv.y >= ${activeClip.top.wgsl()} && in.uv.y < ${activeClip.bottom.wgsl()});
        $sample
        color = mix(color, bitmapColor$index.rgb, rect$index * bitmapColor$index.a * ${bitmap.alpha.wgsl()});
            """.trimIndent()
        }
        val rect = command as? ReplayCommand.FillRect ?: return@mapNotNull null
        val index = rectIndex++
        val x = if (rect.animated) "${rect.x.wgsl()} + ${rect.dx.wgsl()} * phase" else rect.x.wgsl()
        val y = rect.y.wgsl()
        val width = rect.width.wgsl()
        val height = rect.height.wgsl()
        val u = "clamp((in.uv.x - $x) / $width, 0.0, 1.0)"
        val v = "clamp((in.uv.y - $y) / $height, 0.0, 1.0)"
        val fill = when (rect.fillKind) {
            ReplayFillKind.Solid -> rect.color.toWgslVec3()
            ReplayFillKind.LinearGradient -> "mix(${rect.color.toWgslVec3()}, ${rect.endColor.toWgslVec3()}, $u)"
            ReplayFillKind.CheckerBitmap -> {
                "select(${rect.endColor.toWgslVec3()}, ${rect.color.toWgslVec3()}, (i32(floor($u * 12.0)) + i32(floor($v * 8.0))) % 2 == 0)"
            }
            ReplayFillKind.ColorFilterPlus -> {
                "min(mix(${rect.color.toWgslVec3()}, ${rect.endColor.toWgslVec3()}, $u) + ${(rect.tintColor ?: ReplayColor(0.0, 0.0, 0.0)).toWgslVec3()}, vec3<f32>(1.0))"
            }
        }
        val alpha = when (rect.fillKind) {
            ReplayFillKind.Solid -> rect.color.a.wgsl()
            ReplayFillKind.LinearGradient,
            ReplayFillKind.ColorFilterPlus -> "mix(${rect.color.a.wgsl()}, ${rect.endColor.a.wgsl()}, $u)"
            ReplayFillKind.CheckerBitmap -> {
                "select(${rect.endColor.a.wgsl()}, ${rect.color.a.wgsl()}, (i32(floor($u * 12.0)) + i32(floor($v * 8.0))) % 2 == 0)"
            }
        }
        """
        let rect$index = select(0.0, 1.0, in.uv.x >= $x && in.uv.x < ($x + $width) && in.uv.y >= $y && in.uv.y < ($y + $height) && in.uv.x >= ${activeClip.left.wgsl()} && in.uv.x < ${activeClip.right.wgsl()} && in.uv.y >= ${activeClip.top.wgsl()} && in.uv.y < ${activeClip.bottom.wgsl()});
        color = mix(color, $fill, rect$index * $alpha);
        """.trimIndent()
    }.joinToString("\n    ")

    return """
struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
};

@vertex
fn vs_main(@builtin(vertex_index) vertexIndex: u32) -> VertexOutput {
    var positions = array<vec2<f32>, 6>(
        vec2<f32>(-1.0, -1.0),
        vec2<f32>( 1.0, -1.0),
        vec2<f32>(-1.0,  1.0),
        vec2<f32>(-1.0,  1.0),
        vec2<f32>( 1.0, -1.0),
        vec2<f32>( 1.0,  1.0),
    );
    let p = positions[vertexIndex];
    var out: VertexOutput;
    out.position = vec4<f32>(p, 0.0, 1.0);
    out.uv = p * 0.5 + vec2<f32>(0.5, 0.5);
    return out;
}

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    let phase: f32 = ${phase.wgsl()};
    var color = ${background.toWgslVec3()};
    $rectTests
    return vec4<f32>(color, 1.0);
}
""".trimIndent()
}

private fun ReplayCommand.toJson(indent: String): String =
    when (this) {
        is ReplayCommand.Clear -> buildString {
            appendLine("{")
            appendLine("$indent  \"label\": ${label.json()},")
            appendLine("$indent  \"family\": ${family.json()},")
            appendLine("$indent  \"supported\": true,")
            appendLine("$indent  \"color\": {")
            appendLine("$indent    \"r\": ${color.r.formatJsonNumber()},")
            appendLine("$indent    \"g\": ${color.g.formatJsonNumber()},")
            appendLine("$indent    \"b\": ${color.b.formatJsonNumber()},")
            appendLine("$indent    \"a\": ${color.a.formatJsonNumber()}")
            appendLine("$indent  }")
            append("$indent}")
        }
        is ReplayCommand.FillRect -> toJson(indent)
        is ReplayCommand.ClipRect -> toJson(indent)
        is ReplayCommand.BitmapRect -> toJson(indent)
        is ReplayCommand.ExpectedUnsupported -> toJson(indent)
    }

private fun String.json(): String =
    buildString {
        append('"')
        for (ch in this@json) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

private fun Double.formatJsonNumber(): String = "%.4f".format(java.util.Locale.US, this)

private fun Double.wgsl(): String = "%.6ff".format(java.util.Locale.US, this)

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

internal enum class ReplayFillKind(val jsonName: String, val commandFamily: String) {
    Solid("solid", "fillRect"),
    LinearGradient("linearGradient", "linearGradientRect"),
    CheckerBitmap("checkerBitmap", "bitmapRectNearest"),
    ColorFilterPlus("colorFilterPlus", "linearGradientColorFilterPlus"),
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

    data class ExpectedUnsupported(
        override val label: String,
        override val family: String,
        val reason: String,
    ) : ReplayCommand {
        override val supported: Boolean = false
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
    val unsupportedCommands: List<String> get() = commands.filterIsInstance<ReplayCommand.ExpectedUnsupported>().map { it.reason }
    val totalCommandCount: Int get() = commands.size
    val supportedCommandCount: Int get() = commands.count { it.supported }
    val unsupportedCommandCount: Int get() = commands.count { !it.supported }
    val fillRectCount: Int get() = rects.count { !it.animated }
    val animatedFillRectCount: Int get() = rects.count { it.animated }
    val srcOverCommandCount: Int get() = rects.count { it.blendMode == ReplayBlendMode.SrcOver }
    val partialAlphaCommandCount: Int get() = rects.count { it.color.a < 1.0 || it.endColor.a < 1.0 || (it.tintColor?.a ?: 1.0) < 1.0 }
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
        appendLine("$indent    \"srcOver\": $srcOverCommandCount,")
        appendLine("$indent    \"partialAlpha\": $partialAlphaCommandCount")
        appendLine("$indent  },")
        appendLine("$indent  \"unsupportedCommands\": [${unsupportedCommands.joinToString(", ") { it.json() }}],")
        appendLine("$indent  \"commands\": [")
        appendLine(rects.joinToString(",\n") { "$indent    ${it.toJson("$indent    ")}" })
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
        replayScene.rects.forEach { rect ->
            val left = (rect.x * width).toInt().coerceIn(0, width)
            val top = (rect.y * height).toInt().coerceIn(0, height)
            val right = ((rect.x + rect.width) * width).toInt().coerceIn(0, width)
            val bottom = ((rect.y + rect.height) * height).toInt().coerceIn(0, height)
            for (py in top until bottom) {
                for (px in left until right) {
                    val u = ((px.toDouble() / width.toDouble()) - rect.x) / rect.width.coerceAtLeast(0.0001)
                    val v = ((py.toDouble() / height.toDouble()) - rect.y) / rect.height.coerceAtLeast(0.0001)
                    val src = rect.fillColorAt(u, v, phase = 0.0)
                    val dst = bitmap.getPixel(px, py).toReplayColor()
                    bitmap.setPixel(px, py, src.blendOver(dst).toArgb())
                }
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
    val rectTests = rects.mapIndexed { index, rect ->
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
        let rect$index = select(0.0, 1.0, in.uv.x >= $x && in.uv.x <= ($x + $width) && in.uv.y >= $y && in.uv.y <= ($y + $height));
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

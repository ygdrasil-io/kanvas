package org.graphiks.kanvas.gpu.renderer.wgsl

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import kotlin.math.pow

data class GradientWgslShader(
    val wgslSource: String,
    val uniformLayoutHash: String,
)

object GradientWgslShaderProvider {

    private const val MAX_STOPS = 16

    fun shaderFor(descriptor: GPUMaterialDescriptor): GradientWgslShader? {
        return when (descriptor) {
            is GPUMaterialDescriptor.LinearGradient -> linearShader(descriptor)
            is GPUMaterialDescriptor.RadialGradient -> radialShader(descriptor)
            is GPUMaterialDescriptor.SweepGradient -> sweepShader(descriptor)
            is GPUMaterialDescriptor.ConicalGradient -> conicalShader(descriptor)
            else -> null
        }
    }

    fun uniformBytesFor(descriptor: GPUMaterialDescriptor): ByteArray? {
        return when (descriptor) {
            is GPUMaterialDescriptor.LinearGradient -> linearUniformBytes(descriptor)
            is GPUMaterialDescriptor.RadialGradient -> radialUniformBytes(descriptor)
            is GPUMaterialDescriptor.SweepGradient -> sweepUniformBytes(descriptor)
            is GPUMaterialDescriptor.ConicalGradient -> conicalUniformBytes(descriptor)
            else -> null
        }
    }

    fun uniformLayoutHashFor(descriptor: GPUMaterialDescriptor): String? {
        return when (descriptor) {
            is GPUMaterialDescriptor.LinearGradient -> "layout:linear-gradient-material-block:v1"
            is GPUMaterialDescriptor.RadialGradient -> "layout:radial-gradient-material-block:v1"
            is GPUMaterialDescriptor.SweepGradient -> "layout:sweep-gradient-material-block:v1"
            is GPUMaterialDescriptor.ConicalGradient -> "layout:conical-gradient-material-block:v1"
            else -> null
        }
    }

    fun canHandle(descriptor: GPUMaterialDescriptor): Boolean {
        val stopCount = when (descriptor) {
            is GPUMaterialDescriptor.LinearGradient -> descriptor.allStopPositions?.size ?: 2
            is GPUMaterialDescriptor.RadialGradient -> descriptor.allStopPositions?.size ?: 2
            is GPUMaterialDescriptor.SweepGradient -> descriptor.allStopPositions?.size ?: 2
            is GPUMaterialDescriptor.ConicalGradient -> descriptor.allStopPositions?.size ?: 2
            else -> return false
        }
        return stopCount <= MAX_STOPS
    }

    private fun linearShader(desc: GPUMaterialDescriptor.LinearGradient): GradientWgslShader {
        val n = desc.allStopPositions?.size ?: 2
        return GradientWgslShader(
            wgslSource = buildLinearWgsl(n, desc.tileMode),
            uniformLayoutHash = "layout:linear-gradient-material-block:v1",
        )
    }

    private fun radialShader(desc: GPUMaterialDescriptor.RadialGradient): GradientWgslShader {
        val n = desc.allStopPositions?.size ?: 2
        return GradientWgslShader(
            wgslSource = buildRadialWgsl(n, desc.tileMode),
            uniformLayoutHash = "layout:radial-gradient-material-block:v1",
        )
    }

    private fun sweepShader(desc: GPUMaterialDescriptor.SweepGradient): GradientWgslShader {
        val n = desc.allStopPositions?.size ?: 2
        return GradientWgslShader(
            wgslSource = buildSweepWgsl(n, desc.tileMode),
            uniformLayoutHash = "layout:sweep-gradient-material-block:v1",
        )
    }

    private fun buildLinearWgsl(stopCount: Int, tileMode: String): String {
        val (tileFn, decalSuffix) = tileFnForMode(tileMode)
        return buildGradientWgsl(
            preamble = """
                let dir = gradient.end - gradient.start;
                let lenSq = dot(dir, dir);
                var t_raw: f32;
                if (lenSq < 1.0e-12) {
                    t_raw = -1.0e30;
                } else {
                    t_raw = dot(pos.xy - gradient.start, dir) / lenSq;
                }
            """.trimIndent(),
            stopCount = stopCount,
            headerExtra = "",
            structFields = """
                    start: vec2<f32>,
                    end: vec2<f32>,
            """.trimIndent(),
            tileFn = tileFn,
            decalSuffix = decalSuffix,
        )
    }

    private fun buildRadialWgsl(stopCount: Int, tileMode: String): String {
        val (tileFn, decalSuffix) = tileFnForMode(tileMode)
        return buildGradientWgsl(
            preamble = """
                let d = pos.xy - gradient.center;
                var t_raw: f32;
                if (gradient.radius <= 0.0) {
                    t_raw = -1.0e30;
                } else {
                    t_raw = length(d) / gradient.radius;
                }
            """.trimIndent(),
            stopCount = stopCount,
            headerExtra = "radius: f32,",
            structFields = """
                    center: vec2<f32>,
                    radius: f32,
            """.trimIndent(),
            tileFn = tileFn,
            decalSuffix = decalSuffix,
        )
    }

    private fun buildSweepWgsl(stopCount: Int, tileMode: String): String {
        val (tileFn, decalSuffix) = tileFnForMode(tileMode)
        return buildGradientWgsl(
            preamble = """
                const TWO_PI: f32 = 6.2831853071795864;
                let d = pos.xy - gradient.center;
                var t_raw: f32;
                if (d.x == 0.0 && d.y == 0.0) {
                    t_raw = 0.0;
                } else {
                    let a = atan2(d.y, d.x);
                    var u = a / TWO_PI;
                    if (u < 0.0) { u = u + 1.0; }
                    let sweep = gradient.endAngle - gradient.startAngle;
                    if (sweep <= 0.0) {
                        t_raw = 0.0;
                    } else {
                        t_raw = (u - gradient.startAngle / 360.0) * (360.0 / sweep);
                    }
                }
            """.trimIndent(),
            stopCount = stopCount,
            headerExtra = "startAngle: f32, endAngle: f32,",
            structFields = """
                    center: vec2<f32>,
                    startAngle: f32,
                    endAngle: f32,
            """.trimIndent(),
            tileFn = tileFn,
            decalSuffix = decalSuffix,
        )
    }

    private fun conicalShader(desc: GPUMaterialDescriptor.ConicalGradient): GradientWgslShader {
        val n = desc.allStopPositions?.size ?: 2
        val (tileFn, decalSuffix) = tileFnForMode(desc.tileMode)
        return GradientWgslShader(
            wgslSource = buildConicalWgsl(
                stopCount = n,
                tileFn = tileFn,
                decalSuffix = decalSuffix,
            ),
            uniformLayoutHash = "layout:conical-gradient-material-block:v1",
        )
    }

    private fun buildConicalWgsl(stopCount: Int, tileFn: String, decalSuffix: String): String = """
struct GradientBlock {
    start: vec2<f32>,
    end: vec2<f32>,
    r1: f32,
    r2: f32,
    count: u32,
    _pad0: u32,
    _pad1: u32,
    _pad2: u32,
    _pad3: u32,
    _pad4: u32,
    stopData: array<vec4<f32>, 32>,
}
@group(0) @binding(0) var<uniform> gradient: GradientBlock;

struct VertexOutput {
    @builtin(position) pos: vec4<f32>,
}

@vertex fn vs_main(@builtin(vertex_index) vi: u32) -> VertexOutput {
    let verts = array<vec2<f32>, 3>(
        vec2<f32>(-1.0, -1.0),
        vec2<f32>(3.0, -1.0),
        vec2<f32>(-1.0, 3.0),
    );
    return VertexOutput(vec4<f32>(verts[vi], 0.0, 1.0));
}

@fragment fn fs_main(@builtin(position) pos: vec4<f32>) -> @location(0) vec4<f32> {
    let dx = gradient.end.x - gradient.start.x;
    let dy = gradient.end.y - gradient.start.y;
    let fx = pos.x - gradient.start.x;
    let fy = pos.y - gradient.start.y;
    let A = dx*dx + dy*dy - (gradient.r2 - gradient.r1)*(gradient.r2 - gradient.r1);
    let B = 2.0 * (dx*fx + dy*fy + gradient.r1*(gradient.r2 - gradient.r1));
    let C = fx*fx + fy*fy - gradient.r1*gradient.r1;
    var t_raw: f32 = 0.0;
    if (abs(A) < 1.0e-12) {
        if (abs(B) >= 1.0e-12) {
            t_raw = -C / B;
        }
    } else {
        let disc = B*B - 4.0*A*C;
        if (disc >= 0.0) {
            t_raw = (-B + sqrt(disc)) / (2.0 * A);
        }
    }
    let t = $tileFn;
    var positions: array<vec4<f32>, 16>;
    var colors: array<vec4<f32>, 16>;
    for (var i: u32 = 0u; i < ${stopCount}u; i = i + 1u) {
        positions[i] = gradient.stopData[i * 2u];
        colors[i] = gradient.stopData[i * 2u + 1u];
    }
    let result = sample_stops_at(t, ${stopCount}u, &positions, &colors)${decalSuffix};
    return result;
}

fn sample_stops_at(t: f32, count: u32, positions: ptr<function, array<vec4<f32>, 16>>, colors: ptr<function, array<vec4<f32>, 16>>) -> vec4<f32> {
    if (count <= 1u) { return (*colors)[0]; }
    if (t <= (*positions)[0].x) { return (*colors)[0]; }
    let lastIdx = count - 1u;
    if (t >= (*positions)[lastIdx].x) { return (*colors)[lastIdx]; }
    var lo: u32 = 0u;
    for (var i: u32 = 1u; i < count; i = i + 1u) {
        if ((*positions)[i].x >= t) { lo = i - 1u; break; }
    }
    let hi = lo + 1u;
    let t0 = (*positions)[lo].x;
    let t1 = (*positions)[hi].x;
    let span = t1 - t0;
    let u = select((t - t0) / span, 0.0, span <= 0.0);
    let c_lo_srgb = vec4f(pow((*colors)[lo].rgb, vec3f(1.0 / 2.2)), (*colors)[lo].a);
    let c_hi_srgb = vec4f(pow((*colors)[hi].rgb, vec3f(1.0 / 2.2)), (*colors)[hi].a);
    let mixed_srgb = (1.0 - u) * c_lo_srgb + u * c_hi_srgb;
    return vec4f(pow(mixed_srgb.rgb, vec3f(2.2)), mixed_srgb.a);
}
""".trimIndent()

    private fun conicalUniformBytes(desc: GPUMaterialDescriptor.ConicalGradient): ByteArray {
        return packGradientUniforms(
            geometryPacker = { bb ->
                bb.putFloat(desc.startX); bb.putFloat(desc.startY)
                bb.putFloat(desc.endX); bb.putFloat(desc.endY)
                bb.putFloat(desc.startRadius); bb.putFloat(desc.endRadius)
                val n = desc.allStopPositions?.size ?: 2
                bb.putInt(n)
                bb.putInt(0); bb.putInt(0); bb.putInt(0)
                bb.putInt(0); bb.putInt(0) // pad to 48
            },
            allStopPositions = desc.allStopPositions,
            allStopColors = desc.allStopColors,
        )
    }

    private fun tileFnForMode(tileMode: String): Pair<String, String> {
        return when (tileMode) {
            "clamp" -> Pair("let t = clamp(t_raw, 0.0, 1.0);", "")
            "repeat" -> Pair("let t = t_raw - floor(t_raw);", "")
            "mirror" -> Pair("""
                let even = (floor(t_raw) % 2.0) == 0.0;
                let t = select(1.0 - (t_raw - floor(t_raw)), t_raw - floor(t_raw), even);
            """.trimIndent(), "")
            "decal" -> Pair("""
                let t = clamp(t_raw, 0.0, 1.0);
                let decalA = select(0.0, 1.0, t_raw >= 0.0 && t_raw <= 1.0);
            """.trimIndent(), " * decalA")
            else -> Pair("let t = clamp(t_raw, 0.0, 1.0);", "")
        }
    }

    private fun buildGradientWgsl(
        preamble: String,
        stopCount: Int,
        headerExtra: String,
        structFields: String,
        tileFn: String,
        decalSuffix: String = "",
    ): String = """
struct GradientBlock {
    $structFields
    count: u32,
    stopData: array<vec4<f32>, 32>,
}
@group(0) @binding(0) var<uniform> gradient: GradientBlock;

struct VertexOutput {
    @builtin(position) pos: vec4<f32>,
}

@vertex fn vs_main(@builtin(vertex_index) vi: u32) -> VertexOutput {
    let verts = array<vec2<f32>, 3>(
        vec2<f32>(-1.0, -1.0),
        vec2<f32>(3.0, -1.0),
        vec2<f32>(-1.0, 3.0),
    );
    return VertexOutput(vec4<f32>(verts[vi], 0.0, 1.0));
}

@fragment fn fs_main(@builtin(position) pos: vec4<f32>) -> @location(0) vec4<f32> {
    $preamble
    $tileFn
    var positions: array<vec4<f32>, 16>;
    var colors: array<vec4<f32>, 16>;
    for (var i: u32 = 0u; i < ${stopCount}u; i = i + 1u) {
        positions[i] = gradient.stopData[i * 2u];
        colors[i] = gradient.stopData[i * 2u + 1u];
    }
    return sample_stops_at(t, ${stopCount}u, &positions, &colors)${decalSuffix};
}

fn sample_stops_at(t: f32, count: u32, positions: ptr<function, array<vec4<f32>, 16>>, colors: ptr<function, array<vec4<f32>, 16>>) -> vec4<f32> {
    if (count <= 1u) { return (*colors)[0]; }
    if (t <= (*positions)[0].x) { return (*colors)[0]; }
    let lastIdx = count - 1u;
    if (t >= (*positions)[lastIdx].x) { return (*colors)[lastIdx]; }
    var lo: u32 = 0u;
    for (var i: u32 = 1u; i < count; i = i + 1u) {
        if ((*positions)[i].x >= t) { lo = i - 1u; break; }
    }
    let hi = lo + 1u;
    let t0 = (*positions)[lo].x;
    let t1 = (*positions)[hi].x;
    let span = t1 - t0;
    let u = select((t - t0) / span, 0.0, span <= 0.0);
    let c_lo_srgb = vec4f(pow((*colors)[lo].rgb, vec3f(1.0 / 2.2)), (*colors)[lo].a);
    let c_hi_srgb = vec4f(pow((*colors)[hi].rgb, vec3f(1.0 / 2.2)), (*colors)[hi].a);
    let mixed_srgb = (1.0 - u) * c_lo_srgb + u * c_hi_srgb;
    return vec4f(pow(mixed_srgb.rgb, vec3f(2.2)), mixed_srgb.a);
}
""".trimIndent()

    // ---- Uniform packing ----

    private fun linearUniformBytes(desc: GPUMaterialDescriptor.LinearGradient): ByteArray {
        return packGradientUniforms(
            geometryPacker = { bb ->
                bb.putFloat(desc.startX); bb.putFloat(desc.startY)
                bb.putFloat(desc.endX); bb.putFloat(desc.endY)
                val n = desc.allStopPositions?.size ?: 2
                bb.putInt(n)
                bb.putInt(0); bb.putInt(0); bb.putInt(0) // pad to 32
            },
            allStopPositions = desc.allStopPositions,
            allStopColors = desc.allStopColors,
        )
    }

    private fun radialUniformBytes(desc: GPUMaterialDescriptor.RadialGradient): ByteArray {
        return packGradientUniforms(
            geometryPacker = { bb ->
                bb.putFloat(desc.centerX); bb.putFloat(desc.centerY)
                bb.putFloat(desc.radius)
                val n = desc.allStopPositions?.size ?: 2
                bb.putInt(n)
            },
            allStopPositions = desc.allStopPositions,
            allStopColors = desc.allStopColors,
        )
    }

    private fun sweepUniformBytes(desc: GPUMaterialDescriptor.SweepGradient): ByteArray {
        return packGradientUniforms(
            geometryPacker = { bb ->
                bb.putFloat(desc.centerX); bb.putFloat(desc.centerY)
                bb.putFloat(desc.startAngle); bb.putFloat(desc.endAngle)
                val n = desc.allStopPositions?.size ?: 2
                bb.putInt(n)
                bb.putInt(0); bb.putInt(0); bb.putInt(0) // pad to 32
            },
            allStopPositions = desc.allStopPositions,
            allStopColors = desc.allStopColors,
        )
    }

    private const val STRUCT_HEADER_SIZE = 32
    private const val MAX_STOPS_WGSL = 16
    private const val BYTES_PER_STOP = 32  // vec4f position + vec4f color
    private const val FULL_STRUCT_SIZE = STRUCT_HEADER_SIZE + MAX_STOPS_WGSL * BYTES_PER_STOP // 544

    private fun packGradientUniforms(
        geometryPacker: (java.nio.ByteBuffer) -> Unit,
        allStopPositions: FloatArray?,
        allStopColors: FloatArray?,
    ): ByteArray {
        val n = allStopPositions?.size ?: 2
        val bb = java.nio.ByteBuffer.allocate(FULL_STRUCT_SIZE)
            .order(java.nio.ByteOrder.nativeOrder())
        geometryPacker(bb)
        for (i in 0 until MAX_STOPS_WGSL) {
            if (i < n) {
                val pos = allStopPositions?.getOrElse(i) {
                    i.toFloat() / (n - 1).coerceAtLeast(1)
                } ?: (i.toFloat() / (n - 1).coerceAtLeast(1))
                bb.putFloat(pos); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                if (allStopColors != null && i * 4 + 3 < allStopColors.size) {
                    val r = srgbToLinear(allStopColors[i * 4]) * allStopColors[i * 4 + 3]
                    val g = srgbToLinear(allStopColors[i * 4 + 1]) * allStopColors[i * 4 + 3]
                    val b = srgbToLinear(allStopColors[i * 4 + 2]) * allStopColors[i * 4 + 3]
                    val a = allStopColors[i * 4 + 3]
                    bb.putFloat(r); bb.putFloat(g); bb.putFloat(b); bb.putFloat(a)
                } else {
                    bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                }
            } else {
                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
                bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
            }
        }
        return bb.array()
    }

    private fun srgbToLinear(c: Float): Float {
        return if (c <= 0.04045f) c / 12.92f
        else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
}

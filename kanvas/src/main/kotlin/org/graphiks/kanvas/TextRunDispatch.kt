package org.graphiks.kanvas

import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerScopeKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.wgsl.TextAtlasA8Wgsl
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Per-glyph A8 mask WGSL. Reuses the proven texture/sampler binding snippet
 * ([TextAtlasA8Wgsl], `@group(1)`), and maps each fragment's device position into
 * the glyph's atlas sub-rectangle so one quad per glyph samples only its region.
 * Coverage modulates the (premultiplied) paint color — `A8TextMaskStep` semantics.
 */
internal val TextAtlasGlyphWgsl: String = """
struct Uniforms {
    color: vec4f,
    targetRect: vec4f,
    atlasUV: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$TextAtlasA8Wgsl

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let local = (pos.xy - uniforms.targetRect.xy) / uniforms.targetRect.zw;
    let uv = uniforms.atlasUV.xy + local * uniforms.atlasUV.zw;
    let t = text_atlas_source(uv);
    return vec4f(uniforms.color.rgb * uniforms.color.a * t.a, uniforms.color.a * t.a);
}
""".trimIndent()

/** One glyph quad: device target rect, normalized atlas UV sub-rect, paint color, and scissor. */
internal data class TextGlyphPlacement(
    val colorR: Float,
    val colorG: Float,
    val colorB: Float,
    val colorA: Float,
    val targetLeft: Float,
    val targetTop: Float,
    val targetWidth: Float,
    val targetHeight: Float,
    val uvLeft: Float,
    val uvTop: Float,
    val uvWidth: Float,
    val uvHeight: Float,
    val scissorX: Int,
    val scissorY: Int,
    val scissorWidth: Int,
    val scissorHeight: Int,
) {
    /** 48-byte little-endian uniform: color vec4, targetRect vec4, atlasUV vec4. */
    fun uniformBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(colorR); buffer.putFloat(colorG); buffer.putFloat(colorB); buffer.putFloat(colorA)
        buffer.putFloat(targetLeft); buffer.putFloat(targetTop); buffer.putFloat(targetWidth); buffer.putFloat(targetHeight)
        buffer.putFloat(uvLeft); buffer.putFloat(uvTop); buffer.putFloat(uvWidth); buffer.putFloat(uvHeight)
        return buffer.array()
    }
}

/** Result of planning a [NormalizedDrawCommand.DrawTextRun] into an A8 atlas-sample dispatch. */
internal sealed interface TextRunDispatchPlan {
    data class Draws(
        val atlasBytes: ByteArray,
        val atlasWidth: Int,
        val atlasHeight: Int,
        val placements: List<TextGlyphPlacement>,
    ) : TextRunDispatchPlan

    data class Refused(val reason: String) : TextRunDispatchPlan
}

/**
 * Pure planner: turns a `DrawTextRun` carrying a real atlas artifact into per-glyph
 * quad placements, or a stable refusal. No GPU access — fully unit-testable.
 */
internal object TextRunDispatchPlanner {
    fun plan(cmd: NormalizedDrawCommand.DrawTextRun, surfaceWidth: Int, surfaceHeight: Int): TextRunDispatchPlan {
        val material = cmd.material
        if (material !is GPUMaterialDescriptor.SolidColor) {
            return TextRunDispatchPlan.Refused("unsupported_material:${material.kind.name}")
        }
        if (cmd.transform.type != GPUTransformType.Identity) {
            return TextRunDispatchPlan.Refused("unsupported_transform:${cmd.transform.type.name}")
        }
        if (cmd.clip.kind !in listOf(GPUClipKind.WideOpen, GPUClipKind.DeviceRect)) {
            return TextRunDispatchPlan.Refused("unsupported_clip:${cmd.clip.kind.name}")
        }
        if (cmd.layer.scopeKind != GPULayerScopeKind.Root) {
            return TextRunDispatchPlan.Refused("unsupported_layer:${cmd.layer.scopeKind.name}")
        }

        val descriptor = cmd.glyphRunDescriptor
            ?: return TextRunDispatchPlan.Refused("no_glyph_run")
        val accepted = when (val atlasPlan = descriptor.atlasPlan) {
            is GlyphAtlasUploadPlan.Refused -> return TextRunDispatchPlan.Refused("atlas_refused:${atlasPlan.reason}")
            is GlyphAtlasUploadPlan.Accepted -> atlasPlan
        }
        if (accepted.atlasWidth <= 0 || accepted.atlasHeight <= 0 || accepted.atlasBytes.isEmpty()) {
            return TextRunDispatchPlan.Refused("empty_atlas")
        }

        val atlasWidth = accepted.atlasWidth
        val atlasHeight = accepted.atlasHeight
        val placements = mutableListOf<TextGlyphPlacement>()
        for (glyph in descriptor.glyphs) {
            val region = glyph.placement.region
            if (region.width <= 0 || region.height <= 0) continue
            val left = glyph.drawX
            val top = glyph.drawY
            val targetWidth = region.width.toFloat()
            val targetHeight = region.height.toFloat()

            val scissorX = left.toInt().coerceIn(0, surfaceWidth - 1)
            val scissorY = top.toInt().coerceIn(0, surfaceHeight - 1)
            val scissorWidth = targetWidth.toInt().coerceIn(1, surfaceWidth - scissorX)
            val scissorHeight = targetHeight.toInt().coerceIn(1, surfaceHeight - scissorY)

            placements += TextGlyphPlacement(
                colorR = material.r, colorG = material.g, colorB = material.b, colorA = material.a,
                targetLeft = left, targetTop = top, targetWidth = targetWidth, targetHeight = targetHeight,
                uvLeft = region.x.toFloat() / atlasWidth,
                uvTop = region.y.toFloat() / atlasHeight,
                uvWidth = targetWidth / atlasWidth,
                uvHeight = targetHeight / atlasHeight,
                scissorX = scissorX, scissorY = scissorY,
                scissorWidth = scissorWidth, scissorHeight = scissorHeight,
            )
        }

        if (placements.isEmpty()) return TextRunDispatchPlan.Refused("no_drawable_glyphs")
        return TextRunDispatchPlan.Draws(accepted.atlasBytes, atlasWidth, atlasHeight, placements)
    }
}

/**
 * CPU oracle that composites the same A8 atlas glyphs the GPU pass draws, using nearest
 * sampling and the same coverage×color (premultiplied) formula. Used to prove GPU↔CPU parity.
 */
internal object TextRunCpuOracle {
    fun composite(plan: TextRunDispatchPlan.Draws, surfaceWidth: Int, surfaceHeight: Int): ByteArray {
        val rgba = ByteArray(surfaceWidth * surfaceHeight * 4)
        for (placement in plan.placements) {
            val x0 = placement.scissorX
            val y0 = placement.scissorY
            val x1 = (placement.scissorX + placement.scissorWidth).coerceAtMost(surfaceWidth)
            val y1 = (placement.scissorY + placement.scissorHeight).coerceAtMost(surfaceHeight)
            for (py in y0 until y1) {
                for (px in x0 until x1) {
                    val localX = (px + 0.5f - placement.targetLeft) / placement.targetWidth
                    val localY = (py + 0.5f - placement.targetTop) / placement.targetHeight
                    if (localX < 0f || localX > 1f || localY < 0f || localY > 1f) continue
                    val u = placement.uvLeft + localX * placement.uvWidth
                    val v = placement.uvTop + localY * placement.uvHeight
                    val tx = (u * plan.atlasWidth).toInt().coerceIn(0, plan.atlasWidth - 1)
                    val ty = (v * plan.atlasHeight).toInt().coerceIn(0, plan.atlasHeight - 1)
                    val coverage = (plan.atlasBytes[ty * plan.atlasWidth + tx].toInt() and 0xFF) / 255f
                    if (coverage <= 0f) continue
                    val index = (py * surfaceWidth + px) * 4
                    rgba[index] = channel(placement.colorR * placement.colorA * coverage)
                    rgba[index + 1] = channel(placement.colorG * placement.colorA * coverage)
                    rgba[index + 2] = channel(placement.colorB * placement.colorA * coverage)
                    rgba[index + 3] = channel(placement.colorA * coverage)
                }
            }
        }
        return rgba
    }

    fun nonTransparentPixels(rgba: ByteArray): Int {
        var count = 0
        var i = 3
        while (i < rgba.size) {
            if ((rgba[i].toInt() and 0xFF) > 0) count++
            i += 4
        }
        return count
    }

    private fun channel(value: Float): Byte = (value * 255f).toInt().coerceIn(0, 255).toByte()
}

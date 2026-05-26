package org.skia.effects.runtime

import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SkColor
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkData
import org.skia.foundation.SkShader
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Concrete [SkShader] backing a registered [SkRuntimeImpl] of
 * shader kind. Phase D2.2 binding — wraps the impl so it slots
 * into `paint.shader` like any other shader subtype.
 *
 * **Per-pixel evaluation** : [shadeRow] walks `count` device-space
 * pixels, maps each through [SkShader.deviceToLocal] to obtain
 * the local-space sample point, calls
 * [SkRuntimeImpl.shade] with the local point, and packs the
 * resulting [SkColor4f] into the destination [SkColor] (8-bit
 * ARGB) array. Children are resolved as
 * [ChildResolver.Shader] / [ChildResolver.ColorFilter] /
 * [ChildResolver.Blender] depending on the slot's declared type.
 *
 * **Limitations (D2.2)** :
 *  - Only shader children are fully supported. Color-filter /
 *    blender children compile but throw at first call (caller
 *    must pass `null` slots until D2.4 lands the impls that use
 *    them).
 *  - F16 fast path falls back to byte-precision through
 *    [shadeRowF16]'s default — full F16 support deferred until
 *    a runtime effect proves it matters in practice.
 *
 * Constructed exclusively by
 * [SkRuntimeEffect.makeShader] ; the constructor is `internal`.
 */
public class SkRuntimeShader internal constructor(
    /** Registered impl. Already instantiated by [SkRuntimeEffect.impl]. */
    private val impl: SkRuntimeImpl,
    public val runtimeEffectDescriptor: SkRuntimeEffectDescriptor?,
    /** Snapshot of the uniforms passed at construction. */
    private val uniformsBuffer: ByteBuffer,
    /** Pre-built resolvers, one per declared child. */
    private val childResolvers: Array<ChildResolver>,
    localMatrix: SkMatrix,
) : SkShader(localMatrix) {
    public fun runtimeEffectUniformBytes(): ByteArray {
        val dup = uniformsBuffer.duplicate()
        dup.position(0)
        val out = ByteArray(dup.capacity())
        dup.get(out)
        return out
    }

    override fun setupForDraw(
        canvasCtm: SkMatrix,
        xform: SkColorSpaceXformSteps,
    ) {
        // Default base impl computes the (canvasCtm · localMatrix)
        // inverse — that's all we need for the per-pixel local-space
        // mapping. No stop pre-transformation : the impl owns
        // colour generation.
        super.setupForDraw(canvasCtm, xform)
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        require(dst.size >= count) {
            "dst too small : ${dst.size} < $count"
        }
        val inv = deviceToLocal
        if (inv == null) {
            // Singular matrix — the impl can't sample meaningfully.
            // Fill with transparent black (matches Skia's
            // singular-CTM degeneracy behaviour for shaders that
            // don't have a constant-fallback).
            for (i in 0 until count) dst[i] = 0
            return
        }
        // Reset buffer position for each shadeRow ; the impl reads
        // uniforms by absolute offset, but slicing the buffer to
        // limit() = capacity is cheap and resets internal mark/pos.
        val u = uniformsBuffer.duplicate().order(ByteOrder.nativeOrder())
        for (i in 0 until count) {
            val (lx, ly) = inv.mapXY((devX + i).toFloat() + 0.5f, devY.toFloat() + 0.5f)
            val out = impl.shade(
                coords = SkPoint(lx, ly),
                srcColor = null,
                dstColor = null,
                uniforms = u,
                children = childResolvers,
            )
            dst[i] = pack4fToColor(out)
        }
    }

    override fun sampleAtLocal(lx: Float, ly: Float): SkColor {
        // Bypass the canvasCtm × localMatrix chain — the caller
        // supplies a local-space point already.
        val u = uniformsBuffer.duplicate().order(ByteOrder.nativeOrder())
        val out = impl.shade(
            coords = SkPoint(lx, ly),
            srcColor = null,
            dstColor = null,
            uniforms = u,
            children = childResolvers,
        )
        return pack4fToColor(out)
    }

    public companion object {
        /**
         * Pack a [SkColor4f] (unpremultiplied, ≥ 0) into an 8-bit
         * ARGB [SkColor]. Values are clamped to `[0, 1]` per
         * channel — same convention as
         * [SkColor4f.toSkColor] but inlined for the hot path.
         */
        internal fun pack4fToColor(c: SkColor4f): SkColor {
            val a = (c.fA.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val r = (c.fR.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val g = (c.fG.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val b = (c.fB.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            return SkColorSetARGB(a, r, g, b)
        }

        /** Convert a packed ARGB [SkColor] back to [SkColor4f]
         *  (unpremul, normalised). Inverse of [pack4fToColor]. */
        internal fun unpackColorTo4f(c: SkColor): SkColor4f = SkColor4f(
            fR = SkColorGetR(c) * INV_255,
            fG = SkColorGetG(c) * INV_255,
            fB = SkColorGetB(c) * INV_255,
            fA = SkColorGetA(c) * INV_255,
        )

        private const val INV_255: Float = 1f / 255f

        /**
         * Build a fresh [ByteBuffer] holding [data]'s bytes (or
         * a zero-byte empty one if [data] is null). The returned
         * buffer is ready-to-read at position 0 ; the binding
         * uses `duplicate()` per call to avoid sharing position
         * state across threads.
         */
        internal fun makeUniformsBuffer(data: SkData?): ByteBuffer {
            val bytes = data?.toByteArray() ?: ByteArray(0)
            return ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        }

        /**
         * Build the per-child resolver array. Each child slot's
         * declared type ([SkRuntimeEffect.ChildType]) must match
         * the supplied resolver variant ; mismatch throws
         * [IllegalArgumentException].
         *
         * **Children pre-checked** : the binding layer (in the
         * `makeXxx` factories on [SkRuntimeEffect]) wraps the
         * raw `Array<SkShader?>` / `Array<SkColorFilter?>` /
         * `Array<SkBlender?>` callers pass into the resolver
         * array. Null slots are passed through as
         * [identityResolver] — the impl receives the
         * still-typed [ChildResolver.Shader] etc., but the
         * lambda inside returns `SkColor4f.kTransparent` (matches
         * upstream's behaviour for missing children).
         */
        internal fun buildShaderChildResolvers(
            declared: List<SkRuntimeEffect.Child>,
            children: Array<SkShader?>,
            canvasCtm: SkMatrix,
            xform: SkColorSpaceXformSteps,
        ): Array<ChildResolver> {
            require(declared.size == children.size) {
                "child count mismatch : declared=${declared.size}, given=${children.size}"
            }
            // Set up every non-null shader for sampling.
            for (s in children) s?.setupForDraw(canvasCtm, xform)

            return Array(declared.size) { i ->
                val decl = declared[i]
                require(decl.type == SkRuntimeEffect.ChildType.kShader) {
                    "SkRuntimeShader only accepts shader children (slot ${decl.name} declared as ${decl.type})"
                }
                val s = children[i]
                if (s == null) identityShaderResolver
                else ChildResolver.Shader { p ->
                    unpackColorTo4f(s.sampleAtLocal(p.fX, p.fY))
                }
            }
        }

        /** Resolver returned for a `null` child slot — answers
         *  transparent black at every query point. */
        internal val identityShaderResolver: ChildResolver.Shader =
            ChildResolver.Shader { _ -> SkColor4f.kTransparent }
    }
}

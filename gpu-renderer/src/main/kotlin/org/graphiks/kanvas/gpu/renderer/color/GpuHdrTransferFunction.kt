package org.graphiks.kanvas.gpu.renderer.color

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import kotlin.math.*

enum class GpuHdrTransferFunction {
    PQ, HLG, scRGBLinear;

    fun eotf(encoded: Float): Float = when (this) {
        PQ -> pqEotf(encoded)
        HLG -> hlgOetfInverse(encoded)
        scRGBLinear -> encoded
    }

    fun oetfInverse(linear: Float): Float = when (this) {
        PQ -> pqEotf(linear)
        HLG -> hlgOetfInverse(linear)
        scRGBLinear -> linear
    }

    fun wgslEotfFunction(): String = when (this) {
        PQ -> """
            fn pq_eotf(N: f32) -> f32 {
                let m1 = 0.1593017578125;
                let m2 = 78.84375;
                let c1 = 0.8359375;
                let c2 = 18.8515625;
                let c3 = 18.6875;
                let Np = pow(N, 1.0 / m2);
                let num = max(Np - c1, 0.0);
                let den = c2 - c3 * Np;
                return pow(num / den, 1.0 / m1);
            }
        """.trimIndent()
        HLG -> """
            fn hlg_oetf_inverse(E: f32) -> f32 {
                let a = 0.17883277;
                let b = 1.0 - 4.0 * a;
                let c = 0.5 - a * ln(4.0 * a);
                if (E <= 0.5) {
                    return E * E / 3.0;
                } else {
                    return (exp((E - c) / a) + b) / 12.0;
                }
            }
        """.trimIndent()
        scRGBLinear -> """
            fn scrgb_linear_to_display(color: vec4<f32>) -> vec4<f32> {
                return color;
            }
        """.trimIndent()
    }

    companion object {
        private const val PQ_M1 = 0.1593017578125f
        private const val PQ_M2 = 78.84375f
        private const val PQ_C1 = 0.8359375f
        private const val PQ_C2 = 18.8515625f
        private const val PQ_C3 = 18.6875f

        private fun pqEotf(N: Float): Float {
            val Np = N.toDouble().pow(1.0 / PQ_M2).toFloat()
            val num = max(Np - PQ_C1, 0f)
            val den = PQ_C2 - PQ_C3 * Np
            if (den <= 0f) return 0f
            return (num / den).toDouble().pow(1.0 / PQ_M1).toFloat()
        }

        private const val HLG_A = 0.17883277f
        private const val HLG_B = 1.0f - 4.0f * HLG_A
        private val HLG_C = 0.5f - HLG_A * ln(4.0 * HLG_A).toFloat()

        private fun hlgOetfInverse(E: Float): Float {
            return if (E <= 0.5f) {
                (E * E) / 3.0f
            } else {
                val d = (E - HLG_C) / HLG_A
                (exp(d.toDouble()) + HLG_B).toFloat() / 12.0f
            }
        }
    }
}

enum class GpuHdrToneMapStrategy {
    Reinhard, ACES, Hable, Custom;

    fun apply(hdrValue: Float, peakLuminance: Float = 1.0f): Float = when (this) {
        Reinhard -> {
            val v = hdrValue * peakLuminance
            v / (1.0f + v)
        }
        ACES -> acesToneMap(hdrValue)
        Hable -> hableToneMap(hdrValue)
        Custom -> hdrValue.coerceIn(0f, peakLuminance)
    }

    companion object {
        private fun acesToneMap(v: Float): Float {
            val a = 2.51f; val b = 0.03f; val c = 2.43f
            val d = 0.59f; val e = 0.14f
            val mapped = (v * (a * v + b)) / (v * (c * v + d) + e)
            return mapped.coerceIn(0f, 1f)
        }

        private fun hableToneMap(v: Float): Float {
            val a = 0.15f; val b = 0.50f; val c = 0.10f
            val d = 0.20f; val e = 0.02f; val f = 0.30f
            val mapped = ((v * (a * v + c * b) + d * e) / (v * (a * v + b) + d * f)) - e / f
            return mapped.coerceIn(0f, 1f)
        }
    }
}

data class GpuHdrTransferFunctionPlan(
    val transferFunction: GpuHdrTransferFunction,
    val wgslSource: String,
    val colorFormat: String = "rgba16float",
) {
    companion object {
        fun forTransfer(tf: GpuHdrTransferFunction): GpuHdrTransferFunctionPlan =
            GpuHdrTransferFunctionPlan(
                transferFunction = tf,
                wgslSource = tf.wgslEotfFunction(),
            )

        fun generateToneMapShader(strategy: GpuHdrToneMapStrategy): String = when (strategy) {
            GpuHdrToneMapStrategy.Reinhard -> """
                fn reinhard_tone_map(color: vec3<f32>) -> vec3<f32> {
                    return color / (1.0 + color);
                }
            """.trimIndent()
            GpuHdrToneMapStrategy.ACES -> """
                fn aces_tone_map(color: vec3<f32>) -> vec3<f32> {
                    let a = 2.51; let b = 0.03; let c = 2.43;
                    let d = 0.59; let e = 0.14;
                    return (color * (a * color + b)) / (color * (c * color + d) + e);
                }
            """.trimIndent()
            GpuHdrToneMapStrategy.Hable -> """
                fn hable_tone_map(color: vec3<f32>) -> vec3<f32> {
                    let a = 0.15; let b = 0.50; let c = 0.10;
                    let d = 0.20; let e = 0.02; let f = 0.30;
                    return ((color * (a * color + c * b) + d * e) / (color * (a * color + b) + d * f)) - e / f;
                }
            """.trimIndent()
            GpuHdrToneMapStrategy.Custom -> """
                fn custom_tone_map(color: vec3<f32>) -> vec3<f32> {
                    return clamp(color, vec3(0.0), vec3(1.0));
                }
            """.trimIndent()
        }
    }

    fun analyze(
        displayPeakLuminance: Float = 1000f,
        toneMapStrategy: GpuHdrToneMapStrategy? = GpuHdrToneMapStrategy.ACES,
    ): GpuHdrTransferRoute {
        val eotfPlan = GpuHdrEotfPlan(
            eotf = transferFunction,
            displayPeakLuminance = displayPeakLuminance,
            wgslSource = wgslSource,
        )
        val toneMapPlan = if (transferFunction != GpuHdrTransferFunction.scRGBLinear) {
            GpuHdrToneMapPlan(
                strategy = toneMapStrategy ?: GpuHdrToneMapStrategy.ACES,
                displayPeakLuminance = displayPeakLuminance,
                wgslSource = generateToneMapShader(toneMapStrategy ?: GpuHdrToneMapStrategy.ACES),
            )
        } else null
        return GpuHdrTransferRoute.Accepted(
            GpuHdrTransferRoute.Accepted.AcceptedData(
                transferPlan = this,
                eotfPlan = eotfPlan,
                toneMapPlan = toneMapPlan,
            )
        )
    }
}

data class GpuHdrEotfPlan(
    val eotf: GpuHdrTransferFunction,
    val displayPeakLuminance: Float,
    val wgslSource: String,
)

data class GpuHdrToneMapPlan(
    val strategy: GpuHdrToneMapStrategy,
    val displayPeakLuminance: Float,
    val wgslSource: String,
)

sealed interface GpuHdrTransferRoute {
    data class Accepted(
        val transferPlan: GpuHdrTransferFunctionPlan,
        val eotfPlan: GpuHdrEotfPlan,
        val toneMapPlan: GpuHdrToneMapPlan?,
    ) : GpuHdrTransferRoute {
        data class AcceptedData(
            val transferPlan: GpuHdrTransferFunctionPlan,
            val eotfPlan: GpuHdrEotfPlan,
            val toneMapPlan: GpuHdrToneMapPlan?,
        )
        constructor(data: AcceptedData) : this(data.transferPlan, data.eotfPlan, data.toneMapPlan)
    }
    data class Refused(val diagnostic: RefuseDiagnostic) : GpuHdrTransferRoute
}

package org.graphiks.kanvas.color.hdr

import org.graphiks.kanvas.color.PQ_PEAK_NITS
import org.graphiks.kanvas.color.pqEotfNits
import org.graphiks.kanvas.color.pqInverseEotf
import kotlin.math.min

public fun interface ToneMapper {
    /** Maps absolute display-light RGB in nits to linear RGB relative to target white. */
    public fun map(linearRgb: FloatArray, offset: Int)
}

public fun interface ToneMapperFactory {
    public fun create(targetPeakNits: Double): ToneMapper
}

/** Factory for the BT.2390 Hermite-knee EETF using the report's YRGB application. */
public object Bt2390ToneMapper : ToneMapperFactory {
    override fun create(targetPeakNits: Double): ToneMapper = Bt2390ToneMapperImpl(targetPeakNits)

    public operator fun invoke(targetPeakNits: Double): ToneMapper = create(targetPeakNits)
}

private class Bt2390ToneMapperImpl(
    private val targetPeakNits: Double,
) : ToneMapper {
    private val maxLum: Double
    private val kneeStart: Double

    init {
        require(targetPeakNits.isFinite() && targetPeakNits > 0.0 && targetPeakNits <= PQ_PEAK_NITS) {
            "targetPeakNits must be finite and in (0, 10000]"
        }
        maxLum = pqInverseEotf(targetPeakNits)
        kneeStart = 1.5 * maxLum - 0.5
    }

    override fun map(linearRgb: FloatArray, offset: Int) {
        require(offset >= 0 && offset.toLong() + RGB_CHANNELS <= linearRgb.size.toLong()) {
            "linearRgb must contain three RGB components at offset"
        }
        val red = finiteLight(linearRgb[offset])
        val green = finiteLight(linearRgb[offset + 1])
        val blue = finiteLight(linearRgb[offset + 2])
        val sourceLuminance = RED_Y * red + GREEN_Y * green + BLUE_Y * blue
        val maximum = maxOf(red, green, blue)
        if (sourceLuminance <= 0.0 || maximum <= 0.0) {
            linearRgb.fill(0f, offset, offset + RGB_CHANNELS)
            return
        }

        val targetRelativeLuminance = mapLuminance(sourceLuminance) / targetPeakNits
        val luminanceScale = targetRelativeLuminance / sourceLuminance
        val gamutScale = 1.0 / maximum
        val scale = min(luminanceScale, gamutScale)
        linearRgb[offset] = (red * scale).toFloat()
        linearRgb[offset + 1] = (green * scale).toFloat()
        linearRgb[offset + 2] = (blue * scale).toFloat()
    }

    private fun mapLuminance(sourceNits: Double): Double {
        val e1 = pqInverseEotf(sourceNits.coerceAtMost(PQ_PEAK_NITS))
        val e2 = if (e1 < kneeStart) {
            e1
        } else {
            val t = (e1 - kneeStart) / (1.0 - kneeStart)
            val t2 = t * t
            val t3 = t2 * t
            (2.0 * t3 - 3.0 * t2 + 1.0) * kneeStart +
                (t3 - 2.0 * t2 + t) * (1.0 - kneeStart) +
                (-2.0 * t3 + 3.0 * t2) * maxLum
        }
        return pqEotfNits(e2.coerceIn(0.0, maxLum))
    }

    private companion object {
        const val RGB_CHANNELS: Int = 3
        const val RED_Y: Double = 0.2627
        const val GREEN_Y: Double = 0.6780
        const val BLUE_Y: Double = 0.0593

        fun finiteLight(value: Float): Double = if (value.isFinite()) value.coerceAtLeast(0f).toDouble() else 0.0
    }
}

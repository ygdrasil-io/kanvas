package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientGeometryPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientKind
import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientStopPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientStopStorePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUImageShaderPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSamplingPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialTileMode
import org.graphiks.kanvas.gpu.renderer.materials.GPUSolidColorPlan

enum class KanvasTileMode(val label: String) {
    CLAMP("clamp"),
    REPEAT("repeat"),
    MIRROR("mirror"),
    DECAL("decal"),
}

sealed class Shader {

    fun lower(): GPUMaterialSourceDescriptor = when (this) {
        is SolidColor -> GPUMaterialSourceDescriptor.Solid(
            plan = GPUSolidColorPlan(r = r, g = g, b = b, a = a, colorSpecLabel = "sRGB"),
        )
        is LinearGradient -> lowerGradient(
            kind = GPUGradientKind.Linear,
            controlPoints = listOf(start.x, start.y, end.x, end.y),
            stops = stops,
            positions = positions,
        )
        is RadialGradient -> lowerGradient(
            kind = GPUGradientKind.Radial,
            controlPoints = listOf(center.x, center.y, radius),
            stops = stops,
            positions = positions,
        )
        is SweepGradient -> lowerGradient(
            kind = GPUGradientKind.Sweep,
            controlPoints = listOf(center.x, center.y, startAngle, endAngle),
            stops = stops,
            positions = positions,
        )
        is Bitmap -> GPUMaterialSourceDescriptor.Image(
            plan = GPUImageShaderPlan(
                imageSourceKey = "kanvas-image-${System.identityHashCode(image)}",
                sampling = GPUMaterialSamplingPlan(
                    tileModeX = GPUMaterialTileMode.Clamp,
                    tileModeY = GPUMaterialTileMode.Clamp,
                    filterMode = "linear",
                    mipmapMode = "none",
                ),
                colorTreatment = "srgb-unchanged",
            ),
        )
        is RuntimeEffect -> GPUMaterialSourceDescriptor.RuntimeEffect(
            effectId = effectId,
            descriptorVersion = descriptorVersion,
            routeContractHash = "runtime-effect:$effectId",
        )
    }

    private fun lowerGradient(
        kind: GPUGradientKind,
        controlPoints: List<Float>,
        stops: List<Triple<Float, Float, Float>>,
        positions: List<Float>?,
    ): GPUMaterialSourceDescriptor.Gradient {
        val stopList = stops.mapIndexed { i: Int, stop: Triple<Float, Float, Float> ->
            val pos = positions?.getOrElse(i) { i.toFloat() / maxOf(stops.size - 1, 1) }
                ?: i.toFloat() / maxOf(stops.size - 1, 1)
            GPUGradientStopPlan(
                offset = pos,
                colorLabel = "rgb(${stop.first},${stop.second},${stop.third})",
            )
        }
        return GPUMaterialSourceDescriptor.Gradient(
            plan = GPUGradientPlan(
                geometry = GPUGradientGeometryPlan(
                    kind = kind,
                    controlPoints = controlPoints,
                ),
                stops = stopList,
                stopStore = GPUGradientStopStorePlan(
                    stopCount = stopList.size,
                    storageKind = "inline",
                    payloadHash = "gradient-${System.identityHashCode(this)}",
                ),
                tileMode = GPUMaterialTileMode.Clamp,
            ),
        )
    }

    data class SolidColor(
        val r: Float, val g: Float, val b: Float, val a: Float = 1f,
    ) : Shader()

    data class LinearGradient(
        val start: KanvasPoint, val end: KanvasPoint,
        val stops: List<Triple<Float, Float, Float>>,
        val positions: List<Float>? = null,
        val tileMode: KanvasTileMode = KanvasTileMode.CLAMP,
    ) : Shader()

    data class RadialGradient(
        val center: KanvasPoint, val radius: Float,
        val stops: List<Triple<Float, Float, Float>>,
        val positions: List<Float>? = null,
        val tileMode: KanvasTileMode = KanvasTileMode.CLAMP,
    ) : Shader()

    data class SweepGradient(
        val center: KanvasPoint,
        val startAngle: Float, val endAngle: Float,
        val stops: List<Triple<Float, Float, Float>>,
        val positions: List<Float>? = null,
        val tileMode: KanvasTileMode = KanvasTileMode.CLAMP,
    ) : Shader()

    data class Bitmap(
        val image: Image,
        val tileModeX: KanvasTileMode = KanvasTileMode.CLAMP,
        val tileModeY: KanvasTileMode = KanvasTileMode.CLAMP,
    ) : Shader()

    data class RuntimeEffect(
        val effectId: String,
        val descriptorVersion: Int = 1,
    ) : Shader()
}

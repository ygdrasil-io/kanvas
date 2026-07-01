package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r

internal fun Paint.toMaterial(): GPUMaterialDescriptor {
    val shader = this.shader
    if (shader != null) {
        return shader.toMaterial()
    }
    return GPUMaterialDescriptor.SolidColor(
        r = this.color.r,
        g = this.color.g,
        b = this.color.b,
        a = this.color.a,
    )
}

internal fun Paint.isStroke(): Boolean = style == PaintStyle.STROKE

internal fun Shader.toMaterial(): GPUMaterialDescriptor = when (this) {
    is Shader.SolidColor -> GPUMaterialDescriptor.SolidColor(
        r = this.color.r,
        g = this.color.g,
        b = this.color.b,
        a = this.color.a,
    )
    is Shader.LinearGradient -> {
        val first = this.stops.first()
        val last = this.stops.last()
        val allPos = FloatArray(this.stops.size) { this.stops[it].position }
        val allCol = FloatArray(this.stops.size * 4) { i ->
            val stop = this.stops[i / 4]
            when (i % 4) { 0 -> stop.color.r; 1 -> stop.color.g; 2 -> stop.color.b; else -> stop.color.a }
        }
        GPUMaterialDescriptor.LinearGradient(
            startX = this.start.x,
            startY = this.start.y,
            endX = this.end.x,
            endY = this.end.y,
            startR = first.color.r,
            startG = first.color.g,
            startB = first.color.b,
            startA = first.color.a,
            endR = last.color.r,
            endG = last.color.g,
            endB = last.color.b,
            endA = last.color.a,
            allStopPositions = allPos,
            allStopColors = allCol,
        )
    }
    is Shader.RadialGradient -> {
        val first = this.stops.first()
        val last = this.stops.last()
        val allPos = FloatArray(this.stops.size) { this.stops[it].position }
        val allCol = FloatArray(this.stops.size * 4) { i ->
            val stop = this.stops[i / 4]
            when (i % 4) { 0 -> stop.color.r; 1 -> stop.color.g; 2 -> stop.color.b; else -> stop.color.a }
        }
        GPUMaterialDescriptor.RadialGradient(
            centerX = this.center.x,
            centerY = this.center.y,
            radius = this.radius,
            startR = first.color.r, startG = first.color.g, startB = first.color.b, startA = first.color.a,
            endR = last.color.r, endG = last.color.g, endB = last.color.b, endA = last.color.a,
            allStopPositions = allPos,
            allStopColors = allCol,
        )
    }
    is Shader.Image -> GPUMaterialDescriptor.ImageDraw()
    is Shader.Blend -> GPUMaterialDescriptor.SolidColor(r = 0f, g = 0f, b = 0f, a = 0f)
    is Shader.RuntimeEffect -> GPUMaterialDescriptor.RuntimeEffect()
    is Shader.WithLocalMatrix -> this.shader.toMaterial()
    is Shader.WithColorFilter -> this.shader.toMaterial()
    is Shader.SweepGradient -> {
        val first = this.stops.first()
        val last = this.stops.last()
        val allPos = FloatArray(this.stops.size) { this.stops[it].position }
        val allCol = FloatArray(this.stops.size * 4) { i ->
            val stop = this.stops[i / 4]
            when (i % 4) { 0 -> stop.color.r; 1 -> stop.color.g; 2 -> stop.color.b; else -> stop.color.a }
        }
        GPUMaterialDescriptor.SweepGradient(
            centerX = this.center.x, centerY = this.center.y,
            startAngle = this.startAngle, endAngle = this.endAngle,
            startR = first.color.r, startG = first.color.g, startB = first.color.b, startA = first.color.a,
            endR = last.color.r, endG = last.color.g, endB = last.color.b, endA = last.color.a,
            allStopPositions = allPos,
            allStopColors = allCol,
        )
    }
    is Shader.ConicalGradient -> GPUMaterialDescriptor.LinearGradient(
        startX = this.start.x, startY = this.start.y,
        endX = this.start.x, endY = this.start.y,
        startR = 0f, startG = 0f, startB = 0f, startA = 0f,
        endR = 0f, endG = 0f, endB = 0f, endA = 0f,
    )
}

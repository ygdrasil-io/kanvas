package org.graphiks.kanvas.svg

import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts

class SvgTransformParser {
    fun parse(transform: String?): GPUTransformFacts {
        if (transform == null || transform.isEmpty()) {
            return GPUTransformFacts.identity()
        }

        val transformations = splitTransforms(transform)

        var translateX = 0f
        var translateY = 0f
        var scaleX = 1f
        var scaleY = 1f
        var skewX = 0f
        var skewY = 0f

        for (t in transformations) {
            val (type, args) = parseTransformCommand(t)
            val (newTranslateX, newTranslateY, newScaleX, newScaleY, newSkewX, newSkewY) = applyTransform(type, args)
            translateX += newTranslateX
            translateY += newTranslateY
            scaleX *= newScaleX
            scaleY *= newScaleY
            skewX += newSkewX
            skewY += newSkewY
        }

        return GPUTransformFacts.affine(
            scaleX = scaleX,
            skewX = skewX,
            skewY = skewY,
            scaleY = scaleY,
            translateX = translateX,
            translateY = translateY,
        )
    }

    private fun splitTransforms(transform: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inParens = false
        
        for (char in transform) {
            when (char) {
                '(' -> {
                    inParens = true
                    current.append(char)
                }
                ')' -> {
                    inParens = false
                    current.append(char)
                }
                ' ' -> {
                    if (!inParens && current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun parseTransformCommand(command: String): Pair<String, List<Float>> {
        val openParen = command.indexOf('(')
        val closeParen = command.lastIndexOf(')')
        
        val type = if (openParen >= 0) command.substring(0, openParen) else command
        val argsStr = if (openParen >= 0 && closeParen > openParen) {
            command.substring(openParen + 1, closeParen)
        } else {
            ""
        }
        
        val args = argsStr.split(",".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toFloatOrNull() ?: 0f }
        
        return Pair(type, args)
    }

    private fun applyTransform(type: String, args: List<Float>): Tuple6<Float, Float, Float, Float, Float, Float> {
        return when (type.lowercase()) {
            "translate" -> {
                val tx = args.getOrElse(0) { 0f }
                val ty = args.getOrElse(1) { 0f }
                Tuple6(tx, ty, 1f, 1f, 0f, 0f)
            }
            "scale" -> {
                val sx = args.getOrElse(0) { 1f }
                val sy = args.getOrElse(1) { sx }
                Tuple6(0f, 0f, sx, sy, 0f, 0f)
            }
            "rotate" -> {
                val angle = args.getOrElse(0) { 0f }
                val rad = Math.toRadians(angle.toDouble()).toFloat()
                val cos = kotlin.math.cos(rad)
                val sin = kotlin.math.sin(rad)
                val cx = args.getOrElse(1) { Float.NaN }
                val cy = args.getOrElse(2) { Float.NaN }
                if (cx.isNaN() || cy.isNaN()) {
                    Tuple6(0f, 0f, cos, cos, sin, -sin)
                } else {
                    val tx = cx - cx * cos - cy * sin
                    val ty = cy + cx * sin - cy * cos
                    Tuple6(tx, ty, cos, cos, sin, -sin)
                }
            }
            "skewx" -> {
                val angle = args.getOrElse(0) { 0f }
                val tan = kotlin.math.tan(Math.toRadians(angle.toDouble()).toFloat())
                Tuple6(0f, 0f, 1f, 1f, tan, 0f)
            }
            "skewy" -> {
                val angle = args.getOrElse(0) { 0f }
                val tan = kotlin.math.tan(Math.toRadians(angle.toDouble()).toFloat())
                Tuple6(0f, 0f, 1f, 1f, 0f, tan)
            }
            "matrix" -> {
                val a = args.getOrElse(0) { 1f }
                val b = args.getOrElse(1) { 0f }
                val c = args.getOrElse(2) { 0f }
                val d = args.getOrElse(3) { 1f }
                val e = args.getOrElse(4) { 0f }
                val f = args.getOrElse(5) { 0f }
                Tuple6(e, f, a, d, b, c)
            }
            else -> Tuple6(0f, 0f, 1f, 1f, 0f, 0f)
        }
    }
}

data class Tuple6<out A, out B, out C, out D, out E, out F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
)

package org.graphiks.kanvas.surface

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import org.graphiks.math.color.ColorARGB

/** Independent child-opacity semantics: linear premultiplied child * alpha. */
internal object W5hRuntimeEffectCpuOracle {
    fun linearPremul(color: ColorARGB): List<Double> {
        val a = color.alpha / 255.0
        fun decode(v: Int): Double = (v / 255.0).let {
            if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4)
        } * a
        return listOf(decode(color.red), decode(color.green), decode(color.blue), a)
    }

    fun opacity(child: List<Double>, alpha: Double): List<Double> = child.map { it * alpha }

    fun srcOver(dst: List<Double>, src: List<Double>): List<Double> =
        src.indices.map { src[it] + dst[it] * (1.0 - src[3]) }

    /** Adjacent attachment codes enclosing the independently computed ideal. */
    fun attachment(child: List<Double>): List<IntRange> = child.mapIndexed { index, value ->
        val linear = value.coerceIn(0.0, 1.0)
        val encoded = if (index == 3) linear else if (linear <= .0031308) 12.92 * linear
            else 1.055 * linear.pow(1.0 / 2.4) - .055
        val code = encoded * 255.0
        floor(code).toInt().coerceIn(0, 255)..ceil(code).toInt().coerceIn(0, 255)
    }
}

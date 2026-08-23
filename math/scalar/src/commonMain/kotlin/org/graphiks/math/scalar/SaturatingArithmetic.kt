package org.graphiks.math.scalar

public fun saturatingAddI32(a: Int, b: Int): Int = clampToI32(a.toLong() + b.toLong())

public fun saturatingSubtractI32(a: Int, b: Int): Int = clampToI32(a.toLong() - b.toLong())

public fun saturatingNegateI32(value: Int): Int =
    if (value == Int.MIN_VALUE) Int.MAX_VALUE else -value

public fun saturatingMultiplyI32(a: Int, b: Int): Int = clampToI32(a.toLong() * b.toLong())

public fun saturatingAddI64(a: Long, b: Long): Long = when {
    b > 0L && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
    b < 0L && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
    else -> a + b
}

public fun saturatingSubtractI64(a: Long, b: Long): Long = when {
    b > 0L && a < Long.MIN_VALUE + b -> Long.MIN_VALUE
    b < 0L && a > Long.MAX_VALUE + b -> Long.MAX_VALUE
    else -> a - b
}

private fun clampToI32(value: Long): Int =
    value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

package org.skia.math

typealias MapPtsProc = (mat: SkMatrix, dst: Array<SkPoint>, src: Array<SkPoint>, count: Int) -> Unit

fun skIsPow2(value: Int): Boolean {
    return (value and (value - 1)) == 0
}

fun skIsPow2(value: Long): Boolean {
    return (value and (value - 1)) == 0L
}
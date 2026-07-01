package org.graphiks.kanvas.paint

sealed interface Blender {
    data class Mode(val mode: BlendMode) : Blender
    data class Arithmetic(val k1: Float, val k2: Float, val k3: Float, val k4: Float) : Blender
}

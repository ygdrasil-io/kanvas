package org.graphiks.kanvas.canvas

interface DisplayListBuffer {
    fun append(op: DisplayOp)
    fun ops(): List<DisplayOp>
}

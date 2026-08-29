package org.graphiks.kanvas.gpu.renderer.payloads

/**
 * Phase values covered by the exact horizontal [8,4] dash proof.
 *
 * The native expansion already carries the phase through device-space
 * geometry; keeping the accepted set small makes the pixel contract explicit
 * while leaving arbitrary phase and pattern combinations refused.
 */
fun isBoundedHorizontalDashedPhase(phase: Float): Boolean = phase == 0f || phase == 4f

package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor

object GPUBlendShaderLowering {
    private fun isSupported(child: GPUMaterialDescriptor): Boolean = when (child) {
        is GPUMaterialDescriptor.LinearGradient -> true
        is GPUMaterialDescriptor.RadialGradient -> true
        is GPUMaterialDescriptor.SweepGradient -> true
        is GPUMaterialDescriptor.SolidColor -> true
        is GPUMaterialDescriptor.ImageDraw -> child.rgbaPixels.isNotEmpty()
        else -> false
    }

    fun canHandle(descriptor: GPUMaterialDescriptor.BlendShader): Boolean =
        isSupported(descriptor.dst) && isSupported(descriptor.src)
}

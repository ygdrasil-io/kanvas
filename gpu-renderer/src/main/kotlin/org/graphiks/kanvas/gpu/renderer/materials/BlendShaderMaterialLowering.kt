package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor

object GPUBlendShaderLowering {
    fun canHandle(descriptor: GPUMaterialDescriptor.BlendShader): Boolean {
        val dstOk = descriptor.dst is GPUMaterialDescriptor.LinearGradient ||
                    descriptor.dst is GPUMaterialDescriptor.SolidColor
        val srcOk = descriptor.src is GPUMaterialDescriptor.LinearGradient ||
                    descriptor.src is GPUMaterialDescriptor.SolidColor
        return dstOk && srcOk
    }
}

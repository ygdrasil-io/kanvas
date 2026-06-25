package org.skia.gpu.webgpu

import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureDescriptor

/**
 * Headless render-to-texture surrogate built directly on the device.
 *
 * @deprecated gpu-raster is frozen at M30. Use the Kanvas-native pipeline
 * via [org.skia.kanvas.SkiaKanvasSurface] instead.
 * gpu-raster will be removed in M31+.
 * Owns:
 *  - the color texture (used by callers as render-pass attachment)
 *  - a staging buffer sized for one full readback at WebGPU's
 *    `bytesPerRow = align(width*4, 256)` alignment
 *
 * We don't use the toolkit's [io.ygdrasil.webgpu.TextureRenderingContext]
 * because its `init` block hard-codes the texture at 256×256 instead
 * of using its constructor `width`/`height` parameters — a known bug
 * in wgpu4k-toolkit 0.2.0-SNAPSHOT.
 *
 * G0 sticks to a single texel size — 4 bytes — because the only
 * format we exercise is `RGBA8Unorm`. Phase G6 will introduce
 * `RGBA16Float`; the bytes-per-pixel and the de-padding loop both
 * generalize then.
 */
@Deprecated(
    message = "gpu-raster is frozen at M30; use SkiaKanvasBridge instead. " +
        "The Kanvas-native pipeline is the default since M30. " +
        "Set -Dkanvas.rollback.legacy-gpu-raster=true for emergency rollback. " +
        "gpu-raster will be removed in M31+.",
    replaceWith = ReplaceWith(
        expression = "SkiaKanvasSurface.wrap(surface)",
        imports = ["org.skia.kanvas.SkiaKanvasSurface"],
    ),
)
public class HeadlessTarget(
    public val context: WebGpuContext,
    public val width: Int,
    public val height: Int,
    public val format: GPUTextureFormat = GPUTextureFormat.RGBA8Unorm,
) : AutoCloseable {

    private val bytesPerPixel: Int = when (format) {
        GPUTextureFormat.RGBA8Unorm, GPUTextureFormat.RGBA8UnormSrgb,
        GPUTextureFormat.BGRA8Unorm, GPUTextureFormat.BGRA8UnormSrgb -> 4
        else -> error("HeadlessTarget format $format is not supported in G0")
    }

    private val unpaddedBytesPerRow: Int = width * bytesPerPixel

    /**
     * WebGPU requires `bytesPerRow` in `copyTextureToBuffer` to be a
     * multiple of 256. We pad the buffer accordingly, then strip the
     * trailing slack per row in [readPixels].
     */
    private val paddedBytesPerRow: Int =
        ((unpaddedBytesPerRow + COPY_BYTES_PER_ROW_ALIGNMENT - 1) /
            COPY_BYTES_PER_ROW_ALIGNMENT) * COPY_BYTES_PER_ROW_ALIGNMENT

    private val stagingSize: ULong = (paddedBytesPerRow.toLong() * height.toLong()).toULong()

    public val colorTexture: GPUTexture = context.device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = width.toUInt(), height = height.toUInt()),
            format = format,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
            label = "HeadlessTarget.color",
        ),
    )

    private val stagingBuffer: GPUBuffer = context.device.createBuffer(
        BufferDescriptor(
            size = stagingSize,
            usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
            mappedAtCreation = false,
            label = "HeadlessTarget.staging",
        ),
    )

    /**
     * Encode a `copyTextureToBuffer` call onto [encoder] so that after
     * the encoder is submitted, [readPixels] can map and decode the
     * pixels. Caller is responsible for submission.
     */
    public fun encodeCopyToStaging(encoder: GPUCommandEncoder) {
        encoder.copyTextureToBuffer(
            source = TexelCopyTextureInfo(texture = colorTexture),
            destination = TexelCopyBufferInfo(
                buffer = stagingBuffer,
                offset = 0uL,
                bytesPerRow = paddedBytesPerRow.toUInt(),
                rowsPerImage = height.toUInt(),
            ),
            copySize = Extent3D(width = width.toUInt(), height = height.toUInt()),
        )
    }

    /**
     * Map the staging buffer, copy out, unmap, and return RGBA pixels
     * in row-major order — `width*height*4` bytes — padding stripped.
     */
    public suspend fun readPixels(): ByteArray {
        stagingBuffer.mapAsync(GPUMapMode.Read, 0uL, stagingSize).getOrThrow()
        val mapped = stagingBuffer.getMappedRange(0uL, stagingSize).toByteArray()
        stagingBuffer.unmap()
        if (paddedBytesPerRow == unpaddedBytesPerRow) return mapped
        val out = ByteArray(unpaddedBytesPerRow * height)
        for (row in 0 until height) {
            System.arraycopy(
                /* src      = */ mapped,
                /* srcPos   = */ row * paddedBytesPerRow,
                /* dest     = */ out,
                /* destPos  = */ row * unpaddedBytesPerRow,
                /* length   = */ unpaddedBytesPerRow,
            )
        }
        return out
    }

    override fun close() {
        stagingBuffer.close()
        colorTexture.close()
    }

    private companion object {
        const val COPY_BYTES_PER_ROW_ALIGNMENT: Int = 256
    }
}

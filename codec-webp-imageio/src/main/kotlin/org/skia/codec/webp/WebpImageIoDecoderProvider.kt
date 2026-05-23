package org.skia.codec.webp

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec

public class WebpImageIoDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkWebpCodec.Decoder)
}

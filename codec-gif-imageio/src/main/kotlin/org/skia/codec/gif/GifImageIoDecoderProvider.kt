package org.skia.codec.gif

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec

public class GifImageIoDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkGifCodec.Decoder)
}

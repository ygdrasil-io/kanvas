package org.skia.codec

public class IcoKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkIcoDecoder.RegistryEntry)
}

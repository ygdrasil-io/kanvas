package org.graphiks.kanvas.codec

public class IcoKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkIcoDecoder.RegistryEntry)
}

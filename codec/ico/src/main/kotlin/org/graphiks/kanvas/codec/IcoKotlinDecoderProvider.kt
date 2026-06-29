package org.graphiks.kanvas.codec

public class IcoKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(IcoDecoder.RegistryEntry)
}

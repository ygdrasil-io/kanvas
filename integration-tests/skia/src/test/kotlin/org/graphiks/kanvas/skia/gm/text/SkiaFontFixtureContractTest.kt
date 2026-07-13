package org.graphiks.kanvas.skia.gm.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.security.MessageDigest

class SkiaFontFixtureContractTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `uses the verified upstream Skia fixture`(path: String, expectedSha256: String) {
        val bytes = javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() }

        assertNotNull(bytes, "Missing verified Skia font fixture: $path")
        assertEquals(expectedSha256, sha256(requireNotNull(bytes)))
    }

    private companion object {
        @JvmStatic
        fun fixtures(): List<Array<String>> = listOf(
            arrayOf("fonts/Stroking.ttf", "c5566e2adf1bf087048acf28d239c7988ebcdc339208b29993e70f307c467090"),
            arrayOf("fonts/Stroking.otf", "55c8502113cc315eb4b0f1a843ae7d6cfff13c6688b4914403fad1e46a3c2973"),
            arrayOf("fonts/Variable.ttf", "d9437a0f2ecef4fca3eb1567d21cd8b21c557ac23e47da8642105f14c94519d9"),
            arrayOf("fonts/hintgasp.ttf", "a30be3842192f8d76fd8009e5fdbfe4bbcee3fd014211bec151d665c8952875b"),
        )

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}

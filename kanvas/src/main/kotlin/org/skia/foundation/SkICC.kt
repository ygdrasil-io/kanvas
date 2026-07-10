package org.skia.foundation

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.icc.IccProfileWriter
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import java.nio.ByteBuffer

public object SkICC {
    @Suppress("UNUSED_PARAMETER", "FunctionName")
    public fun Make(profile: ByteBuffer, size: Long): SkICC? = null

    @Suppress("FunctionName")
    public fun WriteToICC(
        transferFn: SkcmsTransferFunction,
        matrix: SkcmsMatrix3x3,
    ): ByteArray = IccProfileWriter.writeMatrixTrc(
        ColorProfile(
            colorModel = ColorModel.RGB,
            toXyzD50 = matrix,
            transferFunction = transferFn,
        ),
    )
}

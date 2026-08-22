package org.skia.foundation

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.icc.IccProfileWriter
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.matrix.Matrix3x3F32
import java.nio.ByteBuffer

public object SkICC {
    @Suppress("UNUSED_PARAMETER", "FunctionName")
    public fun Make(profile: ByteBuffer, size: Long): SkICC? = null

    @Suppress("FunctionName")
    public fun WriteToICC(
        transferFn: ColorTransferFunction.Parametric,
        matrix: Matrix3x3F32,
    ): ByteArray = IccProfileWriter.writeMatrixTrc(
        ColorProfile(
            colorModel = ColorModel.RGB,
            toXyzD50 = matrix,
            transferFunction = transferFn,
        ),
    )
}

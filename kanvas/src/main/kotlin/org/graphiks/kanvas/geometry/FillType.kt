package org.graphiks.kanvas.geometry

import org.graphiks.math.geometry.FillRule

enum class FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }

internal fun FillType.toFillRule(): FillRule = when (this) {
    FillType.WINDING -> FillRule.WINDING
    FillType.EVEN_ODD -> FillRule.EVEN_ODD
    FillType.INVERSE_WINDING -> FillRule.INVERSE_WINDING
    FillType.INVERSE_EVEN_ODD -> FillRule.INVERSE_EVEN_ODD
}

internal fun FillRule.toCompatibilityFillType(): FillType = when (this) {
    FillRule.WINDING -> FillType.WINDING
    FillRule.EVEN_ODD -> FillType.EVEN_ODD
    FillRule.INVERSE_WINDING -> FillType.INVERSE_WINDING
    FillRule.INVERSE_EVEN_ODD -> FillType.INVERSE_EVEN_ODD
}

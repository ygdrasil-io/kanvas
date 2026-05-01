package org.skia.foundation

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import org.skia.math.SkScalar

public fun skPreMultiplyARGB(
  a: U8CPU,
  r: U8CPU,
  g: U8CPU,
  b: U8CPU,
): SkPMColor {
  TODO("Implement skPreMultiplyARGB")
}

public fun skPreMultiplyColor(c: SkColor): SkPMColor {
  TODO("Implement skPreMultiplyColor")
}

public fun skPMColorSetARGB(
  a: UByte,
  r: UByte,
  g: UByte,
  b: UByte,
): SkPMColor {
  TODO("Implement skPMColorSetARGB")
}

public fun skPMColorGetA(c: SkPMColor): SkAlpha {
  TODO("Implement skPMColorGetA")
}

public fun skPMColorGetR(c: SkPMColor): UByte {
  TODO("Implement skPMColorGetR")
}

public fun skPMColorGetG(c: SkPMColor): UByte {
  TODO("Implement skPMColorGetG")
}

public fun skPMColorGetB(c: SkPMColor): UByte {
  TODO("Implement skPMColorGetB")
}

public fun byteToScalar(x: U8CPU): SkScalar {
  TODO("Implement byteToScalar")
}

public fun byteDivToScalar(numer: Int, denom: U8CPU): SkScalar {
  TODO("Implement byteDivToScalar")
}

public fun skRGBToHSV(
  r: U8CPU,
  g: U8CPU,
  b: U8CPU,
  hsv: Array<SkScalar>,
) {
  TODO("Implement skRGBToHSV")
}

public fun toSkColor() {
  TODO("Implement toSkColor")
}

public fun toBytesRGBA() {
  TODO("Implement toBytesRGBA")
}

public fun fromBytesRGBA(c: UInt) {
  TODO("Implement fromBytesRGBA")
}

public fun skAlphaTypeIsOpaque(at: SkAlphaType): Boolean {
  TODO("Implement skAlphaTypeIsOpaque")
}

public fun skColorSetARGB(
  a: U8CPU,
  r: U8CPU,
  g: U8CPU,
  b: U8CPU,
): SkColor {
  TODO("Implement skColorSetARGB")
}

public fun skColorSetA(c: SkColor, a: U8CPU): SkColor {
  TODO("Implement skColorSetA")
}

public fun skColorToHSV(color: SkColor, hsv: Array<SkScalar>) {
  TODO("Implement skColorToHSV")
}

public fun skHSVToColor(hsv: Array<SkScalar>): SkColor {
  TODO("Implement skHSVToColor")
}

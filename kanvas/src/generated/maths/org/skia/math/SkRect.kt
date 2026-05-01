package org.skia.math

import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSpan

public open class SkRect public constructor(
  public var fLeft: Float,
  public var fTop: Float,
  public var fRight: Float,
  public var fBottom: Float,
) {
  public fun isEmpty(): Boolean {
    return !(left() < right() && top() < bottom())
  }

  public fun isSorted(): Boolean {
    return fLeft <= fRight && fTop <= fBottom
  }

  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  public fun x(): Float {
    return fLeft
  }

  public fun y(): Float {
    return y()
  }

  public fun left(): Float {
    return this.left()
  }

  public fun top(): Float {
    return top()
  }

  public fun right(): Float {
    return right()
  }

  public fun bottom(): Float {
    return fBottom
  }

  public fun width(): Float {
    return fRight - fLeft
  }

  public fun height(): Float {
    return bottom() - top()
  }

  public fun centerX(): Float {
    TODO("Implement centerX")
  }

  public fun centerY(): Float {
    TODO("Implement centerY")
  }

  public fun center(): Int {
    TODO("Implement center")
  }

  public fun tl(): Int {
    TODO("Implement tl")
  }

  public fun tr(): Int {
    TODO("Implement tr")
  }

  public fun bl(): Int {
    TODO("Implement bl")
  }

  public fun br(): Int {
    TODO("Implement br")
  }

  public fun toQuad(dir: SkPathDirection = TODO()): Int {
    TODO("Implement toQuad")
  }

  public fun copyToQuad(pts: SkSpan<SkPoint>, dir: SkPathDirection = TODO()) {
    TODO("Implement copyToQuad")
  }

  public fun toQuad(quad: Array<SkPoint>) {
    TODO("Implement toQuad")
  }

  public fun setEmpty() {
    TODO("Implement setEmpty")
  }

  public fun `set`(src: SkIRect) {
    TODO("Implement set")
  }

  public fun setLTRB(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
  ) {
    TODO("Implement setLTRB")
  }

  public fun setBounds(pts: SkSpan<SkPoint>) {
    TODO("Implement setBounds")
  }

  public fun setBoundsCheck(pts: SkSpan<SkPoint>): Boolean {
    TODO("Implement setBoundsCheck")
  }

  public fun setBoundsNoCheck(pts: SkSpan<SkPoint>) {
    TODO("Implement setBoundsNoCheck")
  }

  public fun `set`(p0: SkPoint, p1: SkPoint) {
    TODO("Implement set")
  }

  public fun setXYWH(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
  ) {
    TODO("Implement setXYWH")
  }

  public fun setWH(width: Float, height: Float) {
    TODO("Implement setWH")
  }

  public fun setIWH(width: Int, height: Int) {
    TODO("Implement setIWH")
  }

  public fun makeOffset(dx: Float, dy: Float): SkRect {
    TODO("Implement makeOffset")
  }

  public fun makeOffset(v: SkVector): SkRect {
    TODO("Implement makeOffset")
  }

  public fun makeInset(dx: Float, dy: Float): SkRect {
    TODO("Implement makeInset")
  }

  public fun makeOutset(dx: Float, dy: Float): SkRect {
    TODO("Implement makeOutset")
  }

  public fun offset(dx: Float, dy: Float) {
    TODO("Implement offset")
  }

  public fun offset(delta: SkPoint) {
    TODO("Implement offset")
  }

  public fun offsetTo(newX: Float, newY: Float) {
    TODO("Implement offsetTo")
  }

  public fun inset(dx: Float, dy: Float) {
    TODO("Implement inset")
  }

  public fun outset(dx: Float, dy: Float) {
    TODO("Implement outset")
  }

  public fun intersect(r: SkRect): Boolean {
    TODO("Implement intersect")
  }

  public fun intersect(a: SkRect, b: SkRect): Boolean {
    TODO("Implement intersect")
  }

  public fun intersects(r: SkRect): Boolean {
    TODO("Implement intersects")
  }

  public fun join(r: SkRect) {
    TODO("Implement join")
  }

  public fun joinNonEmptyArg(r: SkRect) {
    TODO("Implement joinNonEmptyArg")
  }

  public fun joinPossiblyEmptyRect(r: SkRect) {
    TODO("Implement joinPossiblyEmptyRect")
  }

  public fun contains(x: Float, y: Float): Boolean {
    TODO("Implement contains")
  }

  public fun contains(r: SkRect): Boolean {
    TODO("Implement contains")
  }

  public fun contains(r: SkIRect): Boolean {
    TODO("Implement contains")
  }

  public fun round(dst: SkIRect?) {
    TODO("Implement round")
  }

  public fun roundOut(dst: SkIRect?) {
    TODO("Implement roundOut")
  }

  public fun roundOut(dst: SkRect?) {
    TODO("Implement roundOut")
  }

  public fun roundIn(dst: SkIRect?) {
    TODO("Implement roundIn")
  }

  public fun round(): SkIRect {
    TODO("Implement round")
  }

  public fun roundOut(): SkIRect {
    TODO("Implement roundOut")
  }

  public fun roundIn(): SkIRect {
    TODO("Implement roundIn")
  }

  public fun sort() {
    TODO("Implement sort")
  }

  public fun makeSorted(): SkRect {
    TODO("Implement makeSorted")
  }

  public fun asScalars(): Float {
    TODO("Implement asScalars")
  }

  public fun dump(asHex: Boolean) {
    TODO("Implement dump")
  }

  public fun dumpToString(asHex: Boolean): String {
    TODO("Implement dumpToString")
  }

  public fun dump() {
    TODO("Implement dump")
  }

  public fun dumpHex() {
    TODO("Implement dumpHex")
  }

  public companion object {
    public fun makeEmpty(): SkRect {
      TODO("Implement makeEmpty")
    }

    public fun makeWH(w: Float, h: Float): SkRect {
      TODO("Implement makeWH")
    }

    public fun makeIWH(w: Int, h: Int): SkRect {
      TODO("Implement makeIWH")
    }

    public fun makeSize(size: SkSize): SkRect {
      TODO("Implement makeSize")
    }

    public fun makeLTRB(
      l: Float,
      t: Float,
      r: Float,
      b: Float,
    ): SkRect {
      TODO("Implement makeLTRB")
    }

    public fun makeXYWH(
      x: Float,
      y: Float,
      w: Float,
      h: Float,
    ): SkRect {
      TODO("Implement makeXYWH")
    }

    public fun make(size: SkISize): SkRect {
      TODO("Implement make")
    }

    public fun make(irect: SkIRect): SkRect {
      TODO("Implement make")
    }

    public fun bounds(pts: SkSpan<SkPoint>): SkRect? {
      TODO("Implement bounds")
    }

    public fun boundsOrEmpty(pts: SkSpan<SkPoint>): SkRect {
      TODO("Implement boundsOrEmpty")
    }

    private fun intersects(
      al: Float,
      at: Float,
      ar: Float,
      ab: Float,
      bl: Float,
      bt: Float,
      br: Float,
      bb: Float,
    ): Boolean {
      TODO("Implement intersects")
    }

    public fun intersects(a: SkRect, b: SkRect): Boolean {
      TODO("Implement intersects")
    }
  }
}

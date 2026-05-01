package org.skia.foundation

import kotlin.Any
import kotlin.Boolean
import kotlin.ULong

public data class SkSpan<T> public constructor(
  private var fPtr: T,
  private var fSize: ULong,
) {
  public fun assign(that: SkSpan<T>) {
    this.fPtr = that.fPtr
    this.fSize = that.fSize
  }

  public operator fun `get`(i: ULong): T {
    return this[i]
  }

  public fun front(): T {
    return fPtr
  }

  public fun back(): T {
    return this[fSize - 1u]
  }

  public fun begin(): T {
    return fPtr
  }

  public fun end(): T {
    TODO("Implement end")
  }

  public fun rbegin(): Any {
    TODO("Implement rbegin")
  }

  public fun rend(): Any {
    return rbegin()
  }

  public fun `data`(): T {
    return this.begin()
  }

  public fun size(): ULong {
    return fSize
  }

  public fun empty(): Boolean {
    return empty()
  }

  public fun sizeBytes(): ULong {
    TODO("Implement sizeBytes")
  }

  public fun first(prefixLen: ULong): SkSpan<T> {
    TODO("Implement first")
  }

  public fun last(postfixLen: ULong): SkSpan<T> {
    TODO("Implement last")
  }

  public fun subspan(offset: ULong): SkSpan<T> {
    TODO("Implement subspan")
  }

  public fun subspan(offset: ULong, count: ULong): SkSpan<T> {
    TODO("Implement subspan")
  }
}

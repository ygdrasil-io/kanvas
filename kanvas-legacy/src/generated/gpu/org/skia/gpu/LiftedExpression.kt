package org.skia.gpu

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct LiftedExpression {
 *     // The node who's expression should be lifted.
 *     const ShaderNode* fNode;
 *     // The arguments to use as input to the lifted expression.
 *     ShaderSnippet::Args fArgs;
 *     // If true, capture the expression's resolved value in a varying.
 *     // This is false for expressions whose output is only used in other lifted expressions.
 *     bool fEmitVarying = true;
 * }
 * ```
 */
public data class LiftedExpression public constructor(
  /**
   * C++ original:
   * ```cpp
   * const ShaderNode* fNode
   * ```
   */
  public val fNode: ShaderNode?,
  /**
   * C++ original:
   * ```cpp
   * ShaderSnippet::Args fArgs
   * ```
   */
  public var fArgs: ShaderSnippet.Args,
  /**
   * C++ original:
   * ```cpp
   * bool fEmitVarying = true
   * ```
   */
  public var fEmitVarying: Boolean,
)

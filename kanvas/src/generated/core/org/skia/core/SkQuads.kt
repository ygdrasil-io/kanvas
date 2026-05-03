package org.skia.core

import kotlin.Double
import kotlin.DoubleArray
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkQuads {
 * public:
 *     /**
 *      * Calculate a very accurate discriminant.
 *      * Given
 *      *    A*t^2 -2*B*t + C = 0,
 *      * calculate
 *      *    B^2 - AC
 *      * accurate to 2 bits.
 *      * Note the form of the quadratic is slightly different from the normal formulation.
 *      *
 *      * The method used to calculate the discriminant is from
 *      *    "On the Cost of Floating-Point Computation Without Extra-Precise Arithmetic"
 *      * by W. Kahan.
 *      */
 *     static double Discriminant(double A, double B, double C);
 *
 *     struct RootResult {
 *         double discriminant;
 *         double root0;
 *         double root1;
 *     };
 *
 *     /**
 *      * Calculate the roots of a quadratic.
 *      * Given
 *      *    A*t^2 -2*B*t + C = 0,
 *      * calculate the roots.
 *      *
 *      * This does not try to detect a linear configuration of the equation, or detect if the two
 *      * roots are the same. It returns the discriminant and the two roots.
 *      *
 *      * Not this uses a different form the quadratic equation to reduce rounding error. Give
 *      * standard A, B, C. You can call this root finder with:
 *      *    Roots(A, -0.5*B, C)
 *      * to find the roots of A*x^2 + B*x + C.
 *      *
 *      * The method used to calculate the roots is from
 *      *    "On the Cost of Floating-Point Computation Without Extra-Precise Arithmetic"
 *      * by W. Kahan.
 *      *
 *      * If the roots are imaginary then nan is returned.
 *      * If the roots can't be represented as double then inf is returned.
 *      */
 *     static RootResult Roots(double A, double B, double C);
 *
 *     /**
 *      * Puts up to 2 real solutions to the equation
 *      *   A*t^2 + B*t + C = 0
 *      * in the provided array.
 *      */
 *     static int RootsReal(double A, double B, double C, double solution[2]);
 *
 *     /**
 *      * Evaluates the quadratic function with the 3 provided coefficients and the
 *      * provided variable.
 *      */
 *     static double EvalAt(double A, double B, double C, double t);
 * }
 * ```
 */
public open class SkQuads {
  public data class RootResult public constructor(
    public var discriminant: Double,
    public var root0: Double,
    public var root1: Double,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * double SkQuads::Discriminant(const double a, const double b, const double c) {
     *     const double b2 = b * b;
     *     const double ac = a * c;
     *
     *     // Calculate the rough discriminate which may suffer from a loss in precision due to b2 and
     *     // ac being too close.
     *     const double roughDiscriminant = b2 - ac;
     *
     *     // We would like the calculated discriminant to have a relative error of 2-bits or less. For
     *     // doubles, this means the relative error is <= E = 3*2^-53. This gives a relative error
     *     // bounds of:
     *     //
     *     //     |D - D~| / |D| <= E,
     *     //
     *     // where D = B*B - AC, and D~ is the floating point approximation of D.
     *     // Define the following equations
     *     //     B2 = B*B,
     *     //     B2~ = B2(1 + eB2), where eB2 is the floating point round off,
     *     //     AC = A*C,
     *     //     AC~ = AC(1 + eAC), where eAC is the floating point round off, and
     *     //     D~ = B2~ - AC~.
     *     //  We can now rewrite the above bounds as
     *     //
     *     //     |B2 - AC - (B2~ - AC~)| / |B2 - AC| = |B2 - AC - B2~ + AC~| / |B2 - AC| <= E.
     *     //
     *     //  Substituting B2~ and AC~, and canceling terms gives
     *     //
     *     //     |eAC * AC - eB2 * B2| / |B2 - AC| <= max(|eAC|, |eBC|) * (|AC| + |B2|) / |B2 - AC|.
     *     //
     *     //  We know that B2 is always positive, if AC is negative, then there is no cancellation
     *     //  problem, and max(|eAC|, |eBC|) <= 2^-53, thus
     *     //
     *     //     2^-53 * (AC + B2) / |B2 - AC| <= 3 * 2^-53. Leading to
     *     //     AC + B2 <= 3 * |B2 - AC|.
     *     //
     *     // If 3 * |B2 - AC| >= AC + B2 holds, then the roughDiscriminant has 2-bits of rounding error
     *     // or less and can be used.
     *     if (3 * std::abs(roughDiscriminant) >= b2 + ac) {
     *         return roughDiscriminant;
     *     }
     *
     *     // Use the extra internal precision afforded by fma to calculate the rounding error for
     *     // b^2 and ac.
     *     const double b2RoundingError = std::fma(b, b, -b2);
     *     const double acRoundingError = std::fma(a, c, -ac);
     *
     *     // Add the total rounding error back into the discriminant guess.
     *     const double discriminant = (b2 - ac) + (b2RoundingError - acRoundingError);
     *     return discriminant;
     * }
     * ```
     */
    public fun discriminant(
      a: Double,
      b: Double,
      c: Double,
    ): Double {
      TODO("Implement discriminant")
    }

    /**
     * C++ original:
     * ```cpp
     * SkQuads::RootResult SkQuads::Roots(double A, double B, double C) {
     *     const double discriminant = Discriminant(A, B, C);
     *
     *     if (A == 0) {
     *         double root;
     *         if (B == 0) {
     *             if (C == 0) {
     *                 root = std::numeric_limits<double>::infinity();
     *             } else {
     *                 root = std::numeric_limits<double>::quiet_NaN();
     *             }
     *         } else {
     *             // Solve -2*B*x + C == 0; x = c/(2*b).
     *             root = C / (2 * B);
     *         }
     *         return {discriminant, root, root};
     *     }
     *
     *     SkASSERT(A != 0);
     *     if (discriminant == 0) {
     *         return {discriminant, B / A, B / A};
     *     }
     *
     *     if (discriminant > 0) {
     *         const double D = sqrt(discriminant);
     *         const double R = B > 0 ? B + D : B - D;
     *         return {discriminant, R / A, C / R};
     *     }
     *
     *     // The discriminant is negative or is not finite.
     *     return {discriminant, NAN, NAN};
     * }
     * ```
     */
    public fun roots(
      a: Double,
      b: Double,
      c: Double,
    ): RootResult {
      TODO("Implement roots")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkQuads::RootsReal(const double A, const double B, const double C, double solution[2]) {
     *
     *     if (close_to_linear(A, B)) {
     *         return solve_linear(B, C, solution);
     *     }
     *
     *     SkASSERT(A != 0);
     *     auto [discriminant, root0, root1] = Roots(A, -0.5 * B, C);
     *
     *     // Handle invariants to mesh with existing code from here on.
     *     if (!std::isfinite(discriminant) || discriminant < 0) {
     *         return 0;
     *     }
     *
     *     int roots = 0;
     *     if (const double r0 = zero_if_tiny(root0); std::isfinite(r0)) {
     *         solution[roots++] = r0;
     *     }
     *     if (const double r1 = zero_if_tiny(root1); std::isfinite(r1)) {
     *         solution[roots++] = r1;
     *     }
     *
     *     if (roots == 2 && sk_doubles_nearly_equal_ulps(solution[0], solution[1])) {
     *         roots = 1;
     *     }
     *
     *     return roots;
     * }
     * ```
     */
    public fun rootsReal(
      a: Double,
      b: Double,
      c: Double,
      solution: DoubleArray,
    ): Int {
      TODO("Implement rootsReal")
    }

    /**
     * C++ original:
     * ```cpp
     * double SkQuads::EvalAt(double A, double B, double C, double t) {
     *     // Use fused-multiply-add to reduce the amount of round-off error between terms.
     *     return std::fma(std::fma(A, t, B), t, C);
     * }
     * ```
     */
    public fun evalAt(
      a: Double,
      b: Double,
      c: Double,
      t: Double,
    ): Double {
      TODO("Implement evalAt")
    }
  }
}

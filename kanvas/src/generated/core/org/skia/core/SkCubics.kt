package org.skia.core

import kotlin.Double
import kotlin.DoubleArray
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkCubics {
 * public:
 *     /**
 *      * Puts up to 3 real solutions to the equation
 *      *   A*t^3 + B*t^2 + C*t + d = 0
 *      * in the provided array and returns how many roots that was.
 *      */
 *     static int RootsReal(double A, double B, double C, double D,
 *                          double solution[3]);
 *
 *     /**
 *      * Puts up to 3 real solutions to the equation
 *      *   A*t^3 + B*t^2 + C*t + D = 0
 *      * in the provided array, with the constraint that t is in the range [0.0, 1.0],
 *      * and returns how many roots that was.
 *      */
 *     static int RootsValidT(double A, double B, double C, double D,
 *                            double solution[3]);
 *
 *
 *     /**
 *      * Puts up to 3 real solutions to the equation
 *      *   A*t^3 + B*t^2 + C*t + D = 0
 *      * in the provided array, with the constraint that t is in the range [0.0, 1.0],
 *      * and returns how many roots that was.
 *      * This is a slower method than RootsValidT, but more accurate in circumstances
 *      * where floating point error gets too big.
 *      */
 *     static int BinarySearchRootsValidT(double A, double B, double C, double D,
 *                                        double solution[3]);
 *
 *     /**
 *      * Evaluates the cubic function with the 4 provided coefficients and the
 *      * provided variable.
 *      */
 *     static double EvalAt(double A, double B, double C, double D, double t) {
 *         return std::fma(t, std::fma(t, std::fma(t, A, B), C), D);
 *     }
 *
 *     static double EvalAt(double coefficients[4], double t) {
 *         return EvalAt(coefficients[0], coefficients[1], coefficients[2], coefficients[3], t);
 *     }
 * }
 * ```
 */
public open class SkCubics {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * int SkCubics::RootsReal(double A, double B, double C, double D, double solution[3]) {
     *     if (close_to_a_quadratic(A, B)) {
     *         return SkQuads::RootsReal(B, C, D, solution);
     *     }
     *     if (sk_double_nearly_zero(D)) {  // 0 is one root
     *         int num = SkQuads::RootsReal(A, B, C, solution);
     *         for (int i = 0; i < num; ++i) {
     *             if (sk_double_nearly_zero(solution[i])) {
     *                 return num;
     *             }
     *         }
     *         solution[num++] = 0;
     *         return num;
     *     }
     *     if (sk_double_nearly_zero(A + B + C + D)) {  // 1 is one root
     *         int num = SkQuads::RootsReal(A, A + B, -D, solution);
     *         for (int i = 0; i < num; ++i) {
     *             if (sk_doubles_nearly_equal_ulps(solution[i], 1)) {
     *                 return num;
     *             }
     *         }
     *         solution[num++] = 1;
     *         return num;
     *     }
     *     double a, b, c;
     *     {
     *         // If A is zero (e.g. B was nan and thus close_to_a_quadratic was false), we will
     *         // temporarily have infinities rolling about, but will catch that when checking
     *         // R2MinusQ3.
     *         double invA = sk_ieee_double_divide(1, A);
     *         a = B * invA;
     *         b = C * invA;
     *         c = D * invA;
     *     }
     *     double a2 = a * a;
     *     double Q = (a2 - b * 3) / 9;
     *     double R = (2 * a2 * a - 9 * a * b + 27 * c) / 54;
     *     double R2 = R * R;
     *     double Q3 = Q * Q * Q;
     *     double R2MinusQ3 = R2 - Q3;
     *     // If one of R2 Q3 is infinite or nan, subtracting them will also be infinite/nan.
     *     // If both are infinite or nan, the subtraction will be nan.
     *     // In either case, we have no finite roots.
     *     if (!SkIsFinite(R2MinusQ3)) {
     *         return 0;
     *     }
     *     double adiv3 = a / 3;
     *     double r;
     *     double* roots = solution;
     *     if (R2MinusQ3 < 0) {   // we have 3 real roots
     *         // the divide/root can, due to finite precisions, be slightly outside of -1...1
     *         const double theta = acos(SkTPin(R / std::sqrt(Q3), -1., 1.));
     *         const double neg2RootQ = -2 * std::sqrt(Q);
     *
     *         r = neg2RootQ * cos(theta / 3) - adiv3;
     *         *roots++ = r;
     *
     *         r = neg2RootQ * cos((theta + 2 * SK_DoublePI) / 3) - adiv3;
     *         if (!nearly_equal(solution[0], r)) {
     *             *roots++ = r;
     *         }
     *         r = neg2RootQ * cos((theta - 2 * SK_DoublePI) / 3) - adiv3;
     *         if (!nearly_equal(solution[0], r) &&
     *             (roots - solution == 1 || !nearly_equal(solution[1], r))) {
     *             *roots++ = r;
     *         }
     *     } else {  // we have 1 real root
     *         const double sqrtR2MinusQ3 = std::sqrt(R2MinusQ3);
     *         A = fabs(R) + sqrtR2MinusQ3;
     *         A = std::cbrt(A); // cube root
     *         if (R > 0) {
     *             A = -A;
     *         }
     *         if (!sk_double_nearly_zero(A)) {
     *             A += Q / A;
     *         }
     *         r = A - adiv3;
     *         *roots++ = r;
     *         if (!sk_double_nearly_zero(R2) &&
     *              sk_doubles_nearly_equal_ulps(R2, Q3)) {
     *             r = -A / 2 - adiv3;
     *             if (!nearly_equal(solution[0], r)) {
     *                 *roots++ = r;
     *             }
     *         }
     *     }
     *     return static_cast<int>(roots - solution);
     * }
     * ```
     */
    public fun rootsReal(
      a: Double,
      b: Double,
      c: Double,
      d: Double,
      solution: DoubleArray,
    ): Int {
      TODO("Implement rootsReal")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkCubics::RootsValidT(double A, double B, double C, double D,
     *                           double solution[3]) {
     *     double allRoots[3] = {0, 0, 0};
     *     int realRoots = SkCubics::RootsReal(A, B, C, D, allRoots);
     *     int foundRoots = 0;
     *     for (int index = 0; index < realRoots; ++index) {
     *         double tValue = allRoots[index];
     *         if (tValue <= 1.00005 && (tValue >= 1.0 || sk_doubles_nearly_equal_ulps(tValue, 1.0))) {
     *             // Make sure we do not already have 1 (or something very close) in the list of roots.
     *             if ((foundRoots < 1 || !sk_doubles_nearly_equal_ulps(solution[0], 1)) &&
     *                 (foundRoots < 2 || !sk_doubles_nearly_equal_ulps(solution[1], 1))) {
     *                 solution[foundRoots++] = 1;
     *             }
     *         } else if (tValue >= -0.00005 && (tValue <= 0.0 || sk_double_nearly_zero(tValue))) {
     *             // Make sure we do not already have 0 (or something very close) in the list of roots.
     *             if ((foundRoots < 1 || !sk_double_nearly_zero(solution[0])) &&
     *                 (foundRoots < 2 || !sk_double_nearly_zero(solution[1]))) {
     *                 solution[foundRoots++] = 0;
     *             }
     *         } else if (tValue > 0.0 && tValue < 1.0) {
     *             solution[foundRoots++] = tValue;
     *         }
     *     }
     *     return foundRoots;
     * }
     * ```
     */
    public fun rootsValidT(
      a: Double,
      b: Double,
      c: Double,
      d: Double,
      solution: DoubleArray,
    ): Int {
      TODO("Implement rootsValidT")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkCubics::BinarySearchRootsValidT(double A, double B, double C, double D,
     *                                       double solution[3]) {
     *     if (!SkIsFinite(A, B, C, D)) {
     *         return 0;
     *     }
     *     double regions[4] = {0, 0, 0, 1};
     *     // Find local minima and maxima
     *     double minMax[2] = {0, 0};
     *     int extremaCount = find_extrema_valid_t(A, B, C, minMax);
     *     int startIndex = 2 - extremaCount;
     *     if (extremaCount == 1) {
     *         regions[startIndex + 1] = minMax[0];
     *     }
     *     if (extremaCount == 2) {
     *         // While the roots will be in the range 0 to 1 inclusive, they might not be sorted.
     *         regions[startIndex + 1] = std::min(minMax[0], minMax[1]);
     *         regions[startIndex + 2] = std::max(minMax[0], minMax[1]);
     *     }
     *     // Starting at regions[startIndex] and going up through regions[3], we have
     *     // an ascending list of numbers in the range 0 to 1.0, between which are the possible
     *     // locations of a root.
     *     int foundRoots = 0;
     *     for (;startIndex < 3; startIndex++) {
     *         double root = binary_search(A, B, C, D, regions[startIndex], regions[startIndex + 1]);
     *         if (root >= 0) {
     *             // Check for duplicates
     *             if ((foundRoots < 1 || !approximately_zero(solution[0] - root)) &&
     *                 (foundRoots < 2 || !approximately_zero(solution[1] - root))) {
     *                 solution[foundRoots++] = root;
     *             }
     *         }
     *     }
     *     return foundRoots;
     * }
     * ```
     */
    public fun binarySearchRootsValidT(
      a: Double,
      b: Double,
      c: Double,
      d: Double,
      solution: DoubleArray,
    ): Int {
      TODO("Implement binarySearchRootsValidT")
    }

    /**
     * C++ original:
     * ```cpp
     * static double EvalAt(double A, double B, double C, double D, double t) {
     *         return std::fma(t, std::fma(t, std::fma(t, A, B), C), D);
     *     }
     * ```
     */
    public fun evalAt(
      a: Double,
      b: Double,
      c: Double,
      d: Double,
      t: Double,
    ): Double {
      TODO("Implement evalAt")
    }

    /**
     * C++ original:
     * ```cpp
     * static double EvalAt(double coefficients[4], double t) {
     *         return EvalAt(coefficients[0], coefficients[1], coefficients[2], coefficients[3], t);
     *     }
     * ```
     */
    public fun evalAt(coefficients: DoubleArray, t: Double): Double {
      TODO("Implement evalAt")
    }
  }
}

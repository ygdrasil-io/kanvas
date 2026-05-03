package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * static struct {
 *     const char* fStr;
 *     const SkRect fBounds;
 * } gRec[] = {
 *     { "M1,1 l-2.58-2.828-3.82-0.113, 1.9-3.3223-1.08-3.6702, 3.75,0.7744,3.16-2.1551,"
 *        "0.42,3.8008,3.02,2.3384-3.48,1.574-1.29,3.601z",
 *         { -5.39999962f, -10.3142f, 5.77000046f, 1.f } },
 *     { "", { 0, 0, 0, 0 } },
 *     { "M0,0L10,10", { 0, 0, SkIntToScalar(10), SkIntToScalar(10) } },
 *     { "M-5.5,-0.5 Q 0 0 6,6.50",
 *         { -5.5f, -0.5f,
 *           6, 6.5f } }
 * }
 * ```
 */
public open class GRec

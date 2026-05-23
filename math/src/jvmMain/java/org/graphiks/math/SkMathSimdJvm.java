package org.graphiks.math;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public final class SkMathSimdJvm {
    private static final VectorSpecies<Float> F32X4 = FloatVector.SPECIES_128;
    private static final int[] ROW_0 = {0, 4, 8, 12};
    private static final int[] ROW_1 = {1, 5, 9, 13};
    private static final int[] ROW_2 = {2, 6, 10, 14};
    private static final int[] ROW_3 = {3, 7, 11, 15};
    private static final ThreadLocal<float[]> LEFT = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> RIGHT = ThreadLocal.withInitial(() -> new float[4]);

    private SkMathSimdJvm() {
    }

    public static float dot4(
            float ax,
            float ay,
            float az,
            float aw,
            float bx,
            float by,
            float bz,
            float bw
    ) {
        float[] left = LEFT.get();
        float[] right = RIGHT.get();
        left[0] = ax;
        left[1] = ay;
        left[2] = az;
        left[3] = aw;
        right[0] = bx;
        right[1] = by;
        right[2] = bz;
        right[3] = bw;

        return FloatVector.fromArray(F32X4, left, 0)
                .mul(FloatVector.fromArray(F32X4, right, 0))
                .reduceLanes(VectorOperators.ADD);
    }

    public static void m44Concat(float[] a, float[] b, float[] out) {
        FloatVector row0 = FloatVector.fromArray(F32X4, a, 0, ROW_0, 0);
        FloatVector row1 = FloatVector.fromArray(F32X4, a, 0, ROW_1, 0);
        FloatVector row2 = FloatVector.fromArray(F32X4, a, 0, ROW_2, 0);
        FloatVector row3 = FloatVector.fromArray(F32X4, a, 0, ROW_3, 0);

        for (int c = 0; c < 4; c++) {
            int offset = c * 4;
            FloatVector col = FloatVector.fromArray(F32X4, b, offset);
            out[offset] = row0.mul(col).reduceLanes(VectorOperators.ADD);
            out[offset + 1] = row1.mul(col).reduceLanes(VectorOperators.ADD);
            out[offset + 2] = row2.mul(col).reduceLanes(VectorOperators.ADD);
            out[offset + 3] = row3.mul(col).reduceLanes(VectorOperators.ADD);
        }
    }
}

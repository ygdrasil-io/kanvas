package org.skia.pipeline.jvm;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;

public final class CpuVectorSolidRectKernelJvm {
    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    private CpuVectorSolidRectKernelJvm() {
    }

    public static int fillSrcOverClear(int width, int height, int packedSrcOverClear, int[] dst) {
        int count = Math.multiplyExact(width, height);
        IntVector packed = IntVector.broadcast(SPECIES, packedSrcOverClear);
        int i = 0;
        int upper = SPECIES.loopBound(count);
        for (; i < upper; i += SPECIES.length()) {
            packed.intoArray(dst, i);
        }
        for (; i < count; i++) {
            dst[i] = packedSrcOverClear;
        }
        return SPECIES.length();
    }
}

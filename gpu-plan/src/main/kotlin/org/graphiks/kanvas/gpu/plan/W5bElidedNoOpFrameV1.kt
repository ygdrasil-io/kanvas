package org.graphiks.kanvas.gpu.plan

/** Original operation-index authority retained before prepared source inventories are elided. */
public class W5bElidedNoOpFrameV1 private constructor(operations: List<Operation>) {
    public val operations: List<Operation> = java.util.Collections.unmodifiableList(ArrayList(operations))

    public class Operation private constructor(
        public val operationIndexI32: Int,
        public val sourceTable: MaterialPlanTable,
        public val sourceRef: MaterialPlanRef,
    ) {
        public companion object {
            /** The caller has already validated the prepared geometry and final blend. */
            public fun seal(operationIndexI32: Int, sourceTable: MaterialPlanTable,
                sourceRef: MaterialPlanRef, blend: BlendPlan): Operation {
                require(operationIndexI32 >= 0 && blend == BlendPlan.NoOpV1)
                sourceTable.entry(sourceRef)
                return Operation(operationIndexI32, sourceTable, sourceRef)
            }
        }
    }

    public companion object {
        public fun seal(visualOperationIndicesI32: List<Int>, operations: List<Operation>): W5bElidedNoOpFrameV1 {
            val ordered = operations.sortedBy { it.operationIndexI32 }
            require(visualOperationIndicesI32.isNotEmpty() &&
                visualOperationIndicesI32.zipWithNext().all { (a, b) -> a < b } &&
                ordered.map { it.operationIndexI32 } == visualOperationIndicesI32) {
                "invalid.w5b.elided-operation-domain"
            }
            return W5bElidedNoOpFrameV1(ordered)
        }
    }
}

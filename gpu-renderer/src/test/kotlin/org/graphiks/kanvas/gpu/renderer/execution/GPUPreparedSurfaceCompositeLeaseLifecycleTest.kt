package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GPUPreparedSurfaceCompositeLeaseLifecycleTest {
    @Test
    fun `second submit refusal quarantines every non terminal child`() {
        val first = StrictRecordingLeaseLifecycle()
        val second = StrictRecordingLeaseLifecycle(refuseSubmit = true)
        val third = StrictRecordingLeaseLifecycle()
        val composite = composite(first, second, third)

        assertIs<GPUPreparedNativeFrameLeaseTransition.Refused>(
            composite.markSubmitted(),
        )

        assertEquals(StrictRecordingLeaseLifecycle.State.Terminal, first.state)
        assertEquals(StrictRecordingLeaseLifecycle.State.Terminal, second.state)
        assertEquals(StrictRecordingLeaseLifecycle.State.Terminal, third.state)
        assertEquals(1, first.submitCalls)
        assertEquals(1, second.submitCalls)
        assertEquals(0, third.submitCalls)
        assertEquals(1, first.quarantineCalls)
        assertEquals(1, second.quarantineCalls)
        assertEquals(1, third.quarantineCalls)
    }

    @Test
    fun `partial release before submit quarantines only children that remain non terminal`() {
        val first = StrictRecordingLeaseLifecycle()
        val second = StrictRecordingLeaseLifecycle(refuseRollback = true)
        val third = StrictRecordingLeaseLifecycle()
        val composite = composite(first, second, third)

        assertIs<GPUPreparedNativeFrameLeaseTransition.Refused>(
            composite.releaseBeforeSubmit(),
        )

        assertEquals(StrictRecordingLeaseLifecycle.State.Terminal, first.state)
        assertEquals(StrictRecordingLeaseLifecycle.State.Terminal, second.state)
        assertEquals(StrictRecordingLeaseLifecycle.State.Terminal, third.state)
        assertEquals(0, first.quarantineCalls)
        assertEquals(1, second.quarantineCalls)
        assertEquals(1, third.quarantineCalls)
    }

    @Test
    fun `partial completion quarantines submitted children that remain non terminal`() {
        val first = StrictRecordingLeaseLifecycle()
        val second = StrictRecordingLeaseLifecycle(refuseCompletion = true)
        val third = StrictRecordingLeaseLifecycle()
        val composite = composite(first, second, third)
        assertIs<GPUPreparedNativeFrameLeaseTransition.Applied>(
            composite.markSubmitted(),
        )

        assertIs<GPUPreparedNativeFrameLeaseTransition.Refused>(
            composite.releaseAfterCompletion(),
        )

        assertEquals(StrictRecordingLeaseLifecycle.State.Terminal, first.state)
        assertEquals(StrictRecordingLeaseLifecycle.State.Terminal, second.state)
        assertEquals(StrictRecordingLeaseLifecycle.State.Terminal, third.state)
        assertEquals(0, first.quarantineCalls)
        assertEquals(1, second.quarantineCalls)
        assertEquals(1, third.quarantineCalls)
    }

    @Test
    fun `terminal composite refuses duplicate transitions without releasing children twice`() {
        val first = StrictRecordingLeaseLifecycle()
        val second = StrictRecordingLeaseLifecycle()
        val composite = composite(first, second)

        assertIs<GPUPreparedNativeFrameLeaseTransition.Applied>(
            composite.releaseBeforeSubmit(),
        )
        assertIs<GPUPreparedNativeFrameLeaseTransition.Refused>(
            composite.releaseBeforeSubmit(),
        )
        assertIs<GPUPreparedNativeFrameLeaseTransition.Refused>(
            composite.markSubmitted(),
        )
        assertIs<GPUPreparedNativeFrameLeaseTransition.Refused>(
            composite.releaseAfterCompletion(),
        )
        assertIs<GPUPreparedNativeFrameLeaseTransition.Refused>(
            composite.quarantineUncertain(),
        )

        assertEquals(1, first.rollbackCalls)
        assertEquals(1, second.rollbackCalls)
        assertEquals(0, first.submitCalls)
        assertEquals(0, second.submitCalls)
        assertEquals(0, first.completionCalls)
        assertEquals(0, second.completionCalls)
        assertEquals(0, first.quarantineCalls)
        assertEquals(0, second.quarantineCalls)
    }

    private fun composite(
        vararg children: GPUPreparedNativeFrameLeaseLifecycle,
    ): GPUPreparedNativeFrameLeaseLifecycle =
        GPUPreparedNativeCompositeFrameLeaseLifecycle(children.toList())
}

private class StrictRecordingLeaseLifecycle(
    private val refuseSubmit: Boolean = false,
    private val refuseRollback: Boolean = false,
    private val refuseCompletion: Boolean = false,
) : GPUPreparedNativeFrameLeaseLifecycle {
    enum class State {
        CheckedOut,
        Submitted,
        Terminal,
    }

    var state = State.CheckedOut
        private set
    var rollbackCalls = 0
        private set
    var submitCalls = 0
        private set
    var completionCalls = 0
        private set
    var quarantineCalls = 0
        private set

    override fun releaseBeforeSubmit(): GPUPreparedNativeFrameLeaseTransition {
        rollbackCalls += 1
        if (state != State.CheckedOut) return refused("rollback:$state")
        if (refuseRollback) return refused("rollback:injected")
        state = State.Terminal
        return GPUPreparedNativeFrameLeaseTransition.Applied
    }

    override fun markSubmitted(): GPUPreparedNativeFrameLeaseTransition {
        submitCalls += 1
        if (state != State.CheckedOut) return refused("submit:$state")
        if (refuseSubmit) return refused("submit:injected")
        state = State.Submitted
        return GPUPreparedNativeFrameLeaseTransition.Applied
    }

    override fun releaseAfterCompletion(): GPUPreparedNativeFrameLeaseTransition {
        completionCalls += 1
        if (state != State.Submitted) return refused("completion:$state")
        if (refuseCompletion) return refused("completion:injected")
        state = State.Terminal
        return GPUPreparedNativeFrameLeaseTransition.Applied
    }

    override fun quarantineUncertain(): GPUPreparedNativeFrameLeaseTransition {
        quarantineCalls += 1
        if (state == State.Terminal) return refused("quarantine:$state")
        state = State.Terminal
        return GPUPreparedNativeFrameLeaseTransition.Applied
    }

    private fun refused(reason: String) =
        GPUPreparedNativeFrameLeaseTransition.Refused(reason)
}

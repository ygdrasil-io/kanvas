package org.graphiks.kanvas.gpu.renderer.scenes.windowed

internal enum class KadreWindowLifecycleAction {
    None,
    RequestRedraw,
    AwaitFrameCompletion,
    CloseResources,
}

/** AppKit-thread state machine that keeps completion delivery alive during window teardown. */
internal class KadreWindowFrameLifecycle {
    private enum class State {
        Idle,
        FrameInFlight,
        ClosePending,
        Closed,
    }

    private var state = State.Idle

    val canRequestRedraw: Boolean
        get() = state == State.Idle

    fun beginFrame(): Boolean {
        if (state != State.Idle) return false
        state = State.FrameInFlight
        return true
    }

    fun requestClose(): KadreWindowLifecycleAction = when (state) {
        State.Idle -> {
            state = State.Closed
            KadreWindowLifecycleAction.CloseResources
        }
        State.FrameInFlight -> {
            state = State.ClosePending
            KadreWindowLifecycleAction.AwaitFrameCompletion
        }
        State.ClosePending -> KadreWindowLifecycleAction.AwaitFrameCompletion
        State.Closed -> KadreWindowLifecycleAction.None
    }

    fun destroySurfaces(): KadreWindowLifecycleAction = requestClose()

    fun frameCompleted(): KadreWindowLifecycleAction = when (state) {
        State.FrameInFlight -> {
            state = State.Idle
            KadreWindowLifecycleAction.RequestRedraw
        }
        State.ClosePending -> {
            state = State.Closed
            KadreWindowLifecycleAction.CloseResources
        }
        State.Idle,
        State.Closed,
        -> KadreWindowLifecycleAction.None
    }
}

# FOR-75 Kadre/AppKit Frame Clock Audit

Date: 2026-06-01

## Finding

The M70 Kadre native route is event-driven, not autonomous. The Kanvas smoke
app requests redraws after initialization, before each wait, and after each
presented frame, but Kadre AppKit `requestRedraw()` only sets
`AppKitWindow.needsRedraw`; it does not wake the CFRunLoop. With the default
`ControlFlow.Wait`, the run loop can sleep indefinitely until a native event
arrives. Mouse movement supplies that native event, the AppKit observer reaches
`kCFRunLoopBeforeWaiting`, consumes `needsRedraw`, emits
`WindowEvent.RedrawRequested`, and animation advances by one frame.

## Evidence

- `kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/M69KadreNativeSmoke.kt:336`
  requests the first redraw after surface setup.
- `kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/M69KadreNativeSmoke.kt:342`
  requests another redraw from `aboutToWait`.
- `kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/M69KadreNativeSmoke.kt:348`
  renders only on `WindowEvent.RedrawRequested`.
- `kadre-runtime/src/main/kotlin/org/skia/kadre/runtime/M69KadreNativeSmoke.kt:431`
  requests the next redraw after a frame, but does not change control flow.
- `external/poc-koreos/kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitWindow.kt:186`
  implements `requestRedraw()` as `needsRedraw = true`.
- `external/poc-koreos/kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/CFRunLoopRedrawObserver.kt:77`
  dispatches `RedrawRequested` only from the before-waiting observer.
- `external/poc-koreos/kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/AppKitEventLoop.kt:63`
  defaults the loop to `ControlFlow.Wait`.
- `external/poc-koreos/kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/CFRunLoopRedrawObserver.kt:88`
  applies `Poll`/`WaitUntil` only after `aboutToWait`.
- `external/poc-koreos/kadre-appkit/src/jvmMain/kotlin/org/graphiks/kadre/appkit/KadreApplication.kt:299`
  dispatches mouse movement as `WindowEvent.PointerMoved`, explaining why
  motion wakes the event path.

## Recommended Path

For the Kanvas M70/M71 demo, set an explicit frame clock in the smoke app
instead of relying on `requestRedraw()` alone. The lowest-risk demo fix is to
import `org.graphiks.kadre.core.ControlFlow` and call
`eventLoop.setControlFlow(ControlFlow.Poll)` before requesting demo frames. This
matches the Kadre Pong sample, which sets `ControlFlow.Poll` for continuous
animation.

For a less CPU-heavy route, use `ControlFlow.WaitUntil(System.currentTimeMillis()
+ 16L)` from `aboutToWait` or after each frame and keep requesting redraw. That
should be treated as a paced timer path, not a display-link/vsync path.

## Upstream Blocker

Kadre AppKit does not expose a vsync/display-link frame source in the inspected
sources. `Poll` can make the demo autonomous, and `WaitUntil` can pace it by
timer, but neither proves display-linked animation. If M70 PM wording needs
vsync/display-link semantics, open an upstream Kadre ticket for an AppKit
display-link or frame-callback API.

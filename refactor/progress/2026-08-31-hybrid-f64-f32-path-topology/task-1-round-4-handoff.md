# Task 1 — Round 4 handoff

Round 4 was assigned to a fresh Terra implementer at maximum reasoning after
the round-3 provenance blocker ruling. The agent implemented an uncommitted
bridge across intersections, source topology, the legacy arrangement and F32
projection, then stopped responding to bounded checkpoints and was interrupted
by the controller. No round-4 commit or report section was produced.

Last useful checkpoint from the implementer:

- the first public JVM integration run had 11 failures;
- a local same-span tangency proof reduced the remaining set to 7 failures;
- five failures were historical fixtures calling the projector without a
  provenance trace;
- one was a legacy arrangement collinearity assertion;
- one was an under-threshold fixture without a witness;
- public operations and tangent cases were reported passing;
- the intended remaining work was to remove/neutralize legacy helpers, migrate
  the fixtures to behavior, run JVM/JS, write evidence and commit.

The uncommitted tree at handoff modifies seven files, with approximately 863
insertions and 1137 deletions. The final round must first audit this state. It
must not assume the partial implementation is correct, and must not discard it
destructively. If it cannot establish exact RED/GREEN evidence and close all
Critical/Important findings, it must return `BLOCKED` rather than commit a
partial transition.


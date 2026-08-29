# W146 — translated triangle radial-gradient clip

The existing hard-path radial route is authenticated only for the bounded stroke
consumer. A translated filled triangle with a translated path clip and a two-stop
clamp radial gradient therefore remains outside that native route; the public
inventory test records this refusal boundary and does not promote a false route.

Evidence: `GPUFramePathApiInventoryTest.translated triangle radial gradient remains
explicitly refused outside hard stroke lane`.

No scene/catalogue or pixel oracle was added: the current native materializer has no
independent direct filled-triangle radial consumer proof for this combination.

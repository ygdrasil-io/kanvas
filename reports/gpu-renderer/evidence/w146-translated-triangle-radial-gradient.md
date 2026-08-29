# W146 — translated triangle radial-gradient clip

The existing hard-path radial FillRect consumer is covered by a translated sibling:
the scene translates the clip triangle, rectangle, and radial center by `(2, 0)` and
uses the independent double-precision CPU oracle in device space.

Evidence: catalog case `clip-path-translated-triangle-radial-gradient`, its translated
scene program, and the existing hard-clip radial oracle policy. The public inventory
test `translated triangle radial gradient remains explicitly refused outside hard
stroke lane` remains as a guard for the separate filled-triangle path variant.

The filled-triangle path variant remains refused; this slice promotes only the
translated FillRect radial sibling.

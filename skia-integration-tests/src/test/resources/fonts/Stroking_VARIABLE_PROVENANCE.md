## StrokeTextNative fixtures provenance

These three fixtures are temporary, license-safe stand-ins used to activate
the deterministic `StrokeTextNativeGM` subset in issue #1057:

- `Stroking.ttf` is copied from `kanvas-skia/src/main/resources/fonts/ReallyBigA.ttf`.
- `Stroking.otf` currently reuses the same OpenType bytes as `Stroking.ttf`.
- `Variable.ttf` is copied from `kanvas-skia/src/main/resources/fonts/Distortable.ttf`.

Intent:

- keep the TTF/OTF codepaths runnable from bundled resources;
- keep the overlap branch runnable via variable-axis clone (`wght=721`) on a
  pure Kotlin OpenType fixture;
- unblock test + ratchet wiring while waiting for canonical upstream-equivalent
  Stroking assets.

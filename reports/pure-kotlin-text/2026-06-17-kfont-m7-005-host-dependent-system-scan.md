# KFONT-M7-005 - Host-dependent system scan diagnostics

## Scope landed

- `font:text` now exposes a bounded `defaultSystemFontScanEvidenceBundle()`
  that generates deterministic host-dependent scan evidence from a pure Kotlin
  fixture directory without OS font APIs, fontconfig, AWT, CoreText, or
  DirectWrite.
- The landed evidence stays explicitly non-normative and separate from the
  bundled `font-catalog.json` and shared fallback dumps so `M7-001` and
  `M7-002` claims are not broadened implicitly.

## Evidence

- `system-font-scan.json` records a deterministic fixture-root label,
  `configSha256`, host-dependent marker, bounded max-byte policy, parser table
  facts for valid/malformed SFNT fixtures, and stable diagnostics for
  unreadable, unsupported-wrapper, oversized, duplicate-byte, and
  missing-required-table cases.
- `system-font-scan-font-catalog-link.json` links each scanned source into a
  non-normative catalog example with `SystemScannedFontSource`,
  `hostDependent=true`, and deterministic diagnostic-code propagation.
- `system-font-scan-fallback-trace.json` provides a bounded host-dependent
  fallback trace example showing `selectedSourceKind`,
  `selectedScanEntryId`, and `selectedHostDependent=true` without promoting
  broader fallback support.
- `SystemFontScanTest` asserts byte-identical checked-in goldens and verifies
  stable host-dependent, unreadable, unsupported-wrapper, duplicate-face, and
  required-table diagnostics without HarfBuzz, FreeType, or native font-engine
  wording.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests '*SystemFontScan*'
```

## Remaining gate

This remains bounded host-dependent review evidence only. It does not promote
normative system-font support, bundled-catalog support, platform fallback
parity, native font APIs, CPU oracle fallback validation, or a decision that
the main `font-catalog.json` / shared fallback dumps must absorb these linked
examples.

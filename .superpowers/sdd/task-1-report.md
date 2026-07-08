# Task 1 Report - Extract Shared Dashboard Evidence Types

Status: DONE

Summary:
- Extracted `GmDashboard`, `GmDashboardRow`, and `GmDashboardJsonReader` into `integration-tests/skia-evidence/src/main/kotlin/org/graphiks/kanvas/skia/evidence/GmDashboardEvidence.kt`.
- Removed the duplicate dashboard model and JSON helpers from `Phase6ImageFamilyEvidence.kt`.
- Added the requested characterization test to `Phase6ImageFamilyEvidenceTest.kt` to confirm the shared reader preserves CLIP flags and `sizeMismatch`.

Verification:
- `rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest.dashboard reader keeps path clip flags for shared evidence"`
- `rtk ./gradlew :integration-tests:skia-evidence:test --tests "org.graphiks.kanvas.skia.evidence.Phase6ImageFamilyEvidenceTest"`

Result:
- Both commands passed.

Concerns:
- None.

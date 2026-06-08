#!/usr/bin/env python3
"""Validate the M90-PAA-3A-REF HairlinesGM artifact harness contract."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "M90-PAA-3A-REF"
ROW_ID = "skia-gm-hairlines"
SCENE_ID = ROW_ID
SOURCE_GM = "HairlinesGM"
FALLBACK_REASON = "coverage.hairline.row-specific-artifacts-required"
GPU_THRESHOLD = 99.95
CPU_THRESHOLD = 96.0

TEST_FILE = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/HairlinesSceneCaptureTest.kt"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-m90-hairlines-artifact-harness.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/m90-hairlines-artifact-harness.json"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines"
EXPECTED_ARTIFACT_FILES = {
    "cpu-diff.png",
    "cpu-performance.json",
    "cpu.png",
    "gpu-performance.json",
    "route-cpu.json",
    "route-gpu.json",
    "skia.png",
    "stats.json",
}
GM_FILE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/HairlinesGM.kt"
CPU_TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/Round8Test.kt"
CROSSBACKEND_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/HairlinesCrossBackendTest.kt"
INTAKE = ROOT / "reports/wgsl-pipeline/m90-path-aa-hairlines-evidence-intake/summary.json"
CANDIDATE_CLOSEOUT = ROOT / "reports/wgsl-pipeline/m90-path-aa-candidate-intake-closeout/summary.json"

FORBIDDEN_PROMOTION_FILES = {
    "reports/wgsl-pipeline/m89-gm-registry/registry.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/dashboard-results.json",
    "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json",
}
ALLOWED_STATUS_PATHS = {
    "build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/HairlinesSceneCaptureTest.kt",
    "scripts/m90_path_aa_candidate_intake_closeout.py",
    "scripts/m90_path_aa_hairlines_evidence_intake.py",
    "scripts/validate_m90_hairlines_artifact_harness.py",
    "reports/wgsl-pipeline/2026-06-08-m90-hairlines-artifact-harness.md",
    "reports/wgsl-pipeline/m90-path-aa-candidate-intake-closeout/summary.json",
    "reports/wgsl-pipeline/m90-path-aa-candidate-intake-closeout/summary.md",
    "reports/wgsl-pipeline/m90-path-aa-hairlines-evidence-intake/summary.json",
    "reports/wgsl-pipeline/m90-path-aa-hairlines-evidence-intake/summary.md",
    "reports/wgsl-pipeline/scenes/generated/m90-hairlines-artifact-harness.json",
}


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} root must be an object")
    return data


def require_text(path: Path, snippets: list[str]) -> str:
    require(path.is_file(), f"missing file: {rel(path)}")
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")
    return text


def validate_test() -> None:
    text = require_text(
        TEST_FILE,
        [
            "class HairlinesSceneCaptureTest",
            "HairlinesGM()",
            "TestUtils.loadReferenceBitmap(gm.name())",
            "TestUtils.runGmTest(gm)",
            "WebGpuSink.draw(ctx, gm)",
            'System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true"',
            "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines",
            "skia.png",
            "cpu.png",
            "cpu-diff.png",
            "gpu.png",
            "gpu-diff.png",
            "route-cpu.json",
            "route-gpu.json",
            "stats.json",
            "cpu-performance.json",
            "gpu-performance.json",
            FALLBACK_REASON,
            '"supportClaim": false',
            "globalDashboardPromoted",
            "globalThresholdChanged",
            "neighborEvidenceInherited",
            "broadPathAASupport",
            "broadHairlineSupport",
            "private const val CPU_TOLERANCE = 1",
            "private const val GPU_TOLERANCE = TestUtils.TEXTUAL_GM_TOLERANCE",
            "private const val CPU_MINIMUM_SIMILARITY = 96.0",
            "private const val GPU_PROMOTION_THRESHOLD = 99.95",
        ],
    )
    require("@Disabled" not in text, f"{rel(TEST_FILE)} must not disable the harness test")


def validate_existing_evidence() -> None:
    require_text(
        GM_FILE,
        [
            'override fun getName(): String = "hairlines"',
            "SkISize.Make(1250, 1250)",
            "val alphaValue = intArrayOf(0xFF, 0x40)",
            "val widths = floatArrayOf(0f, 0.5f, 1.5f)",
            "SkColorSetARGB(a, 0, 0, 0)",
        ],
    )
    require_text(
        CPU_TEST,
        [
            "runGm(HairlinesGM(), \"HairlinesGM\", 96.0)",
        ],
    )
    require_text(
        CROSSBACKEND_TEST,
        [
            "rasterFloor = 97.63",
            "gpuFloor = 98.92",
            "rasterTolerance = 1",
        ],
    )

    intake = load_json(INTAKE)
    require(intake.get("ticket") == "M90-PAA-3A", "hairlines intake ticket changed")
    require(intake.get("classification") == "path-aa-hairlines-evidence-intake-no-new-rendering-support", "hairlines intake classification changed")
    require(
        intake.get("status") in {
            "blocked-by-missing-row-specific-evidence",
            "partial-row-specific-evidence-present-non-promotional",
            "row-specific-evidence-present-non-promotional",
        },
        "hairlines intake status changed",
    )
    row = intake.get("row")
    require(isinstance(row, dict), "hairlines intake missing row")
    require(row.get("rowId") == ROW_ID, "hairlines intake row changed")
    require(row.get("status") == "expected-unsupported", "hairlines intake row status changed")
    require(row.get("supportClaim") is False, "hairlines intake support claim changed")
    require(row.get("fallbackReason") == FALLBACK_REASON, "hairlines intake fallback changed")
    counters = intake.get("counters")
    require(isinstance(counters, dict), "hairlines intake missing counters")
    require(0 <= counters.get("presentEvidenceItems") <= 10, "hairlines intake present-evidence count out of range")
    require(counters.get("missingEvidenceItems") == 10 - counters.get("presentEvidenceItems"), "hairlines intake missing-evidence count mismatch")
    if "validatedNonPromotionalEvidenceItems" in counters:
        require(counters.get("validatedNonPromotionalEvidenceItems") == counters.get("presentEvidenceItems"), "hairlines intake present evidence must be validated non-promotional")
    require(counters.get("historicalSignals") == 7, "hairlines intake historical-signal count changed")
    require(counters.get("newSupportClaims") == 0, "hairlines intake must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "hairlines intake readiness delta changed")
    next_ticket = intake.get("nextRecommendedTicket")
    require(isinstance(next_ticket, dict), "hairlines intake missing next ticket")
    require(next_ticket.get("id") == TICKET, "hairlines intake next ticket changed")
    require(next_ticket.get("supportClaimAllowed") is False, "hairlines intake next ticket must not allow support claims")

    closeout = load_json(CANDIDATE_CLOSEOUT)
    require(closeout.get("classification") == "path-aa-candidate-intake-closeout-no-new-rendering-support", "candidate closeout classification changed")
    counters = closeout.get("counters")
    require(isinstance(counters, dict), "candidate closeout missing counters")
    require(counters.get("newSupportClaims") == 0, "candidate closeout gained support claims")
    require(closeout.get("activeNextRecommendedTicket", {}).get("id") == "M90-PAA-3A", "candidate closeout active next ticket changed")
    require(closeout.get("nextHandoff", {}).get("id") == TICKET, "candidate closeout handoff changed")


def validate_evidence_json() -> None:
    data = load_json(EVIDENCE)
    require(data.get("ticket") == TICKET, "evidence ticket mismatch")
    require(data.get("sceneId") == SCENE_ID, "evidence sceneId mismatch")
    require(data.get("inventoryId") == ROW_ID, "evidence inventoryId mismatch")
    require(data.get("sourceGm") == SOURCE_GM, "evidence sourceGm mismatch")
    require(data.get("status") == "expected-unsupported", "evidence must remain expected-unsupported")
    require(data.get("supportClaim") is False, "evidence must not claim support")
    require(data.get("fallbackReason") == FALLBACK_REASON, "evidence fallback mismatch")
    require(data.get("policyOnlyRowRetained") is True, "evidence must retain policy-only row")

    harness = data.get("harness")
    require(isinstance(harness, dict), "evidence missing harness")
    require(harness.get("testFile") == rel(TEST_FILE), "harness testFile mismatch")
    require(harness.get("gmClass") == "org.skia.tests.HairlinesGM", "harness GM class mismatch")
    require(harness.get("referenceName") == "hairlines", "harness referenceName mismatch")
    require(harness.get("artifactDir") == rel(ARTIFACT_DIR), "harness artifactDir mismatch")
    require(harness.get("cpuTolerance") == 1, "harness CPU tolerance changed")
    require(harness.get("cpuMinimumSimilarity") == CPU_THRESHOLD, "harness CPU threshold changed")
    require(harness.get("gpuPromotionThreshold") == GPU_THRESHOLD, "harness GPU threshold changed")
    require(harness.get("globalDashboardPromotionAllowed") is False, "harness must not allow dashboard promotion")
    require(harness.get("globalThresholdChangeAllowed") is False, "harness must not allow threshold changes")
    require(harness.get("neighborEvidenceInheritanceAllowed") is False, "harness must not allow neighbor evidence inheritance")

    contract = data.get("artifactContract")
    require(isinstance(contract, dict), "evidence missing artifactContract")
    require(contract.get("writeMode") == "opt-in", "artifact write mode must remain opt-in")
    require(contract.get("checkedInRenderedArtifactsRequiredByThisItem") is True, "checked-in artifact item must require rendered artifacts")
    expected_artifacts = "\n".join(contract.get("alwaysExpectedWhenWriteEnabled", []) + contract.get("adapterExpectedWhenRendered", []))
    for name in [
        "skia.png",
        "cpu.png",
        "cpu-diff.png",
        "gpu.png",
        "gpu-diff.png",
        "route-cpu.json",
        "route-gpu.json",
        "stats.json",
        "cpu-performance.json",
        "gpu-performance.json",
    ]:
        require(name in expected_artifacts, f"artifact contract missing {name}")

    gate = data.get("promotionGate")
    require(isinstance(gate, dict), "evidence missing promotionGate")
    require(gate.get("supportByStructure") is False, "promotion gate must reject structural support")
    require(gate.get("promotionAllowedWithoutArtifacts") is False, "promotion gate must require artifacts")
    require(gate.get("promotionAllowedWithoutFallbackReasonNone") is False, "promotion gate must require fallbackReason=none")
    require(gate.get("missingArtifactStatus") == "expected-unsupported", "missing artifact status changed")
    require(gate.get("stableUnsupportedReason") == FALLBACK_REASON, "stable unsupported reason changed")

    impact = data.get("scoreImpact")
    require(impact == {
        "newSupportClaims": 0,
        "readinessDelta": 0.0,
        "dashboardPromotion": False,
        "thresholdChanged": False,
        "edgeBudgetChanged": False,
    }, "scoreImpact changed")

    non_claims = data.get("nonClaims")
    require(isinstance(non_claims, dict), "evidence missing nonClaims")
    for key, expected in {
        "rowSupportClaimed": False,
        "broadPathAASupport": False,
        "broadHairlineSupport": False,
        "broadStrokeSupport": False,
        "broadDashSupport": False,
        "ganeshPort": False,
        "graphitePort": False,
        "dynamicSkSLCompiler": False,
        "skSLIR": False,
        "skSLVM": False,
        "thresholdChanged": False,
        "dashboardPromoted": False,
        "belowThresholdCountedAsProductionGap": False,
    }.items():
        require(non_claims.get(key) is expected, f"nonClaim changed: {key}")


def validate_report() -> None:
    require_text(
        REPORT,
        [
            "M90 Hairlines Artifact Harness",
            TICKET,
            "`HairlinesGM` / `skia-gm-hairlines`",
            "does not promote `skia-gm-hairlines`",
            "`expected-unsupported`",
            f"`fallbackReason={FALLBACK_REASON}`",
            "No checked-in dashboard row, registry row, similarity threshold, edge budget, or fallback policy was changed",
            "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.HairlinesSceneCaptureTest",
            "cpu-performance.json",
            "gpu-performance.json",
            "checked-in files as non-promotional evidence",
            "No Ganesh or Graphite port",
            "No dynamic SkSL compiler, IR, or VM",
            "No global threshold reduction",
            "No support promotion from a below-threshold/tolerance-only case",
        ],
    )


def png_header(path: Path) -> None:
    require(path.is_file(), f"missing PNG artifact: {rel(path)}")
    require(path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"), f"artifact is not PNG: {rel(path)}")


def validate_optional_artifacts() -> None:
    require(ARTIFACT_DIR.is_dir(), f"missing artifact directory: {rel(ARTIFACT_DIR)}")
    actual = {path.name for path in ARTIFACT_DIR.iterdir() if path.is_file()}
    require(actual == EXPECTED_ARTIFACT_FILES, f"artifact file set changed: expected={sorted(EXPECTED_ARTIFACT_FILES)} actual={sorted(actual)}")
    for name in ["skia.png", "cpu.png", "cpu-diff.png"]:
        png_header(ARTIFACT_DIR / name)
    for name in ["route-cpu.json", "route-gpu.json", "stats.json", "cpu-performance.json", "gpu-performance.json"]:
        require((ARTIFACT_DIR / name).is_file(), f"missing artifact metadata: {rel(ARTIFACT_DIR / name)}")
    cpu = load_json(ARTIFACT_DIR / "route-cpu.json")
    gpu = load_json(ARTIFACT_DIR / "route-gpu.json")
    stats = load_json(ARTIFACT_DIR / "stats.json")
    cpu_perf = load_json(ARTIFACT_DIR / "cpu-performance.json")
    gpu_perf = load_json(ARTIFACT_DIR / "gpu-performance.json")
    require(cpu.get("sceneId") == SCENE_ID, "CPU route scene mismatch")
    require(cpu.get("backend") == "CPU", "CPU route backend mismatch")
    require(cpu.get("fallbackReason") == "none", "CPU route must have fallbackReason=none")
    require(cpu.get("status") == "pass", "CPU route must pass")
    require(gpu.get("sceneId") == SCENE_ID, "GPU route scene mismatch")
    require(gpu.get("backend") == "WebGPU", "GPU route backend mismatch")
    require(gpu.get("status") == "pass" or gpu.get("fallbackReason") == FALLBACK_REASON, "GPU route must pass or retain refusal")
    require(gpu.get("supportClaim") is False, "GPU route artifact must remain non-promotional")
    require(stats.get("sceneId") == SCENE_ID, "stats scene mismatch")
    require(stats.get("globalDashboardPromoted") is False, "optional stats must not promote dashboard")
    require(stats.get("supportClaim") is False, "optional stats must remain non-promotional")
    require(cpu_perf.get("sceneId") == SCENE_ID, "CPU perf scene mismatch")
    require(cpu_perf.get("backend") == "CPU", "CPU perf backend mismatch")
    require(cpu_perf.get("supportClaim") is False, "CPU perf must remain non-promotional")
    require(cpu_perf.get("globalDashboardPromoted") is False, "CPU perf must not promote dashboard")
    require(isinstance(cpu_perf.get("elapsedNanos"), int) and cpu_perf["elapsedNanos"] > 0, "CPU perf must record elapsedNanos")
    require(gpu_perf.get("sceneId") == SCENE_ID, "GPU perf scene mismatch")
    require(gpu_perf.get("backend") == "WebGPU", "GPU perf backend mismatch")
    require(gpu_perf.get("supportClaim") is False, "GPU perf must remain non-promotional")
    require(gpu_perf.get("globalDashboardPromoted") is False, "GPU perf must not promote dashboard")
    if gpu_perf.get("elapsedNanos") is not None:
        require(isinstance(gpu_perf.get("elapsedNanos"), int) and gpu_perf["elapsedNanos"] > 0, "GPU perf elapsedNanos must be positive when present")
    if (ARTIFACT_DIR / "gpu.png").exists():
        png_header(ARTIFACT_DIR / "gpu.png")
        png_header(ARTIFACT_DIR / "gpu-diff.png")


def validate_worktree_scope(allow_artifacts: bool = False) -> None:
    proc = subprocess.run(
        ["git", "status", "--short"],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    seen_forbidden: list[str] = []
    unexpected: list[str] = []
    for raw in proc.stdout.splitlines():
        if not raw.strip():
            continue
        path = raw[3:] if len(raw) > 3 else raw.strip()
        if path == "tmp/" or path.startswith("tmp/"):
            continue
        if allow_artifacts and (path == rel(ARTIFACT_DIR) + "/" or path.startswith(rel(ARTIFACT_DIR) + "/")):
            if path == rel(ARTIFACT_DIR) + "/":
                continue
            require(Path(path).name in EXPECTED_ARTIFACT_FILES, f"unexpected Hairlines artifact path: {path}")
            continue
        if path in FORBIDDEN_PROMOTION_FILES:
            seen_forbidden.append(path)
        if path not in ALLOWED_STATUS_PATHS:
            unexpected.append(raw)
    require(not seen_forbidden, f"forbidden promotion/dashboard files modified: {seen_forbidden}")
    require(not unexpected, f"unexpected worktree paths for this scoped item: {unexpected}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-artifacts", action="store_true", help="also validate opt-in rendered artifacts if they were generated")
    args = parser.parse_args()

    validate_test()
    validate_existing_evidence()
    validate_evidence_json()
    validate_report()
    artifacts_present = ARTIFACT_DIR.exists()
    if args.require_artifacts or artifacts_present:
        validate_optional_artifacts()
    validate_worktree_scope(allow_artifacts=args.require_artifacts or artifacts_present)
    print(f"validated {TICKET} HairlinesGM artifact harness contract")


if __name__ == "__main__":
    main()

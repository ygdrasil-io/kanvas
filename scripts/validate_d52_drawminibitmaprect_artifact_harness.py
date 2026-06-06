#!/usr/bin/env python3
"""Validate D52-2 DrawMiniBitmapRect artifact harness evidence."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D52-2"
ROW_ID = "skia-gm-drawminibitmaprect"
SCENE_ID = "d52-drawminibitmaprect"
FALLBACK_REASON = "bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required"
SOURCE_DRAFT = "global/kanvas/tickets/drafts/brouillon-ticket-d52-2-ajouter-harness-artefacts-drawminibitmaprect"
FORBIDDEN_M66_ROW = "m66-bitmap-rect-nearest-skia"
GPU_THRESHOLD = 99.95

TEST_FILE = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/DrawMiniBitmapRectSceneCaptureTest.kt"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d52-2-drawminibitmaprect-artifact-harness.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-artifact-harness.json"
D52_1_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d52-1-drawminibitmaprect-promotion-readiness.md"
D52_1_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-promotion-readiness.json"
D52_1_VALIDATOR = ROOT / "scripts/validate_d52_drawminibitmaprect_promotion_readiness.py"
GM_FILE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/DrawMiniBitmapRectGM.kt"
CPU_TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/DrawMiniBitmapRectTest.kt"
CPU_AA_TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/DrawMiniBitmapRectAaTest.kt"
REFERENCE = ROOT / "skia-integration-tests/src/test/resources/original-888/drawminibitmaprect.png"
M66_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect"

EXPECTED_CHANGED_FILES = {
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/DrawMiniBitmapRectSceneCaptureTest.kt",
    "reports/wgsl-pipeline/2026-06-06-d52-2-drawminibitmaprect-artifact-harness.md",
    "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-artifact-harness.json",
    "scripts/validate_d52_drawminibitmaprect_artifact_harness.py",
}
ALLOWED_ARTIFACT_FILES = {
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/skia.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/cpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/cpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/gpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/gpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/route-cpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/route-gpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/stats.json",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/generated/d51-drawminibitmaprect-row-specific-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-promotion-readiness.json",
    "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "cpu-raster/",
    "render-pipeline/",
    "skia-integration-tests/",
)
VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_d52_drawminibitmaprect_artifact_harness.py",
    "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-artifact-harness.json",
    "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d52-drawminibitmaprect-harness-pycache python3 -m py_compile scripts/validate_d52_drawminibitmaprect_artifact_harness.py",
    "rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest",
    "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest",
    "rtk python3 scripts/validate_d52_drawminibitmaprect_artifact_harness.py --require-artifacts",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"{TICKET} validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def git_changed_paths() -> set[str]:
    diff_result = subprocess.run(
        ["git", "diff", "--name-only", "origin/master"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    status_result = subprocess.run(
        ["git", "status", "--short"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    untracked_result = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip()
        if " -> " in path:
            path = path.split(" -> ", 1)[1].strip()
        if path:
            changed.add(path.rstrip("/"))
    for line in untracked_result.stdout.splitlines():
        path = line.strip()
        if path:
            changed.add(path)
    changed.discard("reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect")
    return changed


def require_scope(require_artifacts: bool) -> None:
    changed = git_changed_paths()
    allowed = set(EXPECTED_CHANGED_FILES) | ALLOWED_ARTIFACT_FILES
    if require_artifacts:
        missing_artifacts = sorted(
            path for path in ALLOWED_ARTIFACT_FILES
            if path not in changed and not (ROOT / path).is_file()
        )
        require(not missing_artifacts, f"missing required artifact files: {missing_artifacts}")
    unexpected = sorted(path for path in changed if path not in allowed)
    require(not unexpected, f"unexpected changed files: {unexpected}")
    missing_core = sorted(
        path for path in EXPECTED_CHANGED_FILES
        if path not in changed and not (ROOT / path).is_file()
    )
    require(not missing_core, f"missing core D52-2 files: {missing_core}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard or previous evidence changed: {forbidden_paths}")
    forbidden_prefixes = sorted(
        path for path in changed
        if path.startswith(FORBIDDEN_PREFIXES) and path not in EXPECTED_CHANGED_FILES
    )
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def require_inputs() -> None:
    for path in (D52_1_REPORT, D52_1_EVIDENCE, D52_1_VALIDATOR, GM_FILE, CPU_TEST, CPU_AA_TEST, REFERENCE, M66_MANIFEST):
        require(path.is_file(), f"missing required input: {rel(path)}")

    d52_1 = load_json(D52_1_EVIDENCE)
    require(d52_1.get("ticket") == "D52-1", "D52-1 ticket mismatch")
    require(d52_1.get("inventoryId") == ROW_ID, "D52-1 inventory mismatch")
    require(d52_1.get("status") == "expected-unsupported", "D52-1 status changed")
    require(d52_1.get("fallbackReason") == FALLBACK_REASON, "D52-1 fallback changed")
    gate = d52_1.get("nextImplementationSlice", {}).get("acceptanceGate", {})
    require(gate.get("fallbackReasonNoneAllowedOnlyWhenWebGpuPasses") is True, "D52-1 gate missing fallback none rule")
    require(gate.get("m66EvidenceInheritanceAllowed") is False, "D52-1 gate must forbid M66 inheritance")

    cpu_text = CPU_TEST.read_text(encoding="utf-8")
    cpu_aa_text = CPU_AA_TEST.read_text(encoding="utf-8")
    require("@Disabled" in cpu_text, "historical CPU stress test must remain disabled")
    require("@Disabled" in cpu_aa_text, "historical CPU AA stress test must remain disabled")


def require_m66_policy() -> None:
    m66 = load_json(M66_MANIFEST)
    scenes = m66.get("scenes")
    require(isinstance(scenes, list), "M66 scenes must be a list")
    matches = [row for row in scenes if isinstance(row, dict) and row.get("id") == FORBIDDEN_M66_ROW]
    require(len(matches) == 1, "M66 nearest bitmap row must exist exactly once")
    require(matches[0].get("inventoryId") == "skia-gm-drawbitmaprect", "M66 row must remain drawbitmaprect")


def require_test_file() -> None:
    require(TEST_FILE.is_file(), f"missing test file: {rel(TEST_FILE)}")
    text = TEST_FILE.read_text(encoding="utf-8")
    required = (
        "class DrawMiniBitmapRectSceneCaptureTest",
        "DrawMiniBitmapRectGM()",
        "TestUtils.loadReferenceBitmap(gm.name())",
        "TestUtils.runGmTest(gm)",
        "WebGpuSink.draw(ctx, gm)",
        'System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true"',
        "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect",
        "skia.png",
        "cpu.png",
        "cpu-diff.png",
        "gpu.png",
        "gpu-diff.png",
        "route-cpu.json",
        "route-gpu.json",
        "stats.json",
        FALLBACK_REASON,
        "m66EvidenceInherited",
        "GPU_PROMOTION_THRESHOLD = 99.95",
    )
    for phrase in required:
        require(phrase in text, f"test file missing phrase: {phrase}")
    require("@Disabled" not in text, "capture test itself must not be disabled")


def require_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("sourceDraftMemory") == SOURCE_DRAFT, "source draft memory mismatch")
    require(evidence.get("classification") == "artifact-harness-added-no-support-claim-by-default", "classification mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory mismatch")
    require(evidence.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(evidence.get("status") == "expected-unsupported", "static evidence must remain expected-unsupported")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback reason mismatch")
    require(set(evidence.get("expectedChangedFiles", [])) == EXPECTED_CHANGED_FILES, "expectedChangedFiles mismatch")
    require(set(evidence.get("expectedArtifactFiles", [])) == ALLOWED_ARTIFACT_FILES, "expectedArtifactFiles mismatch")
    require(evidence.get("validationCommands") == VALIDATION_COMMANDS, "validation commands mismatch")

    harness = evidence.get("harness", {})
    require(harness.get("testFile") == rel(TEST_FILE), "harness test file mismatch")
    require(harness.get("gmClass") == "org.skia.tests.DrawMiniBitmapRectGM", "GM class mismatch")
    require(harness.get("referenceName") == "drawminibitmaprect", "reference name mismatch")
    require(harness.get("cpuRenderer") == "TestUtils.runGmTest(DrawMiniBitmapRectGM())", "CPU renderer mismatch")
    require(harness.get("webgpuRenderer") == "WebGpuSink.draw(ctx, DrawMiniBitmapRectGM())", "WebGPU renderer mismatch")
    require(harness.get("writeProperty") == "kanvas.sceneEvidence.write", "write property mismatch")
    require(harness.get("writePropertyRequiredValue") == "true", "write property value mismatch")
    require(harness.get("artifactDir") == rel(ARTIFACT_DIR), "artifact dir mismatch")
    require(harness.get("gpuPromotionThreshold") == GPU_THRESHOLD, "GPU threshold mismatch")
    require(harness.get("globalThresholdChangeAllowed") is False, "global threshold change must be forbidden")
    require(harness.get("m66EvidenceInheritanceAllowed") is False, "M66 inheritance must be forbidden")

    artifact = evidence.get("artifactContract", {})
    require(set(artifact.get("alwaysWrittenWhenEnabled", [])) == {
        "skia.png",
        "cpu.png",
        "cpu-diff.png",
        "route-cpu.json",
        "route-gpu.json",
        "stats.json",
    }, "always-written artifact contract mismatch")
    require(set(artifact.get("writtenOnlyWhenWebGpuProducesBitmap", [])) == {"gpu.png", "gpu-diff.png"}, "conditional GPU artifact contract mismatch")

    gate = evidence.get("promotionGate", {})
    require(gate.get("supportCanBeClaimedByStructureOnly") is False, "structure-only support must be false")
    require(gate.get("fallbackReasonNoneAllowedOnlyWhenWebGpuPasses") is True, "fallback none gate missing")
    require(gate.get("webGpuPassRequiresBitmap") is True, "WebGPU pass must require bitmap")
    require(gate.get("webGpuPassRequiresGpuDiff") is True, "WebGPU pass must require diff")
    require(gate.get("webGpuPassRequiresSimilarityAtLeast") == GPU_THRESHOLD, "WebGPU pass threshold mismatch")
    require(gate.get("keepExpectedUnsupportedIfAnyArtifactMissing") is True, "missing artifact must keep unsupported")
    require(gate.get("stableUnsupportedReason") == FALLBACK_REASON, "stable unsupported reason mismatch")

    artifact_run = evidence.get("localArtifactRun", {})
    require(artifact_run.get("result") == "pass", "local artifact run result mismatch")
    require(artifact_run.get("artifactsWritten") is True, "local artifact run must record artifacts")
    require(artifact_run.get("artifactDir") == rel(ARTIFACT_DIR), "local artifact dir mismatch")
    require(artifact_run.get("webgpu", {}).get("status") == "expected-unsupported", "local WebGPU status mismatch")
    require(artifact_run.get("webgpu", {}).get("fallbackReason") == FALLBACK_REASON, "local WebGPU fallback mismatch")
    require(artifact_run.get("webgpu", {}).get("supportClaim") is False, "local WebGPU support claim must be false")
    require(float(artifact_run.get("webgpu", {}).get("similarity", 100.0)) < GPU_THRESHOLD, "local WebGPU must remain below threshold")

    m66 = evidence.get("m66InheritancePolicy", {})
    require(m66.get("forbiddenInheritedDashboardRowId") == FORBIDDEN_M66_ROW, "forbidden M66 row mismatch")
    require(m66.get("canInheritAsSupport") is False, "M66 inheritance must be false")
    require(m66.get("m66InventoryId") == "skia-gm-drawbitmaprect", "M66 inventory mismatch")
    require(m66.get("d52InventoryId") == ROW_ID, "D52 inventory mismatch")

    score = evidence.get("scoreImpact", {})
    require(score.get("supportScoreIncreased") is False, "support score must not increase")
    require(score.get("skiaComparableScoreIncreased") is False, "Skia-comparable score must not increase")
    require(score.get("readinessScoreIncreased") is False, "readiness score must not increase")
    for key, value in evidence.get("nonClaims", {}).items():
        require(value is False, f"{key} must remain false")


def require_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    required = (
        "D52-2 ajoute le harness de capture row-specific",
        "`skia-gm-drawminibitmaprect` reste `expected-unsupported`",
        "WebGPU produit un bitmap sur `Apple M2 Max`, mais atteint `94.9305%`",
        "`fallbackReason=none` reste interdit",
        FALLBACK_REASON,
        "TestUtils.runGmTest",
        "WebGpuSink.draw",
        "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest",
        "`m66-bitmap-rect-nearest-skia` ne peut toujours pas etre herite comme preuve de",
        "Aucun gain de score n'est revendique",
        "DrawMiniBitmapRectTest",
        "DrawMiniBitmapRectAaTest",
    )
    for phrase in required:
        require(phrase in text, f"report missing phrase: {phrase}")
    for command in VALIDATION_COMMANDS:
        require(command in text, f"report missing validation command: {command}")


def require_png(path: Path) -> None:
    require(path.is_file(), f"missing PNG artifact: {rel(path)}")
    with path.open("rb") as handle:
        require(handle.read(8) == b"\x89PNG\r\n\x1a\n", f"invalid PNG header: {rel(path)}")


def require_artifacts() -> None:
    for name in ("skia.png", "cpu.png", "cpu-diff.png"):
        require_png(ARTIFACT_DIR / name)

    route_cpu = load_json(ARTIFACT_DIR / "route-cpu.json")
    route_gpu = load_json(ARTIFACT_DIR / "route-gpu.json")
    stats = load_json(ARTIFACT_DIR / "stats.json")

    require(route_cpu.get("sceneId") == SCENE_ID, "route-cpu scene mismatch")
    require(route_cpu.get("inventoryId") == ROW_ID, "route-cpu inventory mismatch")
    require(route_cpu.get("backend") == "CPU", "route-cpu backend mismatch")
    require(route_cpu.get("status") == "pass", "CPU route must pass")
    require(route_cpu.get("fallbackReason") == "none", "CPU route fallback must be none")
    require(float(route_cpu.get("similarity", 0.0)) >= float(route_cpu.get("threshold", 999.0)), "CPU route below threshold")

    require(route_gpu.get("sceneId") == SCENE_ID, "route-gpu scene mismatch")
    require(route_gpu.get("inventoryId") == ROW_ID, "route-gpu inventory mismatch")
    require(route_gpu.get("backend") == "WebGPU", "route-gpu backend mismatch")
    require(route_gpu.get("globalThresholdChanged") is False, "route-gpu must not change global threshold")
    require(route_gpu.get("m66EvidenceInherited") is False, "route-gpu must not inherit M66")
    require(stats.get("globalThresholdChanged") is False, "stats must not change global threshold")
    require(stats.get("m66EvidenceInherited") is False, "stats must not inherit M66")

    status = route_gpu.get("status")
    require(status in {"pass", "expected-unsupported"}, f"route-gpu invalid status: {status}")
    require(stats.get("status") == status, "stats status must match route-gpu")
    if status == "pass":
        require(route_gpu.get("fallbackReason") == "none", "passing WebGPU route must use fallbackReason none")
        require(route_gpu.get("supportClaim") is True, "passing WebGPU route must claim support in route")
        require(stats.get("supportClaim") is True, "passing stats must claim support")
        require(float(route_gpu.get("similarity", 0.0)) >= GPU_THRESHOLD, "passing WebGPU route below threshold")
        require_png(ARTIFACT_DIR / "gpu.png")
        require_png(ARTIFACT_DIR / "gpu-diff.png")
    else:
        require(route_gpu.get("fallbackReason") == FALLBACK_REASON, "unsupported WebGPU route must keep stable fallback")
        require(route_gpu.get("supportClaim") is False, "unsupported WebGPU route must not claim support")
        require(stats.get("supportClaim") is False, "unsupported stats must not claim support")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-artifacts", action="store_true", help="require generated PNG/routes/stats artifacts")
    args = parser.parse_args()

    evidence = load_json(EVIDENCE)
    require_scope(require_artifacts=args.require_artifacts)
    require_inputs()
    require_m66_policy()
    require_test_file()
    require_evidence(evidence)
    require_report()
    if args.require_artifacts:
        require_artifacts()
    print(
        f"{TICKET} validation passed: {ROW_ID} harness=present "
        f"artifactMode={'required' if args.require_artifacts else 'structural'} status=expected-unsupported-by-default"
    )


if __name__ == "__main__":
    main()

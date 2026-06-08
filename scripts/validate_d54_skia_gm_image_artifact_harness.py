#!/usr/bin/env python3
"""Validate D54-1 ImageGM row-specific artifact harness evidence."""

from __future__ import annotations

import json
import struct
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D54-1"
ROW_ID = "skia-gm-image"
SCENE_ID = "d54-skia-gm-image"
FALLBACK_REASON = "image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required"
SOURCE_DRAFT = "global/kanvas/tickets/drafts/brouillon-ticket-d54-1-ajouter-un-harnais-artefacts-row-specific-pour-skia-gm-image"
GPU_THRESHOLD = 99.95
CPU_THRESHOLD = 98.0

TEST_FILE = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ImageGmSceneCaptureTest.kt"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-d54-1-skia-gm-image-artifact-harness.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d54-skia-gm-image-artifact-harness.json"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image"
GM_FILE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageGM.kt"
CPU_TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/ImageGMTest.kt"
REFERENCE = ROOT / "skia-integration-tests/src/test/resources/original-888/image-surface.png"
D51_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d51-2-imagegm-row-specific-evidence.md"
D51_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-imagegm-row-specific-evidence.json"
FOR466_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md"
FOR466_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json"

EXPECTED_CHANGED_FILES = {
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ImageGmSceneCaptureTest.kt",
    "reports/wgsl-pipeline/2026-06-08-d54-1-skia-gm-image-artifact-harness.md",
    "reports/wgsl-pipeline/scenes/generated/d54-skia-gm-image-artifact-harness.json",
    "scripts/validate_d54_skia_gm_image_artifact_harness.py",
}
EXPECTED_ARTIFACT_FILES = {
    "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image/skia.png",
    "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image/cpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image/cpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image/gpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image/gpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image/route-cpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image/route-gpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image/stats.json",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d51-imagegm-row-specific-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/d53-dashboard-visibility-reconcile.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "cpu-raster/",
    "render-pipeline/",
    "skia-integration-tests/",
)
VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ImageGmSceneCaptureTest",
    "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.ImageGmSceneCaptureTest",
    "rtk python3 scripts/validate_d54_skia_gm_image_artifact_harness.py",
    "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d54-skia-gm-image-artifact-harness.json",
    "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d54-image-pycache python3 -m py_compile scripts/validate_d54_skia_gm_image_artifact_harness.py",
    "rtk git diff --check",
]
FEATURE_NON_CLAIMS = {
    "codecSupportClaim",
    "yuvSupportClaim",
    "animationSupportClaim",
    "exifSupportClaim",
    "mipmapSupportClaim",
    "tileModeSupportClaim",
    "colorManagedImageDecodeSupportClaim",
    "arbitraryImageDecodeSupportClaim",
    "broadImageSupportClaim",
}
NEIGHBOR_NON_CLAIMS = {
    "neighborEvidenceInherited",
    "bitmappremulEvidenceInherited",
    "drawbitmaprectEvidenceInherited",
    "drawminibitmaprectEvidenceInherited",
    "imageSourceEvidenceInherited",
}


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
    changed.discard("reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image")
    return changed


def require_scope() -> None:
    changed = git_changed_paths()
    allowed = set(EXPECTED_CHANGED_FILES) | EXPECTED_ARTIFACT_FILES
    unexpected = sorted(path for path in changed if path not in allowed)
    require(not unexpected, f"unexpected changed files: {unexpected}")
    missing_core = sorted(
        path for path in EXPECTED_CHANGED_FILES
        if path not in changed and not (ROOT / path).is_file()
    )
    require(not missing_core, f"missing core D54-1 files: {missing_core}")
    missing_artifacts = sorted(path for path in EXPECTED_ARTIFACT_FILES if not (ROOT / path).is_file())
    require(not missing_artifacts, f"missing required artifact files: {missing_artifacts}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard or previous evidence changed: {forbidden_paths}")
    forbidden_prefixes = sorted(
        path for path in changed
        if path.startswith(FORBIDDEN_PREFIXES) and path not in EXPECTED_CHANGED_FILES
    )
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def read_png_header(path: Path) -> tuple[int, int, int, int]:
    require(path.is_file(), f"missing PNG file: {rel(path)}")
    with path.open("rb") as handle:
        header = handle.read(33)
    require(header.startswith(b"\x89PNG\r\n\x1a\n"), f"{rel(path)} is not a PNG")
    require(header[12:16] == b"IHDR", f"{rel(path)} missing IHDR chunk")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", header[16:26])
    return width, height, bit_depth, color_type


def require_inputs() -> None:
    for path in (GM_FILE, CPU_TEST, REFERENCE, D51_REPORT, D51_EVIDENCE, FOR466_REPORT, FOR466_EVIDENCE):
        require(path.is_file(), f"missing required input: {rel(path)}")

    source = GM_FILE.read_text(encoding="utf-8")
    for phrase in (
        'override fun getName(): String = "image-surface"',
        "override fun getISize(): SkISize = SkISize.Make(960, 1200)",
        "SkImageInfo.MakeN32(K_W, K_H, SkAlphaType.kPremul)",
        "val surf0 = SkSurface.MakeRaster(info)",
        "val surf1 = SkSurface.MakeRaster(info)",
        "canvas.drawImage(imgR, 0f, 0f, sampling, paint)",
        "surf.draw(canvas, 0f, 160f, paint)",
        "canvas.drawImageRect(imgR, src1, dst1, sampling, paint)",
        "GPU column intentionally left blank",
        "const val K_W: Int = 64",
        "const val K_H: Int = 64",
    ):
        require(phrase in source, f"ImageGM source missing phrase: {phrase}")

    test = CPU_TEST.read_text(encoding="utf-8")
    require("class ImageGMTest" in test, "ImageGMTest class missing")
    require("TestUtils.loadReferenceBitmap(gm.name())" in test, "ImageGMTest must load reference by GM name")
    require('SimilarityTracker.updateScore("ImageGM", comparison.similarity)' in test, "ImageGM score update missing")

    width, height, bit_depth, color_type = read_png_header(REFERENCE)
    require((width, height) == (960, 1200), "image-surface.png dimensions must be 960x1200")
    require(bit_depth == 16 and color_type == 6, "image-surface.png must be PNG RGBA 16-bit/color")

    d51 = load_json(D51_EVIDENCE)
    require(d51.get("ticket") == "D51-2", "D51 evidence ticket mismatch")
    require(d51.get("inventoryId") == ROW_ID, "D51 evidence inventory mismatch")
    require(d51.get("status") == "expected-unsupported", "D51 evidence status changed")
    require(d51.get("fallbackReason") == FALLBACK_REASON, "D51 fallback mismatch")
    require(d51.get("rowSpecificAudit", {}).get("adjacentImageEvidenceInherited") is False, "D51 must forbid adjacent evidence inheritance")

    for466 = load_json(FOR466_EVIDENCE)
    row = for466.get("row", {})
    require(row.get("inventoryId") == ROW_ID, "FOR-466 inventory mismatch")
    require(row.get("status") == "expected-unsupported", "FOR-466 status mismatch")
    require(row.get("fallbackReason") == "image.imagegm.row-specific-artifacts-required", "FOR-466 fallback mismatch")
    provenance = row.get("decodeFixtureProvenance", {})
    require(provenance.get("externalEncodedFixture") == "none", "FOR-466 must not claim external encoded fixture")
    require("not exercised" in provenance.get("decodePath", ""), "FOR-466 decode path mismatch")


def require_test_file() -> None:
    require(TEST_FILE.is_file(), f"missing test file: {rel(TEST_FILE)}")
    text = TEST_FILE.read_text(encoding="utf-8")
    required = (
        "class ImageGmSceneCaptureTest",
        "ImageGM()",
        "TestUtils.loadReferenceBitmap(gm.name())",
        "TestUtils.runGmTest(gm)",
        "WebGpuSink.draw(ctx, gm)",
        'System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true"',
        "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image",
        "image-surface.png",
        "skia.png",
        "cpu.png",
        "cpu-diff.png",
        "gpu.png",
        "gpu-diff.png",
        "route-cpu.json",
        "route-gpu.json",
        "stats.json",
        FALLBACK_REASON,
        "neighborEvidenceInherited",
        "broadImageDecodeClaim",
        "GPU_PROMOTION_THRESHOLD = 99.95",
    )
    for phrase in required:
        require(phrase in text, f"test file missing phrase: {phrase}")
    require("@Disabled" not in text, "capture test itself must not be disabled")


def require_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("sourceDraftMemory") == SOURCE_DRAFT, "source draft memory mismatch")
    require(evidence.get("classification") == "artifact-harness-added-no-dashboard-promotion", "classification mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory mismatch")
    require(evidence.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(evidence.get("status") == "expected-unsupported", "static evidence must remain expected-unsupported")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback reason mismatch")
    require(set(evidence.get("expectedChangedFiles", [])) == EXPECTED_CHANGED_FILES, "expectedChangedFiles mismatch")
    require(set(evidence.get("expectedArtifactFiles", [])) == EXPECTED_ARTIFACT_FILES, "expectedArtifactFiles mismatch")
    require(evidence.get("validationCommands") == VALIDATION_COMMANDS, "validation commands mismatch")

    harness = evidence.get("harness", {})
    require(harness.get("testFile") == rel(TEST_FILE), "harness test file mismatch")
    require(harness.get("gmClass") == "org.skia.tests.ImageGM", "GM class mismatch")
    require(harness.get("referenceName") == "image-surface", "reference name mismatch")
    require(harness.get("cpuRenderer") == "TestUtils.runGmTest(ImageGM())", "CPU renderer mismatch")
    require(harness.get("webgpuRenderer") == "WebGpuSink.draw(ctx, ImageGM())", "WebGPU renderer mismatch")
    require(harness.get("artifactDir") == rel(ARTIFACT_DIR), "artifact dir mismatch")
    require(harness.get("cpuMinimumSimilarity") == CPU_THRESHOLD, "CPU threshold mismatch")
    require(harness.get("gpuPromotionThreshold") == GPU_THRESHOLD, "GPU threshold mismatch")
    require(harness.get("globalThresholdChangeAllowed") is False, "global threshold change must be forbidden")
    require(harness.get("globalDashboardPromotionAllowedByD54_1") is False, "dashboard promotion must be forbidden")
    require(harness.get("neighborEvidenceInheritanceAllowed") is False, "neighbor inheritance must be forbidden")

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
    require(gate.get("globalDashboardPromotionRequiresSeparateTicket") is True, "dashboard promotion must require separate ticket")
    require(gate.get("keepExpectedUnsupportedIfAnyArtifactMissing") is True, "missing artifact must keep unsupported")
    require(gate.get("stableUnsupportedReason") == FALLBACK_REASON, "stable unsupported reason mismatch")

    artifact_run = evidence.get("localArtifactRun", {})
    require(artifact_run.get("result") == "pass", "local artifact run result mismatch")
    require(artifact_run.get("artifactsWritten") is True, "local artifact run must record artifacts")
    require(artifact_run.get("artifactDir") == rel(ARTIFACT_DIR), "local artifact dir mismatch")
    require(artifact_run.get("cpu", {}).get("status") == "pass", "local CPU status mismatch")
    require(float(artifact_run.get("cpu", {}).get("similarity", 0.0)) >= CPU_THRESHOLD, "local CPU below threshold")
    require(artifact_run.get("webgpu", {}).get("status") == "expected-unsupported", "local WebGPU status mismatch")
    require(artifact_run.get("webgpu", {}).get("fallbackReason") == FALLBACK_REASON, "local WebGPU fallback mismatch")
    require(artifact_run.get("webgpu", {}).get("supportClaim") is False, "local WebGPU support claim must be false")
    require(artifact_run.get("webgpu", {}).get("globalDashboardPromoted") is False, "local run must not promote dashboard")
    require(float(artifact_run.get("webgpu", {}).get("similarity", 100.0)) < GPU_THRESHOLD, "local WebGPU must remain below threshold")

    boundary = evidence.get("rowSpecificBoundary", {})
    require(boundary.get("neighborEvidenceInherited") is False, "neighbor inheritance must be false")
    forbidden = set(boundary.get("forbiddenInheritedEvidence", []))
    require({"skia-gm-bitmappremul", "skia-gm-drawbitmaprect", "skia-gm-drawminibitmaprect", "skia-gm-imagesource"}.issubset(forbidden), "missing forbidden neighbor list")
    source = boundary.get("imageGmSource", {})
    require(source.get("gmName") == "image-surface", "GM name mismatch")
    require(source.get("externalEncodedFixture") == "none", "external fixture must be none")
    require("not exercised" in source.get("decodePath", ""), "decode path must be non-exercised")

    score = evidence.get("scoreImpact", {})
    require(score.get("supportScoreIncreased") is False, "support score must not increase")
    require(score.get("skiaComparableScoreIncreased") is False, "Skia-comparable score must not increase")
    require(score.get("globalDashboardPromoted") is False, "dashboard must not be promoted")

    non_claims = evidence.get("nonClaims", {})
    for key, value in non_claims.items():
        require(value is False, f"{key} must remain false")
    for key in FEATURE_NON_CLAIMS | NEIGHBOR_NON_CLAIMS:
        require(non_claims.get(key) is False, f"missing or true non-claim: {key}")


def require_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    required = (
        "D54-1 ajoute le harness de capture row-specific",
        "`skia-gm-image` reste `expected-unsupported`",
        "WebGPU produit un bitmap",
        "`98.5962%`",
        "`fallbackReason=none` reste interdit",
        "La promotion du",
        "dashboard global reste hors scope",
        FALLBACK_REASON,
        "TestUtils.runGmTest",
        "WebGpuSink.draw",
        "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.ImageGmSceneCaptureTest",
        "`ImageGM` est la GM `image-surface`",
        "`bitmappremul`, `drawbitmaprect`, `drawminibitmaprect`",
        "Aucun gain de score n'est revendique",
        "codec, YUV, animation, EXIF, mipmap, tile-mode",
    )
    for phrase in required:
        require(phrase in text, f"report missing phrase: {phrase}")
    for command in VALIDATION_COMMANDS:
        require(command in text, f"report missing validation command: {command}")


def require_png_artifact(path: Path) -> None:
    width, height, bit_depth, color_type = read_png_header(path)
    require((width, height) == (960, 1200), f"{rel(path)} dimensions must be 960x1200")
    require(bit_depth == 8 and color_type == 6, f"{rel(path)} must be PNG RGBA 8-bit/color")


def require_artifacts() -> None:
    for name in ("skia.png", "cpu.png", "cpu-diff.png", "gpu.png", "gpu-diff.png"):
        require_png_artifact(ARTIFACT_DIR / name)

    route_cpu = load_json(ARTIFACT_DIR / "route-cpu.json")
    route_gpu = load_json(ARTIFACT_DIR / "route-gpu.json")
    stats = load_json(ARTIFACT_DIR / "stats.json")

    require(route_cpu.get("sceneId") == SCENE_ID, "route-cpu scene mismatch")
    require(route_cpu.get("inventoryId") == ROW_ID, "route-cpu inventory mismatch")
    require(route_cpu.get("backend") == "CPU", "route-cpu backend mismatch")
    require(route_cpu.get("status") == "pass", "CPU route must pass")
    require(route_cpu.get("selectedRoute") == "cpu.raster.dm-reference-colorspace.imagegm-surface-snapshot", "CPU route mismatch")
    require(route_cpu.get("fallbackReason") == "none", "CPU route fallback must be none")
    require(float(route_cpu.get("similarity", 0.0)) >= CPU_THRESHOLD, "CPU route below threshold")

    require(route_gpu.get("sceneId") == SCENE_ID, "route-gpu scene mismatch")
    require(route_gpu.get("inventoryId") == ROW_ID, "route-gpu inventory mismatch")
    require(route_gpu.get("backend") == "WebGPU", "route-gpu backend mismatch")
    require(route_gpu.get("globalDashboardPromoted") is False, "route-gpu must not promote dashboard")
    require(route_gpu.get("globalThresholdChanged") is False, "route-gpu must not change global threshold")
    require(route_gpu.get("neighborEvidenceInherited") is False, "route-gpu must not inherit neighbors")
    require(route_gpu.get("broadImageDecodeClaim") is False, "route-gpu must not claim broad decode")
    require(stats.get("globalDashboardPromoted") is False, "stats must not promote dashboard")
    require(stats.get("globalThresholdChanged") is False, "stats must not change global threshold")
    require(stats.get("neighborEvidenceInherited") is False, "stats must not inherit neighbors")
    require(stats.get("broadImageDecodeClaim") is False, "stats must not claim broad decode")

    status = route_gpu.get("status")
    require(status in {"pass", "expected-unsupported"}, f"route-gpu invalid status: {status}")
    require(stats.get("status") == status, "stats status must match route-gpu")
    if status == "pass":
        require(route_gpu.get("fallbackReason") == "none", "passing WebGPU route must use fallbackReason none")
        require(route_gpu.get("supportClaim") is True, "passing WebGPU route must claim support in route")
        require(stats.get("supportClaim") is True, "passing stats must claim support")
        require(float(route_gpu.get("similarity", 0.0)) >= GPU_THRESHOLD, "passing WebGPU route below threshold")
        require(stats.get("globalDashboardPromoted") is False, "D54-1 passing route still must not promote dashboard")
    else:
        require(route_gpu.get("fallbackReason") == FALLBACK_REASON, "unsupported WebGPU route must keep stable fallback")
        require(route_gpu.get("supportClaim") is False, "unsupported WebGPU route must not claim support")
        require(stats.get("supportClaim") is False, "unsupported stats must not claim support")
        require(float(route_gpu.get("similarity", 100.0)) < GPU_THRESHOLD, "unsupported WebGPU route must be below threshold")


def main() -> None:
    evidence = load_json(EVIDENCE)
    require_scope()
    require_inputs()
    require_test_file()
    require_evidence(evidence)
    require_report()
    require_artifacts()
    print(f"{TICKET} validation passed: {ROW_ID} harness=present artifacts=present status=expected-unsupported")


if __name__ == "__main__":
    main()

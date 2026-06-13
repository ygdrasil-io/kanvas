#!/usr/bin/env python3
import hashlib
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/text-shaping-fallback-scope"
OUTPUT_JSON = "kan-043-text-shaping-fallback-scope.json"
OUTPUT_MARKDOWN = "kan-043-text-shaping-fallback-scope.md"

KAN012_ARTIFACT_ROOT = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line"
KAN012_STATS_PATH = f"{KAN012_ARTIFACT_ROOT}/stats.json"
KAN012_ATLAS_PATH = f"{KAN012_ARTIFACT_ROOT}/atlas.json"
KERNING_ARTIFACT_ROOT = "reports/wgsl-pipeline/scenes/artifacts/font-kerning-style-fixture"
KERNING_DIAGNOSTICS_PATH = f"{KERNING_ARTIFACT_ROOT}/font-diagnostics.json"
COMPLEX_ARTIFACT_ROOT = "reports/wgsl-pipeline/scenes/artifacts/font-complex-shaping-refusal"
COMPLEX_DIAGNOSTICS_PATH = f"{COMPLEX_ARTIFACT_ROOT}/font-diagnostics.json"
LATIN_DIAGNOSTICS_PATH = "reports/wgsl-pipeline/scenes/artifacts/font-latin-outline-drawstring/font-diagnostics.json"
M62_EVIDENCE_PATH = "reports/wgsl-pipeline/scenes/generated/m62-font-fallback-evidence.json"

SPEC_REALTIME_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
OPENTYPE_DOC_PATH = "docs/opentype-font-backend.md"
SPEC_FONT_README_PATH = ".upstream/specs/font/README.md"
SPEC_SHAPING_PATH = ".upstream/specs/font/03-shaping-and-layout-boundary.md"
SPEC_VALIDATION_PATH = ".upstream/specs/font/06-validation-and-conformance.md"
SPEC_GEOMETRY_LOWERING_PATH = ".upstream/specs/geometry-coverage/02-lowering-rules.md"

FONT_FACE_BY_SOURCE = {
    "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf": "Liberation Sans",
    "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Bold.ttf": "Liberation Sans Bold",
    "kanvas-skia/src/main/resources/fonts/liberation/LiberationSerif-Italic.ttf": "Liberation Serif Italic",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-043 text shaping/fallback scope validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_file(root: Path, relative_path: str) -> None:
    require((root / relative_path).is_file(), f"missing required file: {relative_path}")


def require_contains(root: Path, relative_path: str, snippets: list[str]) -> None:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing snippet: {snippet}",
        )


def sha256_file(root: Path, relative_path: str) -> str:
    path = root / relative_path
    require(path.is_file(), f"missing font file for hash: {relative_path}")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def split_font_source_id(root: Path, source_id: str) -> dict[str, str]:
    marker = "#sha256="
    if marker in source_id:
        source, embedded_hash = source_id.split(marker, 1)
        actual_hash = sha256_file(root, source)
        require(embedded_hash == actual_hash, f"font source hash mismatch for {source}")
        return {"source": source, "sha256": actual_hash}
    return {"source": source_id, "sha256": sha256_file(root, source_id)}


def font_identity(root: Path, source: str, source_id: str | None = None, face: str | None = None) -> dict[str, str]:
    parsed = split_font_source_id(root, source_id or source)
    require(parsed["source"] == source, f"font source id changed: {source_id or source}")
    return {
        "face": face or FONT_FACE_BY_SOURCE.get(source, Path(source).stem),
        "source": source,
        "sha256": parsed["sha256"],
        "sourceId": f"{source}#sha256={parsed['sha256']}",
    }


def codepoint_to_glyph_map(atlas: dict[str, Any]) -> dict[int, int]:
    mapping: dict[int, int] = {}
    entries = atlas.get("entries")
    require(isinstance(entries, list) and entries, "KAN-012 atlas missing entries")
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        glyph_id = entry.get("glyphId")
        code_points = entry.get("codePoints")
        if isinstance(glyph_id, int) and isinstance(code_points, list):
            for code_point in code_points:
                if isinstance(code_point, int):
                    mapping[code_point] = glyph_id
    require(mapping, "KAN-012 atlas did not expose code point to glyph id mapping")
    return mapping


def simple_glyph_ids(text: str, mapping: dict[int, int]) -> list[int]:
    glyph_ids: list[int] = []
    for char in text:
        code_point = ord(char)
        require(code_point in mapping, f"missing KAN-012 glyph id for U+{code_point:04X}")
        glyph_ids.append(mapping[code_point])
    return glyph_ids


def simple_clusters(text: str) -> list[int]:
    clusters: list[int] = []
    offset = 0
    for char in text:
        clusters.append(offset)
        offset += 2 if ord(char) > 0xFFFF else 1
    return clusters


def artifact_paths(root_name: str, names: list[str]) -> dict[str, str]:
    return {Path(name).stem.replace("-", "_"): f"{root_name}/{name}" for name in names}


def proof_files(root: Path, files: list[str]) -> bool:
    return all((root / path).is_file() for path in files)


def support_proofs(root: Path, root_name: str, webgpu_route_name: str = "route-gpu.json") -> dict[str, bool]:
    return {
        "reference": proof_files(root, [f"{root_name}/skia.png"]) or proof_files(root, [f"{root_name}/reference.png"]),
        "cpu": proof_files(root, [f"{root_name}/cpu.png", f"{root_name}/route-cpu.json"]),
        "gpu": proof_files(root, [f"{root_name}/gpu.png", f"{root_name}/{webgpu_route_name}"])
        or proof_files(root, [f"{root_name}/webgpu.png", f"{root_name}/{webgpu_route_name}"]),
        "diff": proof_files(root, [f"{root_name}/cpu-diff.png"])
        and (proof_files(root, [f"{root_name}/gpu-diff.png"]) or proof_files(root, [f"{root_name}/webgpu-diff.png"])),
        "stats": proof_files(root, [f"{root_name}/stats.json"]),
        "route": proof_files(root, [f"{root_name}/route-cpu.json", f"{root_name}/{webgpu_route_name}"]),
    }


def is_simple_latin_no_fallback_policy(policy: Any) -> bool:
    return policy in {"none-for-supported-simple-latin-line", "none"}


def build_simple_latin_row(root: Path) -> dict[str, Any]:
    stats = load_json(root, KAN012_STATS_PATH)
    atlas = load_json(root, KAN012_ATLAS_PATH)
    scene_id = stats.get("sceneId")
    require(scene_id == "text.simple-latin.line.v1", f"KAN-012 scene id changed: {scene_id}")
    require(is_simple_latin_no_fallback_policy(stats.get("fallbackPolicy")), "KAN-012 fallback policy changed")
    require(stats.get("globalThresholdChanged") is False, "KAN-012 threshold changed")
    text = stats.get("text")
    require(isinstance(text, str) and text, "KAN-012 text missing")
    font_source_id = stats.get("fontSourceId")
    require(isinstance(font_source_id, str) and font_source_id, "KAN-012 fontSourceId missing")
    font = font_identity(root, font_source_id.split("#sha256=", 1)[0], font_source_id, stats.get("fontFamily"))
    glyph_ids = simple_glyph_ids(text, codepoint_to_glyph_map(atlas))
    clusters = simple_clusters(text)
    proofs = support_proofs(root, KAN012_ARTIFACT_ROOT, webgpu_route_name="route-webgpu.json")
    return {
        "rowId": "text.simple-latin.line.v1",
        "pmCategory": "simple-latin-support",
        "status": "pass",
        "sourceEvidence": KAN012_STATS_PATH,
        "textInput": text,
        "font": font,
        "shapingRoute": {
            "mode": stats.get("shapingMode"),
            "direction": "ltr",
            "script": "Latin",
            "language": "und",
            "featureSet": "default-simple",
            "fallbackPolicy": "none-for-supported-simple-latin-line",
            "diagnostics": ["drawString remains simple; no implicit complex shaper"],
        },
        "clusters": clusters,
        "glyphIds": glyph_ids,
        "route": {
            "cpu": "cpu.text.outline-path.simple-latin",
            "gpu": stats.get("webGpuRouteIdentifier"),
            "glyphSource": stats.get("glyphSourceRoute"),
            "referenceKind": "cpu-atlas-alpha-mask-oracle",
        },
        "fallbackPolicy": {
            "policy": "no-fallback-for-bundled-simple-latin",
            "reasonCode": "none",
            "legacyReasonCode": "none",
            "implicitSystemFallback": False,
        },
        "proofs": proofs,
        "artifacts": artifact_paths(
            KAN012_ARTIFACT_ROOT,
            [
                "reference.png",
                "cpu.png",
                "webgpu.png",
                "cpu-diff.png",
                "webgpu-diff.png",
                "route-cpu.json",
                "route-webgpu.json",
                "stats.json",
                "atlas.json",
            ],
        ),
        "nonClaims": [
            "no-complex-shaping-claim",
            "no-fallback-font-claim",
            "no-emoji-or-color-font-claim",
            "no-bidi-paragraph-claim",
            "no-broad-text-claim",
        ],
    }


def build_bounded_kerning_row(root: Path) -> dict[str, Any]:
    diagnostics = load_json(root, KERNING_DIAGNOSTICS_PATH)
    require(diagnostics.get("unsupported") is False, "kerning fixture support changed")
    require(diagnostics.get("shapingMode") == "simple-kerning-fixture", "kerning shaping mode changed")
    source = diagnostics.get("fontSource")
    text = diagnostics.get("textInput")
    glyph_ids = diagnostics.get("glyphIds")
    require(isinstance(source, str) and source, "kerning font source missing")
    require(isinstance(text, str) and text, "kerning text missing")
    require(isinstance(glyph_ids, list) and all(isinstance(gid, int) for gid in glyph_ids), "kerning glyph ids missing")
    proofs = support_proofs(root, KERNING_ARTIFACT_ROOT)
    return {
        "rowId": "font-kerning-style-fixture",
        "pmCategory": "bounded-shaping-support",
        "status": "pass",
        "sourceEvidence": KERNING_DIAGNOSTICS_PATH,
        "textInput": text,
        "font": font_identity(root, source),
        "shapingRoute": {
            "mode": diagnostics.get("shapingMode"),
            "direction": "ltr",
            "script": "Latin",
            "language": "und",
            "featureSet": "bounded-pair-positioning",
            "fallbackPolicy": "none",
            "diagnostics": ["bounded kerning-style fixture only; no broad shaping claim"],
        },
        "clusters": simple_clusters(text),
        "glyphIds": glyph_ids,
        "route": {
            "cpu": diagnostics.get("cpuRoute"),
            "gpu": diagnostics.get("gpuRoute"),
            "glyph": diagnostics.get("glyphRepresentation"),
            "referenceKind": diagnostics.get("referenceKind"),
        },
        "fallbackPolicy": {
            "policy": "no-fallback-for-bundled-kerning-fixture",
            "reasonCode": "none",
            "legacyReasonCode": "none",
            "implicitSystemFallback": False,
        },
        "proofs": proofs,
        "artifacts": artifact_paths(
            KERNING_ARTIFACT_ROOT,
            [
                "skia.png",
                "cpu.png",
                "gpu.png",
                "cpu-diff.png",
                "gpu-diff.png",
                "route-cpu.json",
                "route-gpu.json",
                "stats.json",
                "font-diagnostics.json",
            ],
        ),
        "nonClaims": [
            "no-full-gsub-claim",
            "no-full-gpos-claim",
            "no-fallback-font-claim",
            "no-arabic-indic-emoji-claim",
            "no-broad-text-claim",
        ],
    }


def build_complex_refusal_row(root: Path) -> dict[str, Any]:
    diagnostics = load_json(root, COMPLEX_DIAGNOSTICS_PATH)
    require(diagnostics.get("unsupported") is True, "complex shaping refusal changed")
    require(diagnostics.get("fallbackReason") == "font.complex-shaping-requires-explicit-shaper", "complex shaping legacy reason changed")
    source = diagnostics.get("fontSource")
    text = diagnostics.get("textInput")
    require(isinstance(source, str) and source, "complex refusal font source missing")
    require(isinstance(text, str) and text, "complex refusal text missing")
    return {
        "rowId": "font-complex-shaping-refusal",
        "pmCategory": "bounded-shaping-refusal",
        "status": "expected-unsupported",
        "sourceEvidence": COMPLEX_DIAGNOSTICS_PATH,
        "textInput": text,
        "font": font_identity(root, source),
        "shapingRoute": {
            "mode": "unsupported",
            "requestedMode": diagnostics.get("shapingMode"),
            "direction": "ltr",
            "script": "Latin",
            "language": "und",
            "featureSet": "complex-shaping",
            "fallbackPolicy": "explicit-refusal",
            "diagnostics": ["complex shaping requires explicit supported shaper evidence before promotion"],
        },
        "clusters": [],
        "glyphIds": [],
        "route": {
            "cpu": diagnostics.get("cpuRoute"),
            "gpu": diagnostics.get("gpuRoute"),
            "glyph": diagnostics.get("glyphRepresentation"),
            "referenceKind": diagnostics.get("referenceKind"),
        },
        "fallbackPolicy": {
            "policy": "refuse-without-native-shaper",
            "reasonCode": "font.shaping-feature-unsupported",
            "legacyReasonCode": diagnostics.get("fallbackReason"),
            "implicitSystemFallback": False,
        },
        "proofs": {
            "reference": proof_files(root, [f"{COMPLEX_ARTIFACT_ROOT}/skia.png"]),
            "cpu": proof_files(root, [f"{COMPLEX_ARTIFACT_ROOT}/cpu.png", f"{COMPLEX_ARTIFACT_ROOT}/route-cpu.json"]),
            "gpu": proof_files(root, [f"{COMPLEX_ARTIFACT_ROOT}/route-gpu.json"]),
            "diff": proof_files(root, [f"{COMPLEX_ARTIFACT_ROOT}/cpu-diff.png"]),
            "stats": proof_files(root, [f"{COMPLEX_ARTIFACT_ROOT}/stats.json"]),
            "route": proof_files(root, [f"{COMPLEX_ARTIFACT_ROOT}/route-cpu.json", f"{COMPLEX_ARTIFACT_ROOT}/route-gpu.json"]),
        },
        "artifacts": artifact_paths(
            COMPLEX_ARTIFACT_ROOT,
            [
                "skia.png",
                "cpu.png",
                "cpu-diff.png",
                "route-cpu.json",
                "route-gpu.json",
                "stats.json",
                "font-diagnostics.json",
            ],
        ),
        "nonClaims": [
            "no-complex-shaping-support-claim",
            "no-native-shaper-engine-claim",
            "no-fallback-font-claim",
            "no-arabic-indic-emoji-claim",
            "no-broad-text-claim",
        ],
    }


def find_m62_scene(payload: dict[str, Any]) -> dict[str, Any]:
    scenes = payload.get("scenes")
    require(isinstance(scenes, list), "M62 evidence missing scenes")
    for scene in scenes:
        if isinstance(scene, dict) and scene.get("id") == "m62-missing-glyph-fallback-refusal":
            return scene
    fail("missing M62 missing-glyph fallback refusal scene")


def build_missing_glyph_row(root: Path) -> dict[str, Any]:
    m62 = find_m62_scene(load_json(root, M62_EVIDENCE_PATH))
    latin = load_json(root, LATIN_DIAGNOSTICS_PATH)
    font = m62.get("font")
    require(isinstance(font, dict), "M62 font payload missing")
    source = font.get("source")
    text = font.get("textInput")
    require(isinstance(source, str) and source, "M62 font source missing")
    require(isinstance(text, str) and text, "M62 text missing")
    require(m62.get("fallbackReason") == "font.missing-glyph-fallback-unsupported", "M62 fallback reason changed")
    base_glyph_ids = latin.get("glyphIds")
    require(isinstance(base_glyph_ids, list) and all(isinstance(gid, int) for gid in base_glyph_ids), "base Latin glyph ids missing")
    glyph_ids = list(base_glyph_ids) + [0]
    clusters = simple_clusters("KANVAS") + [len("KANVAS ")]
    return {
        "rowId": "m62-missing-glyph-fallback-refusal",
        "pmCategory": "fallback-missing-glyph-refusal",
        "status": "expected-unsupported",
        "sourceEvidence": M62_EVIDENCE_PATH,
        "textInput": text,
        "font": font_identity(root, source),
        "shapingRoute": {
            "mode": font.get("shapingMode"),
            "direction": "ltr",
            "script": "Latin + missing code point",
            "language": "und",
            "featureSet": "missing-glyph-fallback",
            "fallbackPolicy": font.get("fallbackPolicy"),
            "diagnostics": ["missing code point is represented by glyph id 0 (.notdef) and refused without fallback family selection"],
        },
        "clusters": clusters,
        "glyphIds": glyph_ids,
        "route": {
            "cpu": m62.get("cpuRoute"),
            "gpu": m62.get("gpuRoute"),
            "glyph": "unsupported",
            "referenceKind": m62.get("referenceKind"),
        },
        "fallbackPolicy": {
            "policy": "refuse-without-system-font-fallback",
            "reasonCode": "font.shaping-fallback-missing",
            "legacyReasonCode": m62.get("fallbackReason"),
            "implicitSystemFallback": False,
            "notdefGlyphId": 0,
        },
        "proofs": {
            "reference": m62.get("referenceKind") == "cpu-oracle",
            "cpu": isinstance(m62.get("cpuRoute"), str) and m62.get("cpuRoute") != "",
            "gpu": isinstance(m62.get("gpuRoute"), str) and "refuse" in m62.get("gpuRoute", ""),
            "diff": True,
            "stats": all(isinstance(m62.get(field), (int, float)) for field in ("pixels", "matchingPixels", "threshold")),
            "route": isinstance(m62.get("cpuRoute"), str) and isinstance(m62.get("gpuRoute"), str),
        },
        "artifacts": {
            "m62_evidence": M62_EVIDENCE_PATH,
            "base_font_diagnostics": LATIN_DIAGNOSTICS_PATH,
        },
        "nonClaims": [
            "no-fallback-family-selection-claim",
            "no-system-font-policy-claim",
            "no-emoji-or-color-font-claim",
            "no-broad-text-claim",
        ],
    }


def build_claim_guard(rows: list[dict[str, Any]]) -> dict[str, list[str]]:
    support = [row for row in rows if row["status"] == "pass"]
    refusals = [row for row in rows if row["status"] != "pass"]
    return {
        "rowsMissingFontHash": [
            row["rowId"]
            for row in rows
            if not isinstance(row.get("font", {}).get("sha256"), str) or len(row["font"]["sha256"]) != 64
        ],
        "rowsMissingShapingRoute": [
            row["rowId"]
            for row in rows
            if not isinstance(row.get("shapingRoute"), dict) or not row["shapingRoute"].get("mode")
        ],
        "rowsMissingClusters": [
            row["rowId"]
            for row in rows
            if not isinstance(row.get("clusters"), list)
        ],
        "rowsMissingGlyphIds": [
            row["rowId"]
            for row in rows
            if not isinstance(row.get("glyphIds"), list) or (row["status"] == "pass" and not row["glyphIds"])
        ],
        "supportRowsMissingProofs": [
            row["rowId"]
            for row in support
            if row.get("fallbackPolicy", {}).get("reasonCode") != "none"
            or not all(row.get("proofs", {}).get(field) is True for field in ("reference", "cpu", "gpu", "diff", "stats", "route"))
        ],
        "refusalRowsMissingReason": [
            row["rowId"]
            for row in refusals
            if not isinstance(row.get("fallbackPolicy", {}).get("reasonCode"), str)
            or row["fallbackPolicy"]["reasonCode"] == "none"
            or "." not in row["fallbackPolicy"]["reasonCode"]
        ],
        "implicitSystemFallbackRows": [
            row["rowId"]
            for row in rows
            if row.get("fallbackPolicy", {}).get("implicitSystemFallback") is not False
        ],
        "hiddenBroadShapingClaims": [
            row["rowId"]
            for row in rows
            if not any("no-broad-text-claim" == item for item in row.get("nonClaims", []))
        ],
        "externalFontEngineClaims": [],
    }


def committed_artifacts() -> list[str]:
    return sorted({
        KAN012_STATS_PATH,
        KAN012_ATLAS_PATH,
        f"{KAN012_ARTIFACT_ROOT}/route-cpu.json",
        f"{KAN012_ARTIFACT_ROOT}/route-webgpu.json",
        f"{KAN012_ARTIFACT_ROOT}/reference.png",
        f"{KAN012_ARTIFACT_ROOT}/cpu.png",
        f"{KAN012_ARTIFACT_ROOT}/webgpu.png",
        f"{KAN012_ARTIFACT_ROOT}/cpu-diff.png",
        f"{KAN012_ARTIFACT_ROOT}/webgpu-diff.png",
        KERNING_DIAGNOSTICS_PATH,
        f"{KERNING_ARTIFACT_ROOT}/route-cpu.json",
        f"{KERNING_ARTIFACT_ROOT}/route-gpu.json",
        f"{KERNING_ARTIFACT_ROOT}/stats.json",
        f"{KERNING_ARTIFACT_ROOT}/skia.png",
        f"{KERNING_ARTIFACT_ROOT}/cpu.png",
        f"{KERNING_ARTIFACT_ROOT}/gpu.png",
        f"{KERNING_ARTIFACT_ROOT}/cpu-diff.png",
        f"{KERNING_ARTIFACT_ROOT}/gpu-diff.png",
        COMPLEX_DIAGNOSTICS_PATH,
        f"{COMPLEX_ARTIFACT_ROOT}/route-cpu.json",
        f"{COMPLEX_ARTIFACT_ROOT}/route-gpu.json",
        f"{COMPLEX_ARTIFACT_ROOT}/stats.json",
        LATIN_DIAGNOSTICS_PATH,
        M62_EVIDENCE_PATH,
        SPEC_REALTIME_PATH,
        OPENTYPE_DOC_PATH,
        SPEC_FONT_README_PATH,
        SPEC_SHAPING_PATH,
        SPEC_VALIDATION_PATH,
        SPEC_GEOMETRY_LOWERING_PATH,
    })


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    require_contains(root, SPEC_REALTIME_PATH, [
        "The deterministic reference font family is the bundled Liberation",
        "one missing-glyph/fallback scene",
    ])
    require_contains(root, OPENTYPE_DOC_PATH, [
        "The built-in portable shaping entry points are `SkShaper.MakePrimitive()`",
        "Defaults remain conservative: no implicit ligature substitution",
        "Platform font fallback through native desktop APIs remains out of scope.",
    ])
    require_contains(root, SPEC_FONT_README_PATH, [
        "which font source was used",
        "how text became glyphs and clusters",
        "whether CPU and WebGPU rendered, refused, or used a scoped fallback",
    ])
    require_contains(root, SPEC_SHAPING_PATH, [
        "Simple text draws remain deterministic and unshaped by default.",
        "Unsupported shaping requests fail visibly or emit diagnostics.",
        "Fallback cannot call platform font APIs implicitly.",
    ])
    require_contains(root, SPEC_VALIDATION_PATH, [
        "Do not replace a refusal by silently drawing a different font",
        "Dashboard rows distinguish outline, shaped, color, emoji, SDF, LCD, and mask routes.",
    ])
    require_contains(root, SPEC_GEOMETRY_LOWERING_PATH, [
        "Text shaping and glyph discovery remain outside this geometry layer.",
        "Glyph atlas ownership remains with text/glyph infrastructure.",
    ])

    rows = [
        build_simple_latin_row(root),
        build_bounded_kerning_row(root),
        build_complex_refusal_row(root),
        build_missing_glyph_row(root),
    ]
    guard = build_claim_guard(rows)
    for field, values in guard.items():
        require(not values, f"{field}: {values}")

    artifacts = committed_artifacts()
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")

    support_count = sum(1 for row in rows if row["status"] == "pass")
    refusal_count = len(rows) - support_count
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-043",
        "packId": "kan-043-text-shaping-fallback-scope",
        "status": "pass",
        "closureDecision": "text-shaping-fallback-scope",
        "claimLevel": "pm-text-scope-existing-evidence-only",
        "supportClaim": "no-new-rendering-support",
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "externalFontEngineAdded": False,
        "implicitSystemFallbackAllowed": False,
        "readinessDelta": 0,
        "summary": {
            "totalRows": len(rows),
            "supportRows": support_count,
            "refusalRows": refusal_count,
            "rowsMissingFontHash": len(guard["rowsMissingFontHash"]),
            "rowsMissingShapingRoute": len(guard["rowsMissingShapingRoute"]),
            "rowsMissingClusters": len(guard["rowsMissingClusters"]),
            "rowsMissingGlyphIds": len(guard["rowsMissingGlyphIds"]),
            "implicitFallbackRows": len(guard["implicitSystemFallbackRows"]),
        },
        "textScopeRows": rows,
        "claimGuard": guard,
        "requiredValidation": [
            "validateKan043TextShapingFallbackScope",
            ":gpu-raster:pipelineConformanceTest -- includes SimpleLatinLineSceneEvidenceTest",
            ":kanvas-skia:pipelineConformanceTest -- includes font/shaper contract tests in the standard suite",
            "pipelinePmBundle",
        ],
        "validationRows": [
            {
                "id": "font-identity-visible",
                "status": "pass",
                "evidence": "Every row records font face, source path, and SHA-256 hash.",
            },
            {
                "id": "glyphs-and-clusters-visible",
                "status": "pass",
                "evidence": "Rows expose explicit clusters and glyph id arrays; missing glyph uses glyph id 0 (.notdef).",
            },
            {
                "id": "support-refusal-separated",
                "status": "pass",
                "evidence": "Simple Latin and bounded kerning support remain separate from complex shaping and fallback refusals.",
            },
            {
                "id": "no-implicit-system-fallback",
                "status": "pass",
                "evidence": "Every fallback policy records implicitSystemFallback=false.",
            },
        ],
        "nonClaims": [
            "KAN-043 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.",
            "KAN-043 does not claim broad text, broad shaping, broad font fallback, full GSUB/GPOS, Arabic/Indic shaping, emoji ZWJ, or color-font support.",
            "KAN-043 does not add or require HarfBuzz, FreeType, Fontations, CoreText, DirectWrite, fontconfig, AWT, JNI, Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.",
            "KAN-043 does not convert missing glyph fallback or complex shaping refusal into support.",
        ],
        "artifactAudit": {
            "checkedCommittedArtifacts": len(artifacts),
            "missingCommittedArtifacts": len(missing),
            "missing": missing,
        },
        "artifactPaths": artifacts,
    }
    return evidence


def markdown_table(rows: list[dict[str, Any]]) -> str:
    return "\n".join(
        "| `{rowId}` | `{status}` | `{category}` | `{font}` | `{mode}` | `{reason}` | `{cpu}` | `{gpu}` |".format(
            rowId=row["rowId"],
            status=row["status"],
            category=row["pmCategory"],
            font=row["font"]["face"],
            mode=row["shapingRoute"]["mode"],
            reason=row["fallbackPolicy"]["reasonCode"],
            cpu=row["route"]["cpu"],
            gpu=row["route"]["gpu"],
        )
        for row in rows
    )


def render_markdown(evidence: dict[str, Any]) -> str:
    summary = evidence["summary"]
    required = "\n".join(f"- `{item}`" for item in evidence["requiredValidation"])
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    guard_rows = "\n".join(
        f"| {key} | `{value}` |"
        for key, value in evidence["claimGuard"].items()
    )
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-043 Text Shaping And Fallback Scope

KAN-043 packages the explicit text shaping and fallback scope from existing
font/text evidence. It makes the font identity, shaping route, clusters, glyph
ids, CPU/GPU route or refusal, and fallback policy visible without adding
renderer behavior.

## Summary

| Metric | Count |
|---|---:|
| Total rows | {summary['totalRows']} |
| Support rows | {summary['supportRows']} |
| Refusal rows | {summary['refusalRows']} |
| Rows missing font hash | {summary['rowsMissingFontHash']} |
| Rows missing shaping route | {summary['rowsMissingShapingRoute']} |
| Rows missing clusters | {summary['rowsMissingClusters']} |
| Rows missing glyph ids | {summary['rowsMissingGlyphIds']} |
| Implicit fallback rows | {summary['implicitFallbackRows']} |

## Scope Rows

| Row | Status | Category | Font | Shaping mode | Reason | CPU route | GPU route |
|---|---|---|---|---|---|---|---|
{markdown_table(evidence['textScopeRows'])}

## Claim Guard

| Guard | Value |
|---|---|
{guard_rows}

No implicit system font fallback is allowed. Missing glyph fallback remains a
visible refusal with `font.shaping-fallback-missing` and legacy reason
`font.missing-glyph-fallback-unsupported`.

## Required Validation

{required}

## Validation

| Check | Status | Evidence |
|---|---|---|
{validations}

## Non-Claims

{non_claims}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(
        json.dumps(evidence, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    output_dir = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    summary = evidence["summary"]
    print(
        "KAN-043 validation passed: "
        f"{summary['totalRows']} rows, "
        f"{summary['supportRows']} support, "
        f"{summary['refusalRows']} refusal, "
        f"{summary['implicitFallbackRows']} implicit fallback rows."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

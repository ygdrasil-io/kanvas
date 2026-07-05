#!/usr/bin/env python3
import json
import sys
from pathlib import Path


ALLOWED_STATUSES = {"supported", "partial", "expected-unsupported", "blocked-dependency"}
RENDERED_STATUSES = {"supported", "partial"}
REQUIRED_RENDERED_ARTIFACTS = {
    "reference",
    "cpu",
    "gpu",
    "cpuDiff",
    "gpuDiff",
    "routeCpu",
    "routeGpu",
    "stats",
}


def fail(message: str):
    raise SystemExit(f"MEP RC scene pack validation failed: {message}")


def require(condition: bool, message: str):
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str):
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_file(root: Path, relative_path: str):
    require(isinstance(relative_path, str) and relative_path, "referenced path must be a non-empty string")
    require((root / relative_path).is_file(), f"missing referenced artifact: {relative_path}")


def require_text(value, message: str):
    require(isinstance(value, str) and value.strip(), message)


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    manifest_path = "reports/wgsl-pipeline/m91-mep-rc-scene-pack/manifest.json"
    manifest = load_json(root, manifest_path)

    require(manifest.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(manifest.get("packId") == "m91-mep-rc-scene-pack-v1", "unexpected packId")
    require(manifest.get("status") == "pass", "manifest status must be pass")
    require(set(manifest.get("scopeIds", [])) == {"FOR-215", "FOR-216", "FOR-218"}, "scope ids changed")

    scope = manifest.get("scope") or {}
    require(scope.get("shaderTarget") == "WGSL", "shader target must stay WGSL")
    require(scope.get("gpuBackend") == "WebGPU", "GPU backend must stay WebGPU")
    require(scope.get("rendererChangesClaimed") is False, "RC pack must not claim renderer changes")
    require(scope.get("kadreNativeRequiredForValidation") is False, "headless validation must not require Kadre native")

    thresholds = manifest.get("thresholds") or {}
    require(set(thresholds.get("allowedStatuses", [])) == ALLOWED_STATUSES, "status taxonomy changed")
    require(set(thresholds.get("renderedRowRequiredArtifacts", [])) == REQUIRED_RENDERED_ARTIFACTS, "rendered artifact requirements changed")

    rows = manifest.get("sceneRows")
    require(isinstance(rows, list), "sceneRows must be a list")
    require(8 <= len(rows) <= 12, "sceneRows must contain 8-12 rows")

    seen_ids = set()
    status_counts = {status: 0 for status in ALLOWED_STATUSES}
    for index, row in enumerate(rows):
        require(isinstance(row, dict), f"sceneRows[{index}] must be an object")
        row_id = row.get("id")
        require_text(row_id, f"sceneRows[{index}].id is required")
        require(row_id not in seen_ids, f"duplicate scene id: {row_id}")
        seen_ids.add(row_id)

        status = row.get("status")
        require(status in ALLOWED_STATUSES, f"{row_id} has invalid status: {status}")
        status_counts[status] += 1
        require_text(row.get("family"), f"{row_id} family is required")
        require_text(row.get("thresholdPolicy"), f"{row_id} thresholdPolicy is required")

        source_evidence = row.get("sourceEvidence")
        require(isinstance(source_evidence, list) and source_evidence, f"{row_id} sourceEvidence is required")
        for source in source_evidence:
            require_file(root, source)

        diagnostics = row.get("diagnostics")
        require(isinstance(diagnostics, dict), f"{row_id} diagnostics object is required")
        require_text(diagnostics.get("cpuRoute"), f"{row_id} diagnostics.cpuRoute is required")
        require_text(diagnostics.get("gpuRoute"), f"{row_id} diagnostics.gpuRoute is required")
        require_text(diagnostics.get("fallbackReason"), f"{row_id} diagnostics.fallbackReason is required")

        artifacts = row.get("artifacts")
        require(isinstance(artifacts, dict), f"{row_id} artifacts object is required")
        if status in RENDERED_STATUSES:
            missing_keys = sorted(REQUIRED_RENDERED_ARTIFACTS - set(artifacts))
            require(not missing_keys, f"{row_id} missing rendered artifact keys: {', '.join(missing_keys)}")
            require(diagnostics.get("fallbackReason") == "none", f"{row_id} rendered rows must use fallbackReason=none")
            if status == "partial":
                require_text(diagnostics.get("partialReason"), f"{row_id} partial row must explain partialReason")
            for key in REQUIRED_RENDERED_ARTIFACTS:
                require_file(root, artifacts[key])
        elif status == "expected-unsupported":
            require(diagnostics.get("fallbackReason") != "none", f"{row_id} unsupported row must not use fallbackReason=none")
            require_text(diagnostics.get("diagnosticArtifact"), f"{row_id} diagnosticArtifact is required")
            require_file(root, diagnostics["diagnosticArtifact"])
            for key in ("routeCpu", "routeGpu", "stats"):
                require_file(root, artifacts.get(key))
        elif status == "blocked-dependency":
            require(diagnostics.get("fallbackReason") != "none", f"{row_id} blocked row must not use fallbackReason=none")
            require_text(diagnostics.get("blockedDependency"), f"{row_id} blockedDependency is required")
            require_text(diagnostics.get("diagnosticArtifact"), f"{row_id} diagnosticArtifact is required")
            require_file(root, diagnostics["diagnosticArtifact"])
            for key in ("routeCpu", "routeGpu", "stats"):
                require_file(root, artifacts.get(key))

    require(status_counts["supported"] >= 4, "at least four supported rows are required")
    require(status_counts["expected-unsupported"] >= 1, "at least one expected-unsupported row is required")
    require(status_counts["blocked-dependency"] >= 1, "at least one blocked-dependency row is required")

    for source in manifest.get("sourceEvidence", []):
        require_file(root, source)
    for artifact in manifest.get("artifactPaths", []):
        require_file(root, artifact)

    diagnostics = manifest.get("diagnostics")
    require(isinstance(diagnostics, list) and diagnostics, "manifest diagnostics are required")
    failed = [row.get("id", "<unknown>") for row in diagnostics if row.get("status") != "pass"]
    require(not failed, "manifest diagnostics failed: " + ", ".join(failed))

    non_claims = "\n".join(manifest.get("nonClaims", []))
    require("does not add renderer fixes" in non_claims, "renderer-fix non-claim missing")
    require("WGSL remains" in non_claims, "WGSL target non-claim missing")
    require("No SkSL compiler" in non_claims, "SkSL non-claim missing")
    require("Kadre native demos remain opt-in" in non_claims, "Kadre opt-in non-claim missing")

    report_path = root / "reports/wgsl-pipeline/m91-mep-rc-scene-pack/pm-report.md"
    require(report_path.is_file(), "PM report is missing")
    report = report_path.read_text(encoding="utf-8")
    require("does not claim new renderer fixes" in report, "PM report must avoid renderer-fix claims")
    require("WGSL remains" in report, "PM report must name WGSL target")
    require("Exclusions" in report, "PM report must explain exclusions")

    print("MEP RC scene pack validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

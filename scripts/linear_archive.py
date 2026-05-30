#!/usr/bin/env python3
"""Export Linear backlog slices into a versioned repository archive.

The script intentionally uses only the Python standard library so it can run
from a fresh checkout with a `LINEAR_API_KEY`.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import textwrap
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


LINEAR_GRAPHQL_URL = "https://api.linear.app/graphql"
SCHEMA_VERSION = 1
EVIDENCE_RE = re.compile(
    r"(https://github\.com/|/pull/|\breports/|\.md\b|\bPR\s*#?\d+|\bCI\b|\b[0-9a-f]{12,40}\b)",
    re.IGNORECASE,
)


EXPORT_QUERY = """
query ExportIssues($filter: IssueFilter, $after: String, $includeArchived: Boolean) {
  issues(first: 50, after: $after, filter: $filter, includeArchived: $includeArchived) {
    pageInfo {
      hasNextPage
      endCursor
    }
    nodes {
      id
      identifier
      number
      title
      description
      url
      priority
      priorityLabel
      estimate
      createdAt
      updatedAt
      startedAt
      completedAt
      canceledAt
      archivedAt
      dueDate
      team {
        id
        key
        name
      }
      state {
        id
        name
        type
      }
      project {
        id
        name
        url
      }
      projectMilestone {
        id
        name
      }
      parent {
        id
        identifier
        title
      }
      creator {
        id
        name
        email
      }
      assignee {
        id
        name
        email
      }
      labels(first: 50) {
        nodes {
          id
          name
        }
      }
      cycle {
        id
        number
        name
        startsAt
        endsAt
      }
      comments(first: 100) {
        nodes {
          id
          body
          createdAt
          updatedAt
          user {
            id
            name
            email
          }
        }
      }
      attachments(first: 50) {
        nodes {
          id
          title
          url
        }
      }
      relations(first: 50) {
        nodes {
          id
          type
          relatedIssue {
            id
            identifier
            title
            url
          }
        }
      }
    }
  }
}
"""


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()


def compact_date() -> str:
    return dt.datetime.now(dt.timezone.utc).date().isoformat()


def eprint(message: str) -> None:
    print(message, file=sys.stderr)


def read_token(env_name: str) -> str:
    token = os.environ.get(env_name, "").strip()
    if not token:
        raise SystemExit(
            f"Missing Linear API token. Set {env_name}=<token> or pass --token-env."
        )
    return token


def graphql(token: str, query: str, variables: dict[str, Any]) -> dict[str, Any]:
    request = urllib.request.Request(
        LINEAR_GRAPHQL_URL,
        data=json.dumps({"query": query, "variables": variables}).encode("utf-8"),
        headers={
            "Authorization": token,
            "Content-Type": "application/json",
            "User-Agent": "kanvas-linear-archive/1",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"Linear API HTTP {exc.code}: {body}") from exc
    except urllib.error.URLError as exc:
        raise SystemExit(f"Linear API request failed: {exc}") from exc

    if payload.get("errors"):
        formatted = json.dumps(payload["errors"], indent=2, sort_keys=True)
        raise SystemExit(f"Linear GraphQL error:\n{formatted}")
    return payload["data"]


def split_csv(values: list[str] | None) -> list[str]:
    if not values:
        return []
    result: list[str] = []
    for value in values:
        for part in value.split(","):
            stripped = part.strip()
            if stripped:
                result.append(stripped)
    return result


def build_filter(args: argparse.Namespace) -> dict[str, Any]:
    filters: list[dict[str, Any]] = []

    if args.project:
        filters.append({"project": {"name": {"eq": args.project}}})

    if args.team:
        filters.append(
            {
                "or": [
                    {"team": {"key": {"eq": args.team}}},
                    {"team": {"name": {"eq": args.team}}},
                ]
            }
        )

    milestones = split_csv(args.milestone)
    if milestones:
        filters.append({"projectMilestone": {"name": {"in": milestones}}})

    identifiers = split_csv(args.issue)
    if identifiers:
        filters.append(
            {
                "or": [
                    {"identifier": {"eq": identifier}} for identifier in identifiers
                ]
            }
        )

    if not args.include_open:
        filters.append(
            {
                "or": [
                    {"state": {"type": {"eq": "completed"}}},
                    {"state": {"type": {"eq": "canceled"}}},
                ]
            }
        )

    if not filters:
        raise SystemExit(
            "Refusing unbounded export. Pass --project, --team, --milestone, or --issue."
        )
    if len(filters) == 1:
        return filters[0]
    return {"and": filters}


def fetch_issues(args: argparse.Namespace) -> list[dict[str, Any]]:
    token = read_token(args.token_env)
    issue_filter = build_filter(args)
    issues: list[dict[str, Any]] = []
    after: str | None = None

    while True:
        data = graphql(
            token,
            EXPORT_QUERY,
            {
                "filter": issue_filter,
                "after": after,
                "includeArchived": bool(args.include_archived),
            },
        )
        connection = data["issues"]
        issues.extend(connection["nodes"])
        page_info = connection["pageInfo"]
        if not page_info["hasNextPage"]:
            break
        after = page_info["endCursor"]

    issues.sort(key=lambda issue: issue.get("identifier") or issue.get("id") or "")
    return issues


def nodes(connection: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not connection:
        return []
    return list(connection.get("nodes") or [])


def issue_identifier(issue: dict[str, Any]) -> str:
    return str(issue.get("identifier") or issue.get("id") or "issue")


def markdown_escape(value: Any) -> str:
    text = "" if value is None else str(value)
    return text.replace("|", "\\|").replace("\n", " ").strip()


def fenced(text: str | None) -> str:
    if not text:
        return "_No description exported._"
    return text.strip()


def clean_filename(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "-", value).strip("-") or "issue"


def normalized_issue(issue: dict[str, Any]) -> dict[str, Any]:
    result = dict(issue)
    result["labels"] = nodes(issue.get("labels"))
    result["comments"] = nodes(issue.get("comments"))
    result["attachments"] = nodes(issue.get("attachments"))
    result["relations"] = nodes(issue.get("relations"))
    return result


def status_type(issue: dict[str, Any]) -> str:
    state = issue.get("state") or {}
    return str(state.get("type") or "")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def write_ndjson(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, sort_keys=True, ensure_ascii=False))
            handle.write("\n")


def issue_markdown(issue: dict[str, Any]) -> str:
    identifier = issue_identifier(issue)
    labels = ", ".join(label["name"] for label in issue.get("labels", [])) or "-"
    comments = issue.get("comments", [])
    attachments = issue.get("attachments", [])
    relations = issue.get("relations", [])
    parent = issue.get("parent") or {}
    state = issue.get("state") or {}
    milestone = issue.get("projectMilestone") or {}
    project = issue.get("project") or {}
    team = issue.get("team") or {}
    assignee = issue.get("assignee") or {}
    creator = issue.get("creator") or {}

    lines = [
        f"# {identifier} - {issue.get('title', '')}".rstrip(),
        "",
        "## Metadata",
        "",
        "| Field | Value |",
        "|---|---|",
        f"| URL | {markdown_escape(issue.get('url') or '-')} |",
        f"| Team | {markdown_escape(team.get('name') or team.get('key') or '-')} |",
        f"| Project | {markdown_escape(project.get('name') or '-')} |",
        f"| Milestone | {markdown_escape(milestone.get('name') or '-')} |",
        f"| State | {markdown_escape(state.get('name') or '-')} ({markdown_escape(state.get('type') or '-')}) |",
        f"| Priority | {markdown_escape(issue.get('priorityLabel') or issue.get('priority') or '-')} |",
        f"| Estimate | {markdown_escape(issue.get('estimate') or '-')} |",
        f"| Creator | {markdown_escape(creator.get('name') or creator.get('email') or '-')} |",
        f"| Assignee | {markdown_escape(assignee.get('name') or assignee.get('email') or '-')} |",
        f"| Parent | {markdown_escape(parent.get('identifier') or '-')} |",
        f"| Labels | {markdown_escape(labels)} |",
        f"| Created | {markdown_escape(issue.get('createdAt') or '-')} |",
        f"| Updated | {markdown_escape(issue.get('updatedAt') or '-')} |",
        f"| Started | {markdown_escape(issue.get('startedAt') or '-')} |",
        f"| Completed | {markdown_escape(issue.get('completedAt') or '-')} |",
        f"| Canceled | {markdown_escape(issue.get('canceledAt') or '-')} |",
        f"| Archived | {markdown_escape(issue.get('archivedAt') or '-')} |",
        "",
        "## Description",
        "",
        fenced(issue.get("description")),
        "",
    ]

    if attachments:
        lines.extend(["## Attachments", ""])
        for attachment in attachments:
            title = attachment.get("title") or attachment.get("url") or attachment.get("id")
            url = attachment.get("url") or ""
            lines.append(f"- [{markdown_escape(title)}]({url})" if url else f"- {markdown_escape(title)}")
        lines.append("")

    if relations:
        lines.extend(["## Relations", ""])
        for relation in relations:
            related = relation.get("relatedIssue") or {}
            rel_id = related.get("identifier") or related.get("id") or "-"
            rel_title = related.get("title") or ""
            lines.append(
                f"- `{markdown_escape(relation.get('type') or '-')}`: "
                f"{markdown_escape(rel_id)} {markdown_escape(rel_title)}".rstrip()
            )
        lines.append("")

    lines.extend(["## Comments", ""])
    if not comments:
        lines.append("_No comments exported._")
    for comment in comments:
        author = comment.get("user") or {}
        lines.extend(
            [
                f"### {comment.get('createdAt', '')} - {author.get('name') or author.get('email') or 'unknown'}",
                "",
                fenced(comment.get("body")),
                "",
            ]
        )
    return "\n".join(lines).rstrip() + "\n"


def project_markdown(issues: list[dict[str, Any]]) -> str:
    counts: dict[str, int] = {}
    for issue in issues:
        counts[status_type(issue) or "unknown"] = counts.get(status_type(issue) or "unknown", 0) + 1

    lines = [
        "# Linear Archive Project Snapshot",
        "",
        f"Generated: {utc_now()}",
        "",
        "## Counts",
        "",
        "| State type | Count |",
        "|---|---:|",
    ]
    for key in sorted(counts):
        lines.append(f"| {markdown_escape(key)} | {counts[key]} |")

    lines.extend(["", "## Issues", "", "| Issue | State | Milestone | Title |", "|---|---|---|---|"])
    for issue in issues:
        state = issue.get("state") or {}
        milestone = issue.get("projectMilestone") or {}
        identifier = issue_identifier(issue)
        lines.append(
            f"| [{identifier}](issues/{clean_filename(identifier)}.md) "
            f"| {markdown_escape(state.get('name') or '-')} "
            f"| {markdown_escape(milestone.get('name') or '-')} "
            f"| {markdown_escape(issue.get('title') or '')} |"
        )
    return "\n".join(lines).rstrip() + "\n"


def sprint_report(issues: list[dict[str, Any]]) -> str:
    milestones: dict[str, list[dict[str, Any]]] = {}
    for issue in issues:
        milestone = (issue.get("projectMilestone") or {}).get("name") or "No milestone"
        milestones.setdefault(milestone, []).append(issue)

    lines = [
        "# Linear Backlog Archive Sprint Report",
        "",
        f"Date: {compact_date()}",
        "",
        "## Verdict",
        "",
        "This archive freezes the exported Linear backlog slice as repository evidence.",
        "Use Linear for active execution and this archive for historical audit.",
        "",
        "## Milestones",
        "",
        "| Milestone | Issues | Completed/Canceled | Other |",
        "|---|---:|---:|---:|",
    ]

    for milestone in sorted(milestones):
        group = milestones[milestone]
        closed = sum(1 for issue in group if status_type(issue) in {"completed", "canceled"})
        lines.append(f"| {markdown_escape(milestone)} | {len(group)} | {closed} | {len(group) - closed} |")

    lines.extend(
        [
            "",
            "## Archive Policy",
            "",
            "- Keep only active execution backlog in Linear.",
            "- Commit this archive before relying on Linear auto-archive or deletion.",
            "- Do not treat archived snapshots as active backlog.",
            "- Recreate future follow-up work as new scoped Linear tickets.",
        ]
    )
    return "\n".join(lines).rstrip() + "\n"


def relation_rows(issues: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for issue in issues:
        source = issue_identifier(issue)
        parent = issue.get("parent") or {}
        if parent.get("identifier"):
            rows.append(
                {
                    "source": source,
                    "target": parent["identifier"],
                    "type": "parent",
                }
            )
        for relation in issue.get("relations", []):
            related = relation.get("relatedIssue") or {}
            if related.get("identifier"):
                rows.append(
                    {
                        "source": source,
                        "target": related["identifier"],
                        "type": relation.get("type") or "related",
                    }
                )
    return rows


def dependency_graph(rows: list[dict[str, Any]]) -> str:
    lines = ["flowchart TD"]
    if not rows:
        lines.append('    empty["No exported relations"]')
        return "\n".join(lines) + "\n"
    for row in rows:
        source = clean_filename(row["source"])
        target = clean_filename(row["target"])
        label = markdown_escape(row["type"])
        lines.append(f'    {source}["{row["source"]}"] -->|"{label}"| {target}["{row["target"]}"]')
    return "\n".join(lines) + "\n"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def file_manifest(root: Path) -> list[dict[str, Any]]:
    files: list[dict[str, Any]] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.name == "manifest.json":
            continue
        files.append(
            {
                "path": path.relative_to(root).as_posix(),
                "bytes": path.stat().st_size,
                "sha256": sha256(path),
            }
        )
    return files


def write_archive(
    out_dir: Path,
    issues: list[dict[str, Any]],
    args: argparse.Namespace,
    *,
    generated_at: str | None = None,
) -> None:
    normalized = [normalized_issue(issue) for issue in issues]
    comments = [
        {"issue": issue_identifier(issue), **comment}
        for issue in normalized
        for comment in issue.get("comments", [])
    ]
    relations = relation_rows(normalized)

    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "issues").mkdir(exist_ok=True)
    write_ndjson(out_dir / "issues.ndjson", normalized)
    write_ndjson(out_dir / "comments.ndjson", comments)
    write_ndjson(out_dir / "relations.ndjson", relations)
    write_text(out_dir / "project.md", project_markdown(normalized))
    write_text(out_dir / "sprint-report.md", sprint_report(normalized))
    write_text(out_dir / "dependency-graph.mmd", dependency_graph(relations))

    for issue in normalized:
        identifier = issue_identifier(issue)
        write_text(out_dir / "issues" / f"{clean_filename(identifier)}.md", issue_markdown(issue))

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": generated_at or utc_now(),
        "source": {
            "linearGraphqlUrl": LINEAR_GRAPHQL_URL,
            "project": getattr(args, "project", None),
            "team": getattr(args, "team", None),
            "milestones": split_csv(getattr(args, "milestone", None)),
            "issues": split_csv(getattr(args, "issue", None)),
            "includeOpen": bool(getattr(args, "include_open", False)),
            "includeArchived": bool(getattr(args, "include_archived", False)),
        },
        "policy": {
            "archiveIsHistoricalEvidenceOnly": True,
            "deleteFromLinearOnlyAfterCommitted": True,
            "activeBacklogRemainsInLinear": True,
        },
        "counts": {
            "issues": len(normalized),
            "comments": len(comments),
            "relations": len(relations),
        },
        "files": file_manifest(out_dir),
    }
    write_text(out_dir / "manifest.json", json.dumps(manifest, indent=2, sort_keys=True) + "\n")


def load_ndjson(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                rows.append(json.loads(stripped))
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_number}: invalid JSON: {exc}") from exc
    return rows


def issue_has_evidence(issue: dict[str, Any]) -> bool:
    texts: list[str] = [
        str(issue.get("description") or ""),
        str(issue.get("title") or ""),
    ]
    for attachment in issue.get("attachments", []):
        texts.append(str(attachment.get("title") or ""))
        texts.append(str(attachment.get("url") or ""))
    for comment in issue.get("comments", []):
        texts.append(str(comment.get("body") or ""))
    return bool(EVIDENCE_RE.search("\n".join(texts)))


def verify_archive(path: Path, *, strict: bool = False, allow_missing_evidence: bool = False) -> int:
    errors: list[str] = []
    warnings: list[str] = []
    manifest_path = path / "manifest.json"

    if not manifest_path.exists():
        errors.append("manifest.json is missing")
        manifest = {}
    else:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    for entry in manifest.get("files", []):
        file_path = path / entry["path"]
        if not file_path.exists():
            errors.append(f"missing file listed in manifest: {entry['path']}")
            continue
        actual_hash = sha256(file_path)
        if actual_hash != entry["sha256"]:
            errors.append(f"hash mismatch for {entry['path']}")
        actual_bytes = file_path.stat().st_size
        if actual_bytes != entry["bytes"]:
            errors.append(f"byte-size mismatch for {entry['path']}")

    try:
        issues = load_ndjson(path / "issues.ndjson")
    except Exception as exc:  # noqa: BLE001 - verifier reports all parse errors uniformly.
        errors.append(str(exc))
        issues = []

    if not issues:
        errors.append("archive contains no issues")

    for issue in issues:
        identifier = issue_identifier(issue)
        issue_md = path / "issues" / f"{clean_filename(identifier)}.md"
        if not issue_md.exists():
            errors.append(f"missing markdown file for {identifier}")
        if status_type(issue) not in {"completed", "canceled"}:
            warnings.append(f"{identifier} is not closed: state type {status_type(issue) or 'unknown'}")
        if (
            status_type(issue) == "completed"
            and not allow_missing_evidence
            and not issue_has_evidence(issue)
        ):
            warnings.append(f"{identifier} is completed but has no obvious PR/commit/report evidence")

    if warnings and strict:
        errors.extend(warnings)
        warnings = []

    for warning in warnings:
        print(f"warning: {warning}")
    for error in errors:
        print(f"error: {error}", file=sys.stderr)

    if errors:
        return 1

    print(
        f"ok: archive verified ({len(issues)} issues, "
        f"{len(manifest.get('files', []))} manifest files)"
    )
    return 0


def prepare_out_dir(path: Path, force: bool) -> None:
    if not path.exists():
        path.mkdir(parents=True)
        return
    if force:
        shutil.rmtree(path)
        path.mkdir(parents=True)
        return
    if any(path.iterdir()):
        raise SystemExit(f"Output directory is not empty: {path}. Pass --force to replace it.")


def command_export(args: argparse.Namespace) -> int:
    out_dir = Path(args.out)
    prepare_out_dir(out_dir, args.force)
    issues = fetch_issues(args)
    write_archive(out_dir, issues, args)
    print(f"exported {len(issues)} issues to {out_dir}")
    return 0


def command_verify(args: argparse.Namespace) -> int:
    return verify_archive(
        Path(args.archive),
        strict=args.strict,
        allow_missing_evidence=args.allow_missing_evidence,
    )


def sample_issue(identifier: str, title: str, state_type: str = "completed") -> dict[str, Any]:
    return {
        "id": f"id-{identifier}",
        "identifier": identifier,
        "number": int(re.sub(r"\D", "", identifier) or "0"),
        "title": title,
        "description": f"Implemented in PR #123 and documented in reports/{identifier.lower()}.md.",
        "url": f"https://linear.app/example/issue/{identifier.lower()}",
        "priority": 3,
        "priorityLabel": "Medium",
        "estimate": None,
        "createdAt": "2026-05-01T00:00:00.000Z",
        "updatedAt": "2026-05-02T00:00:00.000Z",
        "startedAt": "2026-05-01T01:00:00.000Z",
        "completedAt": "2026-05-02T00:00:00.000Z" if state_type == "completed" else None,
        "canceledAt": None,
        "archivedAt": None,
        "dueDate": None,
        "team": {"id": "team", "key": "GRA", "name": "Graphiks"},
        "state": {"id": "state", "name": "Done", "type": state_type},
        "project": {"id": "project", "name": "Kanvas - WGSL Pipeline Target", "url": ""},
        "projectMilestone": {"id": "m1", "name": "M35 - MVP Release Candidate"},
        "parent": None,
        "creator": {"id": "user", "name": "Archive Test", "email": "archive@example.invalid"},
        "assignee": None,
        "labels": {"nodes": [{"id": "label", "name": "docs"}]},
        "cycle": None,
        "comments": {
            "nodes": [
                {
                    "id": "comment",
                    "body": "Closeout evidence: https://github.com/ygdrasil-io/kanvas/pull/123",
                    "createdAt": "2026-05-02T00:00:00.000Z",
                    "updatedAt": "2026-05-02T00:00:00.000Z",
                    "user": {"id": "user", "name": "Archive Test", "email": "archive@example.invalid"},
                }
            ]
        },
        "attachments": {"nodes": []},
        "relations": {"nodes": []},
    }


def command_self_test(_: argparse.Namespace) -> int:
    with tempfile.TemporaryDirectory(prefix="linear-archive-self-test.") as temp:
        out_dir = Path(temp) / "archive"
        args = argparse.Namespace(
            project="Kanvas - WGSL Pipeline Target",
            team="GRA",
            milestone=["M35 - MVP Release Candidate"],
            issue=[],
            include_open=False,
            include_archived=False,
        )
        write_archive(out_dir, [sample_issue("GRA-1", "Archive smoke")], args)
        result = verify_archive(out_dir, strict=True)
        if result == 0:
            print("self-test passed")
        return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Export Linear backlog slices into a repository archive.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent(
            """
            Examples:
              scripts/linear_archive.py export --project "Kanvas - WGSL Pipeline Target" \
                --milestone M33 --milestone M34 --milestone M35 \
                --out reports/linear-archive/2026-05-mvp-tail

              scripts/linear_archive.py verify --strict \
                reports/linear-archive/2026-05-mvp-tail
            """
        ),
    )
    sub = parser.add_subparsers(dest="command", required=True)

    export = sub.add_parser("export", help="export Linear issues into an archive directory")
    export.add_argument("--project", help="Linear project name")
    export.add_argument("--team", help="Linear team key or name")
    export.add_argument("--milestone", action="append", help="milestone name; repeat or comma-separate")
    export.add_argument("--issue", action="append", help="issue identifier; repeat or comma-separate")
    export.add_argument("--include-open", action="store_true", help="include non-completed/canceled issues")
    export.add_argument("--include-archived", action="store_true", help="include archived Linear issues")
    export.add_argument("--token-env", default="LINEAR_API_KEY", help="environment variable containing the Linear API key")
    export.add_argument("--out", required=True, help="output directory")
    export.add_argument("--force", action="store_true", help="replace an existing output directory")
    export.set_defaults(func=command_export)

    verify = sub.add_parser("verify", help="verify an archive directory")
    verify.add_argument("archive", help="archive directory")
    verify.add_argument("--strict", action="store_true", help="treat warnings as errors")
    verify.add_argument(
        "--allow-missing-evidence",
        action="store_true",
        help="do not warn for completed issues without obvious PR/commit/report evidence",
    )
    verify.set_defaults(func=command_verify)

    self_test = sub.add_parser("self-test", help="run a local no-network archive smoke test")
    self_test.set_defaults(func=command_self_test)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())

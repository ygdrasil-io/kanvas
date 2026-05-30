# Linear Backlog Archive

This directory is the repository-owned archive for completed Linear backlog
slices. Linear remains the active execution system; committed archive snapshots
are historical evidence only.

Use this when a Linear project or milestone is complete and the issues should
stop counting as active planning context for agents or humans. Do not use these
snapshots as active backlog.

## Why This Exists

Linear Free workspaces can run into issue-count pressure. The safe workflow is
to export a completed backlog slice into the repo before relying on Linear's
automatic archiving or deleting old Linear issues.

Linear issue links may remain useful, but the repo archive must be sufficient
to audit what was done without reopening Linear.

Official Linear references:

- https://linear.app/docs/exporting-data
- https://linear.app/docs/delete-archive-issues
- https://linear.app/docs/project-status

## Archive Layout

Each snapshot lives in a dated directory:

```text
reports/linear-archive/2026-05-mvp-tail/
  manifest.json
  project.md
  sprint-report.md
  issues.ndjson
  comments.ndjson
  relations.ndjson
  dependency-graph.mmd
  issues/
    GRA-101.md
    GRA-104.md
```

Files have separate roles:

- `manifest.json`: source query, counts, policy flags, file hashes.
- `issues.ndjson`: one normalized Linear issue per line.
- `comments.ndjson`: exported comments keyed by issue identifier.
- `relations.ndjson`: parent and issue-relation edges.
- `issues/*.md`: human-readable issue snapshots.
- `project.md`: issue table and state counts.
- `sprint-report.md`: PM-readable closeout summary.
- `dependency-graph.mmd`: Mermaid relation graph.

## Export

Create a Linear API key, then run:

```bash
export LINEAR_API_KEY=...

scripts/linear_archive.py export \
  --project "Kanvas - WGSL Pipeline Target" \
  --milestone "M33 — Path AA MVP Boundary" \
  --milestone "M34 — Image-filter MVP Lane" \
  --milestone "M35 — MVP Release Candidate" \
  --out reports/linear-archive/2026-05-mvp-tail
```

The exporter refuses unbounded exports. Provide at least one of `--project`,
`--team`, `--milestone`, or `--issue`.

By default, only completed and canceled issues are exported. Add
`--include-open` only for planning snapshots that intentionally include active
work.

## Verify

Before committing or deleting anything in Linear:

```bash
scripts/linear_archive.py verify --strict \
  reports/linear-archive/2026-05-mvp-tail
```

Strict verification checks:

- every file listed in `manifest.json` exists and matches its SHA-256 hash;
- every issue in `issues.ndjson` has a matching Markdown file;
- exported issues are completed or canceled;
- completed issues have obvious evidence such as PR, commit, CI, or report
  references.

If older issues have poor evidence but are intentionally archived, use
`--allow-missing-evidence` and document the exception in the snapshot's
`sprint-report.md`.

## Commit Policy

Commit archive snapshots before relying on Linear auto-archive or deleting
historical issues.

Recommended closeout commit:

```bash
rtk git add reports/linear-archive/2026-05-mvp-tail
rtk git commit -m "docs: archive Linear M33-M35 backlog"
```

After merge to `master`, completed Linear issues can be allowed to auto-archive
when Linear's project/status rules permit it. Manual deletion should be a
separate explicit decision, not part of the export command.

## Recovery

To recover a historical issue, open `issues/<identifier>.md` first. It contains
the issue description, metadata, comments, attachments, and relation summary
that were present at export time.

If work must resume, create a new Linear issue and link back to the archive
path instead of mutating the historical snapshot.

## Local Smoke Test

The script has a no-network self-test:

```bash
scripts/linear_archive.py self-test
```

# GM port tracking scripts

Disciplined tooling for porting Skia GM tests one .cpp at a time, instead of
the agent-swarm `@Disabled`-stub pattern of Waves O/P.

## Convention

For each upstream `gm/<name>.cpp` we want a Kotlin file (or several) in
`skia-integration-tests/src/main/kotlin/org/skia/tests/`.

When the upstream code calls an API we don't have yet:

1. **Add the API to `kanvas-skia/src/main/kotlin/`** with `TODO("not yet implemented")`
   as the body. The signature must be the real one (incl. types like `SkVertices`,
   `SkSlug`, etc. created in `TODO()` form too).
2. **Port the GM body normally.** Calls compile.
3. **Run the cross-backend test.** If it throws `NotImplementedError` at runtime,
   add `@Ignore("TODO: <api-name>")` on the test class and comment out the body.
4. **Commit.**

This way:
- `grep TODO()` in production code = the true API gap surface
- `gm-missing-apis.sh` ranks gaps by GM call-site count → priorities
- Nothing is "silently skipped" — everything is either ported or explicitly ignored

## Scripts

### `gm-status.sh`
Coverage report keyed by upstream `.cpp`.

```bash
scripts/gm/gm-status.sh                  # full TSV: cpp \t n_gm \t kt_files \t status
scripts/gm/gm-status.sh --summary        # 1-line totals
scripts/gm/gm-status.sh --status=MISSING # filter to MISSING (or PARTIAL, STUB, …)
```

Status values:
- `PORTED`  : all GMs covered with non-trivial `onDraw`
- `STUB`    : matching `.kt` exists but body is `// TODO: missing API` or trivial
- `IGNORED` : `@Ignore`/`@Disabled` on the test class
- `ALIAS`   : `typealias` only
- `PARTIAL` : multi-GM `.cpp` partially covered
- `MISSING` : no matching `.kt`
- `HELPER`  : `.cpp` with no `DEF_GM` (utility file)

### `gm-missing-apis.sh`
Lists every `= TODO(` declaration in `kanvas-skia/src/main/kotlin/`, ranked by
how many GM test files call it.

```bash
scripts/gm/gm-missing-apis.sh           # full ranked list
scripts/gm/gm-missing-apis.sh --top=20  # top N gaps
```

### `gm-batch.sh`
Picks the next un-PORTED `.cpp` files alphabetically and assigns them to N agents.
Used to brief parallel agents with **non-overlapping** file slices.

```bash
scripts/gm/gm-batch.sh                            # 5 agents × 10 files (default)
scripts/gm/gm-batch.sh --agents=3 --per-agent=15
scripts/gm/gm-batch.sh --status=MISSING           # MISSING only (default: MISSING+STUB+PARTIAL)
scripts/gm/gm-batch.sh --plain                    # filenames only, one per line
```

## Suggested workflow

```bash
# 1. snapshot current state
scripts/gm/gm-status.sh --summary
scripts/gm/gm-missing-apis.sh --top=10

# 2. pick a batch
scripts/gm/gm-batch.sh --agents=5 --per-agent=10

# 3. for each agent slice, run the agent in its own worktree
#    (each agent ports its 10 files, never two agents on the same file)

# 4. after merge, re-snapshot
scripts/gm/gm-status.sh --summary
```

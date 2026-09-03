# AGENTS.md — Rules for AI Agents (minecraft-mod-mcp)

> This file governs every AI agent / subagent / coding tool working in this
> repository. It was adapted on **2026-09-03** from the Celestia workspace
> rules (`/mnt/codespace/AGENTS.md` on the yuzu-linux daemon host), keeping
> only the parts that apply to this repo. Where the Celestia workspace rules
> conflict with this repo's own conventions ([CONTRIBUTING.md](CONTRIBUTING.md)),
> **this repo's conventions win**; every deliberate deviation is listed in §9.
>
> **Headline change (2026-09-03, maintainer directive): all changes now land
> through pull requests into `master` — including agent-made changes. The old
> `dev` integration branch is retired; do not push directly to `master`.**

---

## 1. Branch model

- `master` — the only long-lived branch. **Protected since 2026-09-03**: PRs
  are required (0 approvals — self-merge is fine), the `lint` check is
  required, force-pushes/deletions are blocked for everyone including the
  maintainer, and the repo only offers squash merge. Every `master` commit is
  therefore one reviewed, gitmoji-formatted change — same model as the
  Celestia workspace (their §5).
- `dev` — **retired on 2026-09-03** (branch deleted). Before deletion,
  `master` was fast-forwarded to the final `dev` tip, so no history was lost.
  Do not recreate `dev`; old `dev`-based local branches should be rebased onto
  `master`.
- Feature branches: `feat/<name>`, `fix/<name>`, `chore/<name>`,
  `refactor/<name>`, branched off `origin/master`.

## 2. Commit message & PR title format (CI-enforced)

```
<gitmoji> <Capitalized English one-sentence summary ending with a period.>
```

- The gitmoji must come from the [gitmoji.dev](https://gitmoji.dev) canonical
  set, plus the Celestia org additions 🔗 (symlink) 🔄 (sync/refresh)
  📜 (license) 🛡️ (shield). Commonly used here: ✨ 🐛 🔧 ♻️ 🔥 📝 📦 ⬆️ 👷 ✅ 🚀.
- The summary is **one plain English sentence**: capitalized first letter,
  ends with exactly one `.`, printable ASCII only (no CJK).
- **No colon-prefix shapes** — not `feat:`/`fix(scope):` conventional commits
  and not `Topic phrase: details` either (e.g. `🔧 Fix compliance: nonce
  handshake` is forbidden; `🔧 Fix nonce handshake and embed path.` is
  correct). The gitmoji already conveys the change type. Detailed context
  belongs in the commit BODY (blank line + bullets), never in the subject.
- **PR titles follow the exact same rule** — with squash merge, the PR title
  *becomes* the permanent `master` commit subject, so it is the single most
  important line you will write.
- `Revert "..."` subjects produced by `git revert` are exempt.
- Squash-merge suffix ` (#123)` is allowed.
- **Exception (repo convention):** development commits on feature branches may
  use conventional-commit prefixes for internal clarity — they are squashed
  away at merge time anyway. Gitmoji format is still preferred everywhere.
- Local check before pushing: `just lint-commits` (validates
  `origin/master..HEAD`). CI runs the same linter (`scripts/commit_lint.py`)
  on every PR title and on every new commit pushed to `master` — including
  merge-commit subjects, which are rejected: master is squash-merge only.

## 3. PR workflow (per task)

1. **Create a feature branch** off `origin/master`. If multiple agents share
   one checkout, work in an isolated `git worktree` (never edit the main
   checkout concurrently with another agent); remove the worktree after merge.
2. **3-round verify cycle** for each change: analyze → improve → verify, three
   rounds over; if any round fails, restart the count from zero. Use subagents
   for verification to keep the main context clean.
3. **Non-trivial tasks must go through subagents** (or equivalent isolation):
   give them exact file paths, the conventions above, and verification
   criteria; launch independent sub-tasks in parallel.
4. **Verify locally before pushing**: `just full` for build changes (or at
   minimum the touched package: `just mcp-build`, `just mcp-lint`, relevant
   `just build-mod`), `just lint-commits` always. Run `just smoke <version>`
   when behavior can only be proven in-game.
5. **Push** the branch, **open a PR against `master`** (via `gh pr create`)
   with a compliant title and a filled-in PR template.
6. **Merge** once required checks are green: **squash merge**, subject = PR
   title, then **delete the branch**.
7. **PR economy**: bundle one coherent feature/fix wave per PR; do not open a
   separate PR per trivial tweak. Small PRs are fine for urgent hotfixes or
   when nothing else is pending.
8. **Version bumps belong in the main PR**: bump
   `packages/minecraft-mod-mcp/package.json` inside the feature/fix PR that
   warrants a release; never open standalone version-bump PRs. Releases are
   then tag-driven (`git tag vX.Y.Z && git push --tags`).
9. Merging may be done autonomously by an agent once required checks pass and
   the title/body comply. Never merge over a genuine code-level failure;
   infra/environmental check failures may be waived only when documented in
   the PR and local verification passed.

## 4. Git push discipline

- **NEVER use bare `git push --force`** — no exceptions for "convenience".
- Prefer `git push --force-with-lease` for rebase/amend recovery on your own
  feature branch.
- If `--force-with-lease` is rejected (stale remote-tracking ref), **STOP**.
  Never fall back to `--force`: fetch, inspect with
  `git log origin/<branch>..HEAD` and `git log HEAD..origin/<branch>`, and ask
  the maintainer if anything is unaccounted for.
- Force-pushes of any kind to `master` are forbidden; it only advances
  forward via squash merge.
- This applies to all agents, subagents, and interactive sessions.

## 5. Sensitive information red line (hard rule)

> Learned from a real incident in the Celestia family: a live SSH password
> committed to a repo required history rewriting. Treat a violation here as an
> incident of the same severity.

1. **Never put real passwords / keys / tokens / internal IPs into the git
   tree** — any file, branch, comment, example, test fixture, or doc. This
   includes the Celestia workspace files (`/mnt/codespace/AGENTS.md`,
   `PLAN.md`), which contain real credentials: read them for context if you
   must, but **never copy their values into any file of this repo** (including
   this AGENTS.md).
2. Need a secret in code? Use environment variables / untracked config files,
   or obvious placeholders (`<your-token>`, `CHANGE_ME`, `sk-xxx`). Example
   addresses use RFC 5737 documentation ranges (192.0.2.x / 198.51.100.x /
   203.0.113.x) and example values (`test-password`).
3. If a real credential is genuinely required, **ask the maintainer first**;
   never write it on your own authority.
4. **Before committing** anything touching config/deploy/scripts/fixtures:
   grep your diff for `password|secret|token|api_key` and for `192.168.` /
   `10.` internal addresses; replace hits per the rules above.
5. If a leak happens anyway: remove it from the branch/PR, assess the blast
   radius (tags/branches/forks), report to the maintainer, and treat the
   credential as public — rotate it regardless of any history rewrite.

## 6. Verification & CI usage

- CI runs on GitHub-hosted runners (public repo → free; unlike the Celestia
  self-hosted fleet, there is no shared-runner quota to protect, but the
  Windows mod matrix is slow — see below).
- Every workflow already sets `concurrency` + `cancel-in-progress`, so stale
  runs are cancelled automatically; do not add workflows without a
  `concurrency` group.
- **What runs where**: on PRs to `master` — the build matrix (`ci.yml`) plus
  the fast commit/PR-title lint (`commit-lint.yml`). On `push` to `master` —
  the full pipeline including smoke/screenshot/E2E tests, plus the
  push-format guard.
- **CI is a gate for code failures, not a tea ceremony**: for docs/config-only
  changes you may merge once the lint check is green and the relevant code
  checks pass, without waiting out the full Windows build matrix — record the
  waiver in the PR. Never merge over a real compile/test failure; if checks
  are merely queued, wait or re-run instead of force-merging.
- Do not sit and watch CI. If a run is stuck queued for an unusually long
  time, investigate (`gh run list`, `gh run cancel <id>`) instead of stacking
  more pushes on top.

## 7. CHANGELOG policy

- **Never create a CHANGELOG / revision-history file in this repo.** The
  merged PRs are the changelog: the squash subject + PR description form the
  complete history, and `git log` serves any granularity.
- Release notes live on the git tag / GitHub Releases page (written per
  release, never per commit) — the `release.yml` workflow already does this.

## 8. Local lint recipe

```bash
just lint-commits                     # validate origin/master..HEAD
just lint-commits v0.2.0..master      # validate any range
python scripts/commit_lint.py --subject "✨ Add a new tool."   # one subject
```

## 9. Deliberately NOT adopted from the Celestia workspace rules

| Celestia rule | Why not here |
|---|---|
| §0.6 large-download / sing-box proxy discipline | Specific to the yuzu-linux NAT/proxy network and its airport-quota incidents; this repo builds on GitHub-hosted runners and the maintainer's Windows machine. |
| §1 node table, passwords, NFS layout | Workspace infrastructure — and per §5 above, its credentials must never be copied into this repo. |
| §2 Celestia-island repo layout | Different family of repositories. |
| §7 Rust/pnpm/CARGO_HOME & self-hosted runner ops | This is a Gradle/npm/Python repo on hosted runners. |
| §7 "CI 是参考不是门禁" waiver culture | Softened: hosted CI is the real gate here; only documented waivers for docs-only changes (§6). |
| §8 node-2/3 deployment & malkuth supervision | No deployment fleet; releases are tag-driven GitHub Releases. |
| §9 pnpm registry / sibling links / worktree symlink discipline | NFS multi-agent infra that does not exist here. |

> The Celestia §5 model (master-only, squash-merge only, `dev` deprecated) is
> **adopted** here as of 2026-09-03 — see §1.

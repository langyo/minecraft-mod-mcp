"""Commit message / PR title linter for minecraft-mod-mcp.

Enforces the format documented in AGENTS.md section 2 and CONTRIBUTING.md:

    <gitmoji> <Capitalized English one-sentence summary ending with a period.>

Rules:
  1. Subject starts with a whitelisted gitmoji, then exactly one space.
  2. Summary is printable ASCII (no CJK), first character capitalized.
  3. Summary ends with exactly one '.'.
  4. No conventional-commit prefixes (``feat:``, ``fix(scope):``, ...) and no
     ``Topic phrase:`` colon-prefix shape -- write one plain sentence instead.
  5. ``Revert "..."`` subjects produced by ``git revert`` are exempt.
  6. Squash-merge suffix `` (#123)`` is allowed.

Usage:
  python scripts/commit_lint.py --subject "✨ Add a new tool."
  python scripts/commit_lint.py --range origin/dev..HEAD
  python scripts/commit_lint.py --range A..B --check-merges
"""

import argparse
import re
import subprocess
import sys

# gitmoji.dev canonical set (variation selectors stripped) plus the Celestia
# org additions: 🔗 symlink, 🔄 sync/refresh, 📜 license, 🛡 shield.
GITMOJIS = frozenset(
    """
    🎨 ⚡ 🔥 🐛 🚑 ✨ 📝 🚀 💄 🎉 ✅ 🔒 👮 🔖 🚨 💚 📱 ⬇ ⬆ 🩹 👷 📈 ♻ ➕ ➖
    🔧 🔨 🌐 ✏ 💩 ⏪ 🔀 📦 👽 🚚 📄 💥 🍱 ♿ 💡 🍸 💬 🗃 🔊 🔇 👥 🚸 🏗 🧐
    🧪 👔 🩺 🧱 🧑‍💻 💸 🧵 🦺 🥅 💫 ⚰ 🔍 🏷 🌱
    🔗 🔄 📜 🛡
    """.split()
)

SQUASH_SUFFIX = re.compile(r" \(\#\d+\)$")
CONVENTIONAL_PREFIX = re.compile(
    r"^(feat|fix|chore|docs|style|refactor|perf|test|build|ci|revert|hack|wip|hotfix)"
    r"(\([^)]*\))?!?:\s"
)
COLON_PREFIX = re.compile(r"^[A-Za-z][\w'\-]*(\s+[\w'\-]+){0,4}:(?!//)")

MAX_SUBJECT_LEN = 100


def normalize(text):
    return text.replace("\ufe0f", "").strip()


def check_subject(raw):
    """Return None if the subject complies, otherwise a human-readable reason."""
    subject = normalize(raw)

    if subject.startswith('Revert "'):
        return None

    matched = None
    for emoji in sorted(GITMOJIS, key=len, reverse=True):
        if subject.startswith(emoji):
            matched = emoji
            break
    if matched is None:
        return "must start with a whitelisted gitmoji (gitmoji.dev set, see AGENTS.md \u00a72)"

    summary = subject[len(matched):]
    if not summary.startswith(" ") or summary.startswith("  "):
        return "exactly one space required between gitmoji and summary"

    summary = summary.strip()
    summary = SQUASH_SUFFIX.sub("", summary)
    if not summary:
        return "empty summary"

    if len(subject) > MAX_SUBJECT_LEN:
        return "subject longer than %d characters, keep it to one concise sentence" % MAX_SUBJECT_LEN

    if not summary.isascii():
        return "summary must be English (printable ASCII, no CJK)"

    if not (summary[0].isupper() or summary[0].isdigit()):
        return "summary must start with a capital letter"

    if not summary.endswith(".") or summary.endswith(".."):
        return "summary must end with exactly one '.'"

    if CONVENTIONAL_PREFIX.match(summary):
        return "conventional-commit prefix is forbidden; write one plain sentence"

    if COLON_PREFIX.match(summary):
        return "colon-prefix shape ('Topic phrase: details') is forbidden; write the whole change as one sentence"

    return None


def collect_range(commit_range, check_merges):
    """Yield (sha, subject) pairs for commits in a git range."""
    out = git("log", "--no-merges", "--pretty=%H%x1f%s", commit_range)
    for line in out.splitlines():
        if line.strip():
            sha, subject = line.split("\x1f", 1)
            yield sha, subject
    if check_merges:
        out = git("log", "--merges", "--pretty=%H%x1f%s", commit_range)
        for line in out.splitlines():
            if line.strip():
                sha, subject = line.split("\x1f", 1)
                yield sha, subject


def git(*args):
    result = subprocess.run(
        ["git", *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=True,
    )
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--subject", help="validate a single subject line")
    group.add_argument("--range", dest="commit_range", help="validate commits in a git range")
    parser.add_argument(
        "--check-merges",
        action="store_true",
        help="with --range: also lint merge-commit subjects",
    )
    args = parser.parse_args()

    failures = []
    if args.subject is not None:
        reason = check_subject(args.subject)
        if reason:
            failures.append(("(subject)", args.subject, reason))
    else:
        for sha, subject in collect_range(args.commit_range, args.check_merges):
            reason = check_subject(subject)
            if reason:
                failures.append((sha[:9], subject, reason))

    if failures:
        for sha, subject, reason in failures:
            print("FAIL %s %s" % (sha, subject))
            print("     -> %s" % reason)
        print(
            "\n%d violation(s). Format: <gitmoji> <Capitalized English one-sentence "
            "summary ending with a period.>  See AGENTS.md \u00a72." % len(failures)
        )
        return 1

    print("OK" if args.subject is not None else "OK, all commits comply.")
    return 0


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except AttributeError:
            pass
    sys.exit(main())

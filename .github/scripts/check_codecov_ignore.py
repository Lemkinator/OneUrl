import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
IGNORE_KEY = re.compile(r"^ignore:")
IGNORE_BLOCK_KEY = re.compile(r"^ignore:\s*(#.*)?$")
IGNORE_FLOW = re.compile(r"^ignore:\s*\[(.*)\]\s*(#.*)?$")
IGNORE_ENTRY = re.compile(r"^\s+-\s+[\"']?([^\"'#]+?)[\"']?\s*(#.*)?$")
GLOB_CHARS = frozenset("*?[")


def unquote(entry):
    entry = entry.strip()
    if len(entry) >= 2 and entry[0] == entry[-1] and entry[0] in "\"'":
        return entry[1:-1]
    return entry


def ignored_paths(text):
    paths = []
    has_key = False
    in_block = False
    for line in text.splitlines():
        if IGNORE_BLOCK_KEY.match(line):
            has_key = in_block = True
        elif flow := IGNORE_FLOW.match(line):
            has_key = True
            in_block = False
            paths.extend(unquote(item) for item in flow.group(1).split(",") if item.strip())
        elif IGNORE_KEY.match(line):
            has_key = True
            in_block = False
        elif in_block and (match := IGNORE_ENTRY.match(line)):
            paths.append(match.group(1))
        elif in_block and line.strip() and not line.lstrip().startswith("#"):
            in_block = False
    if has_key and not paths:
        raise ValueError("codecov.yml has an ignore key but no entry could be parsed")
    return paths


def glob_regex(pattern):
    out = []
    i = 0
    while i < len(pattern):
        char = pattern[i]
        if char == "*" and pattern.startswith("**", i):
            i += 2
            if pattern.startswith("/", i):
                i += 1
                out.append("(?:.*/)?")
            else:
                out.append(".*")
            continue
        if char == "*":
            out.append("[^/]*")
        elif char == "?":
            out.append("[^/]")
        elif char == "[" and (end := pattern.find("]", i + 2)) != -1:
            body = pattern[i + 1 : end]
            negate = body.startswith("!")
            body = body[1:] if negate else body
            out.append("[" + ("^" if negate else "") + body.replace("\\", "\\\\") + "]")
            i = end
        else:
            out.append(re.escape(char))
        i += 1
    return re.compile("".join(out) + "(?:/.*)?")


def matches_tracked(entry, tracked):
    if GLOB_CHARS & set(entry):
        regex = glob_regex(entry)
        return any(regex.fullmatch(path) for path in tracked)
    prefix = entry.rstrip("/") + "/"
    return any(path == entry or path.startswith(prefix) for path in tracked)


def git_tracked_files(root):
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=root, check=True, capture_output=True, text=True, encoding="utf-8"
    )
    return [path for path in result.stdout.split("\0") if path]


def missing_paths(text, tracked):
    return [entry for entry in ignored_paths(text) if not matches_tracked(entry, tracked)]


def main(argv=None, tracked_files=git_tracked_files):
    args = sys.argv[1:] if argv is None else argv
    root = Path(args[0]) if args else REPO_ROOT
    text = (root / "codecov.yml").read_text(encoding="utf-8")
    try:
        missing = missing_paths(text, tracked_files(root))
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    for path in missing:
        print(f"codecov.yml ignores a path that matches no tracked file: {path}", file=sys.stderr)
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())

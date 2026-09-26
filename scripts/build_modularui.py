#!/usr/bin/env python3
"""Build the pinned ModularUI compatibility patch into Maven local (Java 21 required)."""

import os
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = "https://github.com/brachy84/ModularUI-Modern.git"
REVISION = "8ecb104d0c38b1eb9baab5c837624034db7ca873"
PATCH = ROOT / "patches/modularui/jei-19.57-preview.diff"


def run(*args, cwd):
    subprocess.run(args, cwd=cwd, check=True)


def main():
    # A fresh checkout avoids applying the patch twice or touching a developer's sources.
    # Everything generated here stays under the ignored build directory.
    parent = ROOT / "build/patched-dependencies"
    parent.mkdir(parents=True, exist_ok=True)
    checkout = Path(tempfile.mkdtemp(prefix="modularui-", dir=parent))
    run("git", "init", cwd=checkout)
    run("git", "fetch", "--depth=1", UPSTREAM, REVISION, cwd=checkout)
    run("git", "checkout", "--detach", "FETCH_HEAD", cwd=checkout)
    run("git", "apply", "--check", str(PATCH), cwd=checkout)
    run("git", "apply", str(PATCH), cwd=checkout)
    if os.name == "nt":
        wrapper = ["cmd.exe", "/c", "gradlew.bat"]
    else:
        wrapper = ["sh", "./gradlew"]
    run(*wrapper, "test", "publishToMavenLocal", "--no-daemon", cwd=checkout)
    print("Patched ModularUI published to Maven local. You can now build GregTech.")


if __name__ == "__main__":
    main()

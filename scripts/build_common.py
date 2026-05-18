"""Build mcp-common module."""
import os
import subprocess
import sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def main():
    mc_common = os.path.join(BASE, "packages", "common")
    gradlew = os.path.join(mc_common, "gradlew.bat") if sys.platform == "win32" else os.path.join(mc_common, "gradlew")
    if not os.path.isfile(gradlew):
        gradlew = os.path.join(BASE, "gradlew.bat") if sys.platform == "win32" else os.path.join(BASE, "gradlew")

    env = os.environ.copy()
    env["GRADLE_OPTS"] = "-Xmx2G"

    r = subprocess.run(
        [gradlew, "build", "--no-daemon"],
        cwd=mc_common,
        env=env,
    )
    sys.exit(r.returncode)

if __name__ == "__main__":
    main()

"""Mirror source rotation for local development scripts.

Defines mirror groups for all upstream sources, probes connectivity,
and provides download-with-fallback across mirrors.

Integration points:
  - prepare_cache.py: replace hardcoded Maven URLs with mirror rotation
  - build_all.py: probe mirrors, patch Gradle wrappers, generate init.gradle

Usage:
  from mirrors import probe_all, get_url, download, patch_wrapper
  probe_all()
  url = get_url("maven_forge")
  ok = download("maven_forge", "de/oceanlabs/mcp/...", "/tmp/file.zip")
"""

import urllib.request
import urllib.error
import os
import re
import threading
import time

PROBE_TIMEOUT = 8
DL_TIMEOUT = 180

MIRROR_GROUPS = {
    "maven_central": {
        "desc": "Maven Central",
        "urls": [
            "https://repo1.maven.org/maven2",
            "https://maven.aliyun.com/repository/central",
            "https://mirrors.cloud.tencent.com/nexus/repository/maven-public",
            "https://repo.huaweicloud.com/repository/maven",
        ],
    },
    "maven_forge": {
        "desc": "Forge Maven",
        "urls": [
            "https://maven.minecraftforge.net",
        ],
    },
    "maven_fabric": {
        "desc": "Fabric Maven",
        "urls": [
            "https://maven.fabricmc.net",
        ],
    },
    "maven_neoforge": {
        "desc": "NeoForge Maven",
        "urls": [
            "https://maven.neoforged.net/releases",
        ],
    },
    "gradle_dist": {
        "desc": "Gradle Distributions",
        "urls": [
            "https://services.gradle.org/distributions",
            "https://mirrors.cloud.tencent.com/gradle",
        ],
    },
}

_working = {}
_lock = threading.Lock()


def _probe_url(url, timeout=PROBE_TIMEOUT):
    try:
        req = urllib.request.Request(url, method="HEAD")
        resp = urllib.request.urlopen(req, timeout=timeout)
        return resp.status < 400
    except Exception:
        return False


def _short(url):
    return url.replace("https://", "").replace("http://", "").split("/")[0]


def probe(group_name, force=False):
    if not force and group_name in _working:
        return _working[group_name]

    group = MIRROR_GROUPS.get(group_name)
    if not group:
        return None

    for url in group["urls"]:
        ok = _probe_url(url)
        print(f"    [{'OK' if ok else 'FAIL'}] {_short(url)}")
        if ok:
            with _lock:
                _working[group_name] = url
            return url

    fallback = group["urls"][0]
    print(f"    [WARN] all mirrors failed for {group_name}, using {_short(fallback)}")
    with _lock:
        _working[group_name] = fallback
    return fallback


def probe_all(force=False):
    print("Probing mirror sources...")
    for name, group in MIRROR_GROUPS.items():
        print(f"  {group['desc']}:")
        probe(name, force=force)
    print()
    return dict(_working)


def get_url(group_name):
    if group_name in _working:
        return _working[group_name]
    return probe(group_name)


def get_all_urls(group_name):
    group = MIRROR_GROUPS.get(group_name, {})
    urls = list(group.get("urls", []))
    cached = _working.get(group_name)
    if cached and cached in urls:
        urls.remove(cached)
        urls.insert(0, cached)
    return urls


def download(group_name, rel_path, dest, timeout=DL_TIMEOUT):
    """Download a file trying each mirror for the group until one succeeds."""
    group = MIRROR_GROUPS.get(group_name)
    if not group:
        return False

    dest = os.path.abspath(dest)
    os.makedirs(os.path.dirname(dest), exist_ok=True)

    if os.path.isfile(dest) and os.path.getsize(dest) > 0:
        return True

    mirrors = get_all_urls(group_name)
    for base_url in mirrors:
        url = f"{base_url.rstrip('/')}/{rel_path.lstrip('/')}"
        try:
            urllib.request.urlretrieve(url, dest)
            if os.path.isfile(dest) and os.path.getsize(dest) > 0:
                with _lock:
                    _working[group_name] = base_url
                return True
        except Exception:
            if os.path.isfile(dest):
                os.remove(dest)
            continue

    return False


def _escape_props_url(url):
    return url.replace(":", "\\:")


_GRADLE_VER_RE = re.compile(r"gradle-(\d[\d.]+\d)-bin\.zip")


def patch_wrapper(properties_path):
    """Patch gradle-wrapper.properties to use the working Gradle dist mirror."""
    with open(properties_path, "r") as f:
        content = f.read()

    m = _GRADLE_VER_RE.search(content)
    if not m:
        return False

    version = m.group(1)
    mirror_base = get_url("gradle_dist")
    filename = f"gradle-{version}-bin.zip"
    new_url = f"{mirror_base}/{filename}"

    content = re.sub(
        r"distributionUrl=.*",
        f"distributionUrl={_escape_props_url(new_url)}",
        content,
    )
    content = re.sub(
        r"validateDistributionUrl=.*",
        "validateDistributionUrl=false",
        content,
    )

    with open(properties_path, "w") as f:
        f.write(content)
    return True


def patch_all_wrappers(base_dir):
    """Find and patch ALL gradle-wrapper.properties under packages/mods/."""
    mods_dir = os.path.join(base_dir, "packages", "mods")
    patched = 0
    if not os.path.isdir(mods_dir):
        return 0

    for root, dirs, files in os.walk(mods_dir):
        if "gradle-wrapper.properties" in files:
            path = os.path.join(root, "gradle-wrapper.properties")
            if patch_wrapper(path):
                patched += 1

    root_wrapper = os.path.join(base_dir, "gradle", "wrapper", "gradle-wrapper.properties")
    if os.path.isfile(root_wrapper):
        if patch_wrapper(root_wrapper):
            patched += 1

    return patched


_GRADLE_INIT_DIR = os.path.join(
    os.path.expanduser("~"), ".gradle", "init.d"
)


def generate_init_gradle():
    """Generate ~/.gradle/init.d/mirrors.gradle with working mirror repos."""
    os.makedirs(_GRADLE_INIT_DIR, exist_ok=True)
    init_path = os.path.join(_GRADLE_INIT_DIR, "mirrors.gradle")

    central = get_url("maven_central")

    lines = [
        "// Auto-generated mirror config for local development. Safe to delete.",
        "allprojects {",
        "    repositories {",
        f"        maven {{ url = '{central}' }}",
        "    }",
        "    buildscript {",
        "        repositories {",
        f"            maven {{ url = '{central}' }}",
        "        }",
        "    }",
        "}",
    ]

    with open(init_path, "w") as f:
        f.write("\n".join(lines) + "\n")

    print(f"  Generated {init_path}")
    return init_path


def remove_init_gradle():
    """Remove the auto-generated mirrors.gradle."""
    init_path = os.path.join(_GRADLE_INIT_DIR, "mirrors.gradle")
    if os.path.isfile(init_path):
        os.remove(init_path)

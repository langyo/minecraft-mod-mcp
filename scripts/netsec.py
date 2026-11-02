"""Network & path security helpers (hardening for the build/launch tooling).

Every outbound request made by the Python scripts must go through
``safe_urlopen``/``assert_safe_url`` (HTTPS scheme + host allowlist, no
credentials in the URL, no raw-IP hosts), and every path that is joined
onto a trusted base directory from version manifests, mirror metadata or
CLI arguments must go through ``safe_join`` (the result may not escape
the base via ``..``, absolute paths or drive letters).

Host allowlist: the static set below covers every host the tooling
reaches today (Mojang, Forge, NeoForge, Fabric, Gradle mirrors, GitHub
releases, Adoptium). ``mirrors.MIRROR_GROUPS`` registers its hosts at
import time as well, and ``MCP_EXTRA_ALLOWED_HOSTS`` (comma-separated)
may extend the set for private mirror setups. When adding a new download
source, add its host here in the same change.

Deliberate scope note: hosts are checked as names, and raw-IP hosts are
rejected, but we do not resolve and post-check DNS answers — the threat
model here is a malicious *manifest* pointing the tool at an arbitrary
host, not a compromised local resolver.
"""
import ipaddress
import os
import urllib.parse
import urllib.request

# fmt: off
ALLOWED_HOSTS = frozenset({
    # Mojang / Minecraft
    "piston-meta.mojang.com", "piston-data.mojang.com",
    "launchermeta.mojang.com", "libraries.minecraft.net",
    "resources.download.minecraft.net",
    # Forge
    "maven.minecraftforge.net", "files.minecraftforge.net",
    "metadict.minecraftforge.net", "download.minecraftforge.net",
    # NeoForge
    "maven.neoforged.net", "neoforged.forgecdn.net",
    # Fabric
    "maven.fabricmc.net", "meta.fabricmc.net",
    # Gradle distributions & Maven Central (buildscript deps)
    "services.gradle.org", "repo.maven.apache.org", "repo1.maven.org",
    "plugins.gradle.org", "jitpack.io",
    # Mirror group hosts (also registered by mirrors.py at import)
    "mirrors.cloud.tencent.com", "mirrors.aliyun.com",
    "maven.aliyun.com", "repo.huaweicloud.com",
    # GitHub (1.7.x gradle cache, JDK archives, wrapper distributions)
    "github.com", "codeload.github.com", "objects.githubusercontent.com",
    "api.github.com", "raw.githubusercontent.com",
    # Adoptium (ensure-jdk.mjs parity, in case Python needs it)
    "api.adoptium.net",
})
# fmt: on

_EXTRA = os.environ.get("MCP_EXTRA_ALLOWED_HOSTS", "")
if _EXTRA:
    ALLOWED_HOSTS = frozenset(
        set(ALLOWED_HOSTS) | {h.strip().lower() for h in _EXTRA.split(",") if h.strip()}
    )

_ALLOWED_PORTS = frozenset({"", "443"})


def register_hosts(hosts):
    """Idempotently extend the allowlist (used by mirrors.py for its group URLs)."""
    global ALLOWED_HOSTS
    new = set(ALLOWED_HOSTS)
    for h in hosts:
        h = (h or "").strip().lower()
        if h:
            new.add(h)
    ALLOWED_HOSTS = frozenset(new)


class UnsafeURLError(ValueError):
    """Raised when a URL refuses the allowlist / shape checks."""


class UnsafePathError(ValueError):
    """Raised when a joined path would escape its base directory."""


def assert_no_traversal(path):
    """Reject paths that contain ``..`` segments.

    For absolute local paths that are NOT joined onto a base directory
    (config files passed between internal modules): the only traversal
    risk to fence off is a ``..`` component.
    """
    # Check the RAW segments — os.path.normpath would already resolve
    # `a/../../evil` and hide the traversal from us.
    parts = os.fspath(path).replace("\\", "/").split("/")
    if ".." in parts:
        raise UnsafePathError(f"path contains '..' segment: {path!r}")
    return path


def assert_safe_url(url):
    """Validate that *url* is HTTPS, allowlisted, credential-free and port-safe."""
    if not isinstance(url, str) or not url:
        raise UnsafeURLError("empty URL")
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme.lower() != "https":
        raise UnsafeURLError(f"non-HTTPS scheme not allowed: {url!r}")
    host = (parsed.hostname or "").lower()
    if not host:
        raise UnsafeURLError(f"URL has no host: {url!r}")
    if parsed.username or parsed.password:
        raise UnsafeURLError(f"credentials in URL are not allowed: {url!r}")
    if parsed.port is not None and str(parsed.port) not in _ALLOWED_PORTS:
        raise UnsafeURLError(f"non-standard port not allowed: {url!r}")
    # Reject raw-IP hosts outright (loopback/private/link-local/metadata or
    # anything else that bypasses the hostname allowlist).
    try:
        ipaddress.ip_address(host)
        raise UnsafeURLError(f"raw-IP hosts are not allowed: {url!r}")
    except ValueError:
        pass  # not an IP literal — fine, hostname checks below apply
    if host not in ALLOWED_HOSTS:
        raise UnsafeURLError(f"host {host!r} is not in the download allowlist")
    return url


def safe_urlopen(url, timeout=60, method=None, headers=None):
    """``urllib.request.urlopen`` behind ``assert_safe_url``.

    Downloads from version manifests and mirror metadata must use this
    instead of calling ``urlopen`` directly.
    """
    assert_safe_url(url)
    req_headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    if headers:
        req_headers.update(headers)
    req = urllib.request.Request(url, headers=req_headers, method=method)
    return urllib.request.urlopen(req, timeout=timeout)


def safe_join(base, *parts):
    """``os.path.join`` that refuses to escape *base*.

    Use whenever any element of *parts* originates from a downloaded
    manifest (library ``path`` fields, asset indexes, archive entries),
    CLI input or any other untrusted source. Raises UnsafePathError on
    ``..`` traversal, absolute paths and drive-letter prefixes.
    """
    base_abs = os.path.abspath(base)
    joined = os.path.join(base_abs, *[str(p) for p in parts])
    full = os.path.normpath(joined)
    # Windows: reject drive-letter / UNC parts smuggled into *parts*.
    for p in parts:
        p = str(p)
        if os.path.isabs(p) or (os.name == "nt" and len(p) >= 2 and p[1] == ":"):
            raise UnsafePathError(f"absolute path element {p!r} not allowed under {base!r}")
    base_norm = os.path.normcase(base_abs)
    full_norm = os.path.normcase(full)
    if full_norm != base_norm and not full_norm.startswith(base_norm + os.sep):
        raise UnsafePathError(f"path {full!r} escapes base {base_abs!r}")
    return full

"""Copy Java source files to all mod projects based on API compatibility groups."""
import os, shutil

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS_DIR = os.path.join(BASE, "mods")

# Source templates directory
TEMPLATES_DIR = os.path.join(BASE, "mod-templates")

# API group -> (forge_src_dir, neoforge_src_dir, fabric_src_dir)
# Each src_dir contains: MinecraftMcpMod.java and XxxInputHandler.java
# We'll read from the existing 26.1.2 and 1.21.4 projects as templates

# The existing working mods:
EXISTING = {
    "26.1.2/forge": "fg7",
    "26.1.2/neoforge": "fg7",
    "1.21.4/forge": "fg7",
}

# Map each MC version's loaders to source templates
# For now, use the 26.1.2 Forge source as base for all Forge mods (API differences noted)
# and create per-group templates

def get_template_dir(mc, loader):
    """Determine which template source to use."""
    # MC 26.x uses new API (.handle(), .identifier(), .getSerializedName())
    if mc.startswith("26."):
        if loader == "forge":
            return os.path.join(MODS_DIR, "26.1.2", "forge", "src")
        elif loader == "neoforge":
            return os.path.join(MODS_DIR, "26.1.2", "neoforge", "src")
    
    # 1.21.4+ uses FG7 EventBus 7
    if mc in ("1.21.4", "1.21.5"):
        if loader == "forge":
            return os.path.join(MODS_DIR, "1.21.4", "forge", "src")
        elif loader == "neoforge":
            return os.path.join(MODS_DIR, "1.21.4", "neoforge", "src")
    
    # 1.21.3 uses FG7 but older API
    if mc == "1.21.3":
        if loader == "forge":
            return os.path.join(MODS_DIR, "1.21.4", "forge", "src")
        elif loader == "neoforge":
            return os.path.join(MODS_DIR, "1.21.4", "neoforge", "src")
    
    # 1.21, 1.21.1, 1.21.2 use FG6 EventBus (old @SubscribeEvent)
    if mc in ("1.21", "1.21.1", "1.21.2"):
        if loader == "forge":
            return "template_forge_fg6"
        elif loader == "neoforge":
            return "template_neoforge_fg6"
        elif loader == "fabric":
            return "template_fabric"
    
    # 1.19.3 - 1.20.6 use FG6 with official mappings
    if mc in ("1.19.3", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6"):
        if loader == "forge":
            return "template_forge_fg6"
        elif loader == "neoforge":
            return "template_neoforge_fg6"
        elif loader == "fabric":
            return "template_fabric"
    
    # 1.17 - 1.19.2 use FG5 with official mappings
    if mc in ("1.17.1", "1.18", "1.18.2", "1.19", "1.19.2"):
        if loader == "forge":
            return "template_forge_fg5"
        elif loader == "fabric":
            return "template_fabric"
    
    # 1.14.4 - 1.16.5
    if mc in ("1.14.4", "1.15", "1.15.2", "1.16.1", "1.16.3", "1.16.4", "1.16.5"):
        if loader == "forge":
            return "template_forge_fg4"
        elif loader == "fabric":
            return "template_fabric"
    
    # 1.13.2
    if mc == "1.13.2":
        if loader == "forge":
            return "template_forge_fg3"
    
    # 1.7.2 - 1.12.2 (very old, LWJGL 2)
    if mc in ("1.7.2","1.7.10","1.8","1.8.9","1.9","1.9.4","1.10","1.10.2","1.11","1.11.2","1.12","1.12.2"):
        return "template_forge_legacy"
    
    return None


def copy_src(src_root, dst_root):
    """Copy source files from template to destination."""
    if src_root is None:
        return False
    if not os.path.isdir(src_root):
        return False
    
    pkg_dst = os.path.join(dst_root, "main", "java", "xyz", "langyo", "minecraftmcp")
    os.makedirs(pkg_dst, exist_ok=True)
    
    src_java = os.path.join(src_root, "main", "java")
    if not os.path.isdir(src_java):
        return False
    
    for root, dirs, files in os.walk(src_java):
        for f in files:
            if f.endswith(".java"):
                src_file = os.path.join(root, f)
                dst_file = os.path.join(pkg_dst, f)
                shutil.copy2(src_file, dst_file)
    
    # Copy resources if they exist
    src_res = os.path.join(src_root, "main", "resources")
    if os.path.isdir(src_res):
        dst_res = os.path.join(dst_root, "main", "resources")
        if not os.path.exists(dst_res):
            for root, dirs, files in os.walk(src_res):
                for f in files:
                    src_file = os.path.join(root, f)
                    rel = os.path.relpath(src_file, src_res)
                    dst_file = os.path.join(dst_res, rel)
                    os.makedirs(os.path.dirname(dst_file), exist_ok=True)
                    shutil.copy2(src_file, dst_file)
    
    return True


# MC versions with their loaders
ALL_VERSIONS = {
    "1.7.2": ["forge"], "1.7.10": ["forge"],
    "1.8": ["forge"], "1.8.9": ["forge"],
    "1.9": ["forge"], "1.9.4": ["forge"],
    "1.10": ["forge"], "1.10.2": ["forge"],
    "1.11": ["forge"], "1.11.2": ["forge"],
    "1.12": ["forge"], "1.12.2": ["forge"],
    "1.13.2": ["forge"],
    "1.14.4": ["forge","fabric"],
    "1.15": ["forge","fabric"], "1.15.2": ["forge","fabric"],
    "1.16.1": ["forge","fabric"], "1.16.3": ["forge","fabric"],
    "1.16.4": ["forge","fabric"], "1.16.5": ["forge","fabric"],
    "1.17.1": ["forge","fabric"],
    "1.18": ["forge","fabric"], "1.18.2": ["forge","fabric"],
    "1.19": ["forge","fabric"], "1.19.2": ["forge","fabric"],
    "1.19.3": ["forge","fabric"], "1.19.4": ["forge","fabric"],
    "1.20": ["forge","fabric"],
    "1.20.1": ["forge","neoforge","fabric"],
    "1.20.2": ["forge","neoforge","fabric"],
    "1.20.3": ["forge","neoforge","fabric"],
    "1.20.4": ["forge","neoforge","fabric"],
    "1.20.5": ["neoforge","fabric"],
    "1.20.6": ["forge","neoforge","fabric"],
    "1.21": ["forge","fabric"],
    "1.21.1": ["forge","neoforge","fabric"],
    "1.21.2": ["neoforge","fabric"],
    "1.21.3": ["forge","neoforge","fabric"],
    "1.21.4": ["forge","neoforge","fabric"],
    "1.21.5": ["forge","neoforge","fabric"],
    "26.1": ["forge"],
    "26.1.1": ["forge","neoforge"],
    "26.1.2": ["forge","neoforge"],
}

if __name__ == "__main__":
    created = 0
    skipped = 0
    for mc, loaders in ALL_VERSIONS.items():
        for loader in loaders:
            dst = os.path.join(MODS_DIR, mc, loader, "src")
            template = get_template_dir(mc, loader)
            if template and copy_src(template, dst):
                created += 1
            else:
                skipped += 1
                tmpl_name = template if template else "NO TEMPLATE"
                print(f"  SKIP: {mc}/{loader} (template: {tmpl_name})")
    
    print(f"\nSource files copied: {created}")
    print(f"Skipped (need template): {skipped}")

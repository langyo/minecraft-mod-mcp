export interface Capability {
  id: 'actuate' | 'bootstrap' | 'detach' | 'see'
  icon: string
  color: string
  /** short verb shown as a kicker */
  verbKey: string
  titleKey: string
  descKey: string
  /** optional bullet list keys (i18n arrays) */
  pointsKey: string
}

/**
 * The four pillars of Minecraft Mod MCP — each maps to a concrete, verifiable
 * capability of the project rather than a generic marketing claim.
 *
 *  - actuate:  AI drives the live game (click / type / scroll / keys via reflection)
 *  - bootstrap: CLI auto-resolves Java + version + loader, downloads & launches a test client
 *  - detach:   in-game overlay (top-right) frees the mouse so the agent tool stays usable
 *  - see:      coordinate-grid screenshots — pairs with a vision model for full autonomy
 */
export const capabilities: Capability[] = [
  {
    id: 'actuate',
    icon: 'i-lucide-mouse-pointer-click',
    color: '#5fd35f',
    verbKey: 'cap.actuate.verb',
    titleKey: 'cap.actuate.title',
    descKey: 'cap.actuate.desc',
    pointsKey: 'cap.actuate.points',
  },
  {
    id: 'bootstrap',
    icon: 'i-lucide-rocket',
    color: '#2ee6c8',
    verbKey: 'cap.bootstrap.verb',
    titleKey: 'cap.bootstrap.title',
    descKey: 'cap.bootstrap.desc',
    pointsKey: 'cap.bootstrap.points',
  },
  {
    id: 'detach',
    icon: 'i-lucide-mouse-off',
    color: '#8b5cf6',
    verbKey: 'cap.detach.verb',
    titleKey: 'cap.detach.title',
    descKey: 'cap.detach.desc',
    pointsKey: 'cap.detach.points',
  },
  {
    id: 'see',
    icon: 'i-lucide-scan-eye',
    color: '#f5a623',
    verbKey: 'cap.see.verb',
    titleKey: 'cap.see.title',
    descKey: 'cap.see.desc',
    pointsKey: 'cap.see.points',
  },
]

export interface Loader {
  id: string
  icon: string
  color: string
  /** loader name e.g. "Forge" */
  nameKey: string
}

export const loaders: Loader[] = [
  { id: 'forge', icon: 'i-lucide-anvil', color: '#f5a623', nameKey: 'loader.forge' },
  { id: 'fabric', icon: 'i-lucide-package', color: '#5fd35f', nameKey: 'loader.fabric' },
  { id: 'neoforge', icon: 'i-lucide-hammer', color: '#ef4444', nameKey: 'loader.neoforge' },
]

# Minecraft Mod MCP — Website

The marketing/landing site for Minecraft Mod MCP, served via **GitHub Pages**.

Tech stack mirrors [`celestia-island.github.io`](https://celestia-island.github.io):

- **Vue 3** + **Vite 6** (SPA, hash router)
- **UnoCSS** (Wind + Icons + Typography presets, `@iconify-json/lucide`)
- **Pinia** · **Vue Router** · **Vue I18n** (8 locales)
- **Three.js** shader background + starfield
- **Sass** + **marked** for the About page
- **TypeScript** with `vue-tsc` type-checking
- **Python + Pillow** favicon generation, pnpm-driven build

## Project layout

```
docs/website/
├─ index.html            # Vite entry
├─ vite.config.ts
├─ uno.config.ts
├─ tsconfig.json
├─ package.json
├─ scripts/build.py      # full build (or favicon/asset pass with --skip-*)
├─ shaders/              # GLSL for the Three.js background
├─ res/
│  ├─ i18n/*.json        # en, zh-CN, zh-TW, ja, ko, es, fr, ru
│  └─ logos/             # minecraft-mod-mcp.webp (favicon source)
├─ docs/**/about.md      # per-locale About copy, rendered by marked
└─ src/
   ├─ main.ts · App.vue
   ├─ assets/styles/global.scss
   ├─ components/         # NavBar, ThreeBackground, FeatureCard
   ├─ composables/useTheme.ts
   ├─ i18n/index.ts
   ├─ types/feature.ts
   └─ views/Home.vue
```

## Local development

```bash
cd docs/website
pnpm install
pnpm dev        # http://localhost:4173
```

## Production build

```bash
pnpm build      # runs scripts/build.py: typecheck → vite build → favicons → assets
```

Output lands in `docs/website/dist`.

## Deployment

The `.github/workflows/website.yml` workflow:

- **lints + builds** on every push to `master`/`dev` (and PRs to `master`) when `docs/website/**` changes;
- **deploys to GitHub Pages** only from `master`.

Until you bind a custom domain, GitHub Pages serves the site under the repo
subpath (`/minecraft-mod-mcp/`). The workflow auto-detects this: if no
`docs/website/CNAME` file exists it builds with `--base /minecraft-mod-mcp/`;
once a `CNAME` is present it builds with `--base /`.

### Binding a custom domain

1. Create `docs/website/CNAME` containing your domain, e.g. `mcp.example.com`.
2. Commit and push to `master`.
3. In **Settings → Pages**, set the custom domain and enable HTTPS.

The build copies `CNAME` into `dist/` and switches the Vite base to `/`.

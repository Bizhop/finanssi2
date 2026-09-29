// Dev server for the component test pages in dev-pages/. Deliberately a separate Vite root: the app's config (vite.config.ts) only sees src/,
// so these pages can never end up in the app bundle.
import { defineConfig, mergeConfig } from "vite"
import appConfig from "./vite.config.ts"

export default mergeConfig(
    appConfig,
    defineConfig({
        root: "./dev-pages",
        server: {
            port: 3001,
            // Pages import app components from ../src
            fs: { allow: [import.meta.dirname!] },
        },
    }),
)

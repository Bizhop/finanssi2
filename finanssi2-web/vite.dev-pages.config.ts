// Dev server for the component test pages in dev-pages/. Deliberately a separate Vite root: the app's config (vite.config.ts) only sees src/,
// so these pages can never end up in the app bundle.
import { defineConfig, mergeConfig } from "vite"
import appConfig from "./vite.config.ts"

export default defineConfig((env) => {
    const app = appConfig(env)
    // The Deno Vite resolver treats imports that cross out of this separate root (../src) as Deno modules
    // and mis-resolves them. Deno has already installed the npm dependencies, so Vite can resolve these pages directly.
    const plugins: unknown[] = []
    const keepPlugin = (plugin: unknown): void => {
        if (Array.isArray(plugin)) {
            plugin.forEach(keepPlugin)
            return
        }
        const name = typeof plugin === "object" && plugin !== null && "name" in plugin ? String(plugin.name) : ""
        if (name !== "deno" && !name.startsWith("deno:")) plugins.push(plugin)
    }
    ;(app.plugins as unknown as unknown[] | undefined)?.forEach(keepPlugin)
    app.plugins = plugins as typeof app.plugins
    return mergeConfig(
        app,
        defineConfig({
            root: "./dev-pages",
            server: {
                port: 3001,
                // Pages import app components from ../src and the real game data from the backend
                fs: { allow: [import.meta.dirname ?? ".", `${import.meta.dirname}/../finanssi2-backend/src/main/resources/gamedata`] },
            },
        }),
    )
})

import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import deno from "@deno/vite-plugin"
import { createHash } from "node:crypto"
import { readFileSync } from "node:fs"

import "react"
import "react-dom"

// Vite rebuilds its pre-bundled dependencies when a lockfile it knows changes, but deno.lock isn't one of them, so after a dependency
// change it kept using a stale cache (e.g. "ENOENT ... node_modules/.deno/<removed package>"). A cache directory per deno.lock
// version avoids that; old ones stay behind under .vite/ (gitignored) and can be deleted.
const denoLockHash = createHash("sha256").update(readFileSync(new URL("./deno.lock", import.meta.url))).digest("hex").slice(0, 12)

export default defineConfig({
    root: "./src",
    // Relative to root, so the app (src/) and the test pages (dev-pages/) keep separate caches
    cacheDir: `.vite/${denoLockHash}`,
    server: {
        port: 3000,
    },
    plugins: [
        react(),
        deno(),
    ],
    build: {
        rolldownOptions: {
            output: {
                // Split large, rarely changing libraries into their own chunks so they stay cached between app releases
                codeSplitting: {
                    groups: [
                        {
                            name: "react",
                            test: /node_modules[\\/].*[\\/](react|react-dom|react-router|scheduler)[\\/]/,
                        },
                        { name: "mui", test: /node_modules[\\/].*[\\/](@mui|@emotion|@popperjs|stylis)[\\/]/ },
                        { name: "firebase", test: /node_modules[\\/].*[\\/](@firebase|firebase|idb)[\\/]/ },
                        { name: "vendor", test: /node_modules[\\/]/ },
                    ],
                },
            },
        },
    },
    optimizeDeps: {
        include: ["react/jsx-runtime"],
    },
})

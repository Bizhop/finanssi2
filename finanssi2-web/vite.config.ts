import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import deno from "@deno/vite-plugin"
import { fileURLToPath } from "node:url"
import { createHash } from "node:crypto"
import { readFileSync } from "node:fs"

import "react"
import "react-dom"

// Vite rebuilds its pre-bundled dependencies when a lockfile it knows changes, but deno.lock isn't one of them, so after a dependency
// change it kept using a stale cache (e.g. "ENOENT ... node_modules/.deno/<removed package>"). A cache directory per deno.lock
// version avoids that; old ones stay behind under .vite/ (gitignored) and can be deleted.
const denoLockHash = createHash("sha256").update(readFileSync(new URL("./deno.lock", import.meta.url))).digest("hex").slice(0, 12)
const projectRoot = fileURLToPath(new URL(".", import.meta.url))

function validateProductionEnvironment(mode: string) {
    if (mode !== "production") return
    const required = [
        "VITE_FIREBASE_API_KEY",
        "VITE_FIREBASE_AUTH_DOMAIN",
        "VITE_FIREBASE_PROJECT_ID",
        "VITE_FIREBASE_STORAGE_BUCKET",
        "VITE_FIREBASE_MESSAGING_SENDER_ID",
        "VITE_FIREBASE_APP_ID",
        "VITE_FINANSSI_API_URL",
    ]
    const missing = required.filter((name) => !process.env[name]?.trim())
    if (missing.length) throw new Error(`Missing required production environment variables: ${missing.join(", ")}`)

    let apiUrl: URL
    try {
        apiUrl = new URL(process.env.VITE_FINANSSI_API_URL!)
    } catch {
        throw new Error("VITE_FINANSSI_API_URL must be an absolute HTTPS URL")
    }
    if (apiUrl.protocol !== "https:") throw new Error("VITE_FINANSSI_API_URL must use HTTPS in production")
}

export default defineConfig(({ mode }) => {
    validateProductionEnvironment(mode)
    return {
        root: "./src",
        envDir: projectRoot,
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
            outDir: "../dist",
            emptyOutDir: true,
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
    }
})

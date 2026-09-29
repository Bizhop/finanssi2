import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import deno from "@deno/vite-plugin"

import "react"
import "react-dom"

export default defineConfig({
    root: "./src",
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

// Only the React Hooks rules (rules of hooks, exhaustive deps and the React Compiler's checks); everything else is `deno lint`.
import reactHooks from "eslint-plugin-react-hooks"
import tseslint from "typescript-eslint"

export default [
    { ignores: ["dist/", "node_modules/", "**/.vite/"] },
    {
        files: ["src/**/*.{ts,tsx}", "dev-pages/**/*.{ts,tsx}"],
        languageOptions: { parser: tseslint.parser },
        ...reactHooks.configs.flat["recommended-latest"],
    },
]

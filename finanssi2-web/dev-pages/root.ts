// Every test page imports this, so the translations are set up before it renders (the app does the same in src/index.tsx)
import "../src/i18n/init.ts"

/** The element the test page renders into */
export const appRoot = () => {
    const element = document.getElementById("app")
    if (!element) throw new Error("The page has no #app element")
    return element
}

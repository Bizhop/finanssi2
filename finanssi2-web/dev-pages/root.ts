// Every test page imports this, so the translations are set up before it renders (the app does the same in src/index.tsx)
import "../src/i18n/init.ts"
import { type Language, LANGUAGES, setLanguage } from "../src/i18n/index.ts"

// `?lang=en` opens a test page in English (the app itself has the switch on the account page)
const requested = new URLSearchParams(location.search).get("lang")
if (LANGUAGES.includes(requested as Language)) await setLanguage(requested as Language)

/** The element the test page renders into */
export const appRoot = () => {
    const element = document.getElementById("app")
    if (!element) throw new Error("The page has no #app element")
    return element
}

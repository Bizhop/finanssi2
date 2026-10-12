import i18next from "i18next"
import { initReactI18next } from "react-i18next"
import { z } from "zod/mini"
import { en as zodEnglish, fi as zodFinnish } from "zod/locales"

import { setFormatLocale } from "../ui/format.ts"
import { en } from "./en.ts"
import { fi } from "./fi.ts"

export const LANGUAGES = ["fi", "en"] as const
export type Language = (typeof LANGUAGES)[number]

const DEFAULT_LANGUAGE: Language = "fi"
const STORAGE_KEY = "finanssi2.language"

declare module "i18next" {
    interface CustomTypeOptions {
        defaultNS: "translation"
        resources: { translation: typeof fi }
    }
}

const isLanguage = (value: unknown): value is Language => LANGUAGES.includes(value as Language)

/** The saved choice; Finnish when there is none. The browser's own language is not consulted: the game's assets are Finnish. */
const savedLanguage = (): Language => {
    try {
        const saved = localStorage.getItem(STORAGE_KEY)
        if (isLanguage(saved)) return saved
    } catch { /* storage unavailable */ }
    return DEFAULT_LANGUAGE
}

const applyLanguage = (language: Language) => {
    setFormatLocale(language === "fi" ? "fi-FI" : "en-GB")
    // zod/mini ships without error messages, so the validation messages follow the language too
    z.config(language === "fi" ? zodFinnish() : zodEnglish())
    if (typeof document !== "undefined") document.documentElement.lang = language
}

let initialized = false

/** Sets i18next up once; the language is the saved one unless given */
export const initI18n = (language: Language = savedLanguage()) => {
    if (initialized) return i18next
    initialized = true
    i18next.on("languageChanged", (next) => {
        if (isLanguage(next)) applyLanguage(next)
    })
    void i18next.use(initReactI18next).init({
        lng: language,
        fallbackLng: DEFAULT_LANGUAGE,
        resources: { fi: { translation: fi }, en: { translation: en } },
        // Keys are flat ("auth.email"), so "." is not a separator
        keySeparator: false,
        // React escapes on its own
        interpolation: { escapeValue: false },
        returnNull: false,
    })
    applyLanguage(language)
    return i18next
}

export const currentLanguage = (): Language => isLanguage(i18next.language) ? i18next.language : DEFAULT_LANGUAGE

export const setLanguage = (language: Language) => {
    try {
        localStorage.setItem(STORAGE_KEY, language)
    } catch { /* the choice then lasts until the page is reloaded */ }
    return i18next.changeLanguage(language)
}

/** For code outside React components; components use useTranslation, which re-renders when the language changes */
export const t = i18next.t.bind(i18next)

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

declare module "i18next" {
    interface CustomTypeOptions {
        defaultNS: "translation"
        resources: { translation: typeof fi }
    }
}

const isLanguage = (value: unknown): value is Language => LANGUAGES.includes(value as Language)

const applyLanguage = (language: Language) => {
    setFormatLocale(language === "fi" ? "fi-FI" : "en-GB")
    // zod/mini ships without error messages, so the validation messages follow the language too
    z.config(language === "fi" ? zodFinnish() : zodEnglish())
    if (typeof document !== "undefined") document.documentElement.lang = language
}

let initialized = false

/** Sets i18next up once, in Finnish unless given. The choice lasts until the page is reloaded; the browser's own language is not consulted, as the game's assets are Finnish. */
export const initI18n = (language: Language = DEFAULT_LANGUAGE) => {
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
        // Resources are inline, so t() works as soon as init returns (tests rely on it)
        initAsync: false,
    })
    applyLanguage(language)
    return i18next
}

export const currentLanguage = (): Language => isLanguage(i18next.language) ? i18next.language : DEFAULT_LANGUAGE

export const setLanguage = (language: Language) => i18next.changeLanguage(language)

/** For code outside React components; components use useTranslation, which re-renders when the language changes */
export const t = i18next.t.bind(i18next)

/** The wording of a backend error code in the current language, or undefined when this version of the app has none (then the backend's English detail is shown) */
export const apiErrorMessage = (code: string | undefined, params?: Record<string, unknown>): string | undefined =>
    code && i18next.exists(`apiError.${code}`) ? i18next.t(`apiError.${code}` as never, params ?? {}) as string : undefined

// The one place that formats numbers, money, dates and lists, so every screen shows them the same way. The locale follows the language of the UI
// (see i18n/index.ts); until that is set up, it is English.
let locale = "en-GB"

export const setFormatLocale = (next: string) => {
    locale = next
}

/** Whole euros: "€12,345" in English, "12 345 €" in Finnish */
export const formatEuro = (amount: number, forLocale = locale) =>
    new Intl.NumberFormat(forLocale, { style: "currency", currency: "EUR", maximumFractionDigits: 0 }).format(amount)

/** A plain number with the locale's grouping */
export const formatNumber = (value: number, forLocale = locale) => new Intl.NumberFormat(forLocale).format(value)

/** Date and time of a timestamp in milliseconds */
export const formatDateTime = (timestamp: number, forLocale = locale) =>
    new Intl.DateTimeFormat(forLocale, { dateStyle: "short", timeStyle: "medium" }).format(new Date(timestamp))

/** "A, B and C" in English, "A, B ja C" in Finnish */
export const formatList = (items: string[], forLocale = locale) => new Intl.ListFormat(forLocale, { style: "long", type: "conjunction" }).format(items)

// The one place that formats numbers, money and dates, so every screen shows them the same way. The locale is the language of the UI,
// set once with setFormatLocale; until the UI has a language of its own, that is English.
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

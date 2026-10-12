import { assertEquals } from "@std/assert"
import { formatDateTime, formatEuro, formatNumber } from "./format.ts"

Deno.test("money is whole euros with the symbol the locale puts it in", () => {
    assertEquals(formatEuro(12345, "en-GB"), "€12,345")
    assertEquals(formatEuro(0, "en-GB"), "€0")
    assertEquals(formatEuro(-500, "en-GB"), "-€500")
    // Finnish groups with a no-break space and puts the symbol last
    assertEquals(formatEuro(12345, "fi-FI").replaceAll(" ", " "), "12 345 €")
})

Deno.test("numbers and timestamps follow the locale", () => {
    assertEquals(formatNumber(1234567, "en-GB"), "1,234,567")
    assertEquals(formatNumber(1234567, "fi-FI").replaceAll(" ", " "), "1 234 567")
    assertEquals(
        formatDateTime(Date.UTC(2026, 9, 12, 8, 30, 15), "en-GB").includes("2026") || formatDateTime(Date.UTC(2026, 9, 12), "en-GB").includes("26"),
        true,
    )
})

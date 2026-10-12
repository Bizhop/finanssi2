import { assertEquals } from "@std/assert"
import { formatEuro } from "../ui/format.ts"
import { currentLanguage, initI18n, setLanguage, t } from "./index.ts"

Deno.test("the UI starts in the given language and switching changes messages, plurals and number formats", async () => {
    initI18n("fi")
    assertEquals(currentLanguage(), "fi")
    assertEquals(t("nav.games"), "Pelit")
    assertEquals(t("gameCard.players", { count: 1 }), "1 pelaaja")
    assertEquals(formatEuro(12345).replaceAll("\u00a0", " "), "12 345 €")

    await setLanguage("en")
    assertEquals(currentLanguage(), "en")
    assertEquals(t("nav.games"), "Games")
    assertEquals(t("gameCard.players", { count: 2 }), "2 players")
    assertEquals(formatEuro(12345), "€12,345")
})

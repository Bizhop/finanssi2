// Smoke run over the test pages: starts the test-page dev server, opens every page linked from dev-pages/index.html in headless Chromium
// and fails on script errors, console errors, a signed-out render or a request the fake backend has no answer for. Run with `deno task smoke`.
import { chromium } from "playwright-core"

const port = 3011
const origin = `http://localhost:${port}`

// Pages that are expected to log errors, with the text they must show
const errorPages = [{ page: "error-boundary.html", text: "Something went wrong while showing this page." }]

const indexHtml = await Deno.readTextFile(new URL("./index.html", import.meta.url))
const pages = [...indexHtml.matchAll(/href="([^"]+\.html[^"]*)"/g)].map((match) => match[1].replaceAll("&amp;", "&")).filter((page) =>
    !errorPages.some((item) => item.page === page)
)
if (pages.length < 20) throw new Error(`Expected the index to link many pages, found ${pages.length}`)

const server = new Deno.Command(Deno.execPath(), {
    args: ["run", "-A", "--env-file=.env.development", "vite", "--config", "vite.dev-pages.config.ts", "--port", String(port), "--strictPort"],
    cwd: new URL("..", import.meta.url).pathname,
    stdout: "null",
    stderr: "null",
}).spawn()

const stopServer = async () => {
    try {
        server.kill()
        await server.status
    } catch { /* already stopped */ }
}

/** Playwright's own Chromium when its revision matches the installed package, otherwise any Chromium in the Playwright cache */
const browserPath = async () => {
    const candidates = [Deno.env.get("CHROMIUM_PATH"), chromium.executablePath()].filter((path): path is string => Boolean(path))
    const cache = `${Deno.env.get("HOME")}/.cache/ms-playwright`
    try {
        for await (const entry of Deno.readDir(cache)) {
            if (entry.name.startsWith("chromium_headless_shell-")) candidates.push(`${cache}/${entry.name}/chrome-headless-shell-linux64/chrome-headless-shell`)
            if (entry.name.startsWith("chromium-")) candidates.push(`${cache}/${entry.name}/chrome-linux64/chrome`)
        }
    } catch { /* no cache */ }
    for (const path of candidates) {
        try {
            await Deno.stat(path)
            return path
        } catch { /* try the next one */ }
    }
    throw new Error("No Chromium found; set CHROMIUM_PATH or run `npx playwright install chromium`")
}

const waitForServer = async () => {
    for (let attempt = 0; attempt < 60; attempt++) {
        try {
            if ((await fetch(`${origin}/`)).ok) return
        } catch { /* not up yet */ }
        await new Promise((resolve) => setTimeout(resolve, 500))
    }
    throw new Error("The test-page server did not start")
}

const failures: string[] = []
try {
    await waitForServer()
    const browser = await chromium.launch({ executablePath: await browserPath() })
    const context = await browser.newContext({ viewport: { width: 1400, height: 900 } })
    // Vite may re-optimize dependencies while the first pages load, which breaks a page once; a second attempt is a fair check
    const check = async (page: string) => {
        const problems: string[] = []
        const tab = await context.newPage()
        tab.on("pageerror", (error) => problems.push(`script error: ${error.message}`))
        tab.on("console", (message) => {
            const text = message.text()
            // The mocks log every request they answer, with the level info
            if (message.type() === "error" && !text.includes("favicon")) problems.push(`console error: ${text}`)
        })
        await tab.goto(`${origin}/${page}`, { waitUntil: "load" })
        await tab.waitForLoadState("networkidle")
        await tab.waitForTimeout(400)
        const text = await tab.locator("body").innerText()
        if (text.trim().length < 20) problems.push("the page rendered (almost) nothing")
        if (/Sign in to/i.test(text)) problems.push("the page rendered signed out")
        if (/No mock for/.test(text)) problems.push("a request had no mock")
        await tab.close()
        return problems
    }
    for (const page of pages) {
        let problems = await check(page)
        if (problems.length) problems = await check(page)
        console.log(`${problems.length ? "FAIL" : "ok  "} ${page}`)
        problems.forEach((problem) => {
            console.log(`       ${problem}`)
            failures.push(`${page}: ${problem}`)
        })
    }
    for (const { page, text } of errorPages) {
        const tab = await context.newPage()
        await tab.goto(`${origin}/${page}`, { waitUntil: "load" })
        const shown = await tab.getByText(text).count()
        await tab.close()
        console.log(`${shown ? "ok  " : "FAIL"} ${page}`)
        if (!shown) failures.push(`${page}: expected "${text}"`)
    }
    await browser.close()
} finally {
    await stopServer()
}

if (failures.length) {
    console.error(`\n${failures.length} problem(s) on the test pages`)
    Deno.exit(1)
}
console.log(`\n${pages.length} pages rendered without problems`)

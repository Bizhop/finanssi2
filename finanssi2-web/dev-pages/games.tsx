// Game list with a stateful fake backend: one game for each status and role, so every list control can be tried without a backend.
// Create, join, leave, settings, start, end game and delete change the fake games; ?debug adds debug games and their controls.
import ReactDOM from "react-dom/client"
import { Link, MemoryRouter, Route, Routes, useParams } from "react-router"
import { ToastContainer } from "react-toastify"

import Games from "../src/components/Games.tsx"
import { type StompConnection, StompContext } from "../src/components/StompContext.tsx"
import type { Game } from "../src/components/gameApi.ts"
import { FakeCurrentUser } from "./FakeCurrentUser.tsx"
import { liisa as liisaPerson, olli as olliPerson, pekka as pekkaPerson, type Person, profileOf, publicProfiles, tester } from "./people.ts"

const debug = new URLSearchParams(location.search).has("debug")

// The app names players through /api/users; a debug seat has no account and is named by the app ("Debug player N")
const seat = (playerId: string, piece: number) => ({
    playerId,
    name: "",
    photoUrl: null,
    piece,
    cash: 75_000,
    position: 1,
    car: false,
    loans: 0,
    out: false,
    heldStockTips: [],
})
const seatOf = (person: Person, piece: number) => seat(person.id, piece)
const me = seatOf(tester, 0)
const olli = seatOf(olliPerson, 1)
const pekka = seatOf(pekkaPerson, 2)
const liisa = seatOf(liisaPerson, 3)
const debugSeat = (number: number) => seat(`debug:preview:seat:${number}`, number - 1)

let lastId = 0
const fakeGame = (status: Game["status"], creator: string, players: ReturnType<typeof seat>[], mode: Game["mode"] = "NORMAL") =>
    ({
        mode,
        id: `preview-${(++lastId).toString(16).padStart(6, "0")}`,
        status,
        creator,
        createdAt: Date.now() - lastId * 3_600_000,
        version: 1,
        state: {
            players: players.map((player, piece) => ({ ...player, piece })),
            turnOrder: players.map((player) => player.playerId),
            currentPlayer: status === "RUNNING" ? players[0].playerId : null,
            settings: { loanLimit: "UNLIMITED", compulsorySaleMinimumBid: "HALF_NOMINAL_PRICE", shareholdersMeeting: "ALL_ASSETS_BOUGHT" },
            activeFinanceNews: null,
            finished: status === "FINISHED",
            winner: status === "FINISHED" ? olli.playerId : null,
            finalStandings: [],
            properties: [],
            shares: [],
            bonds: [],
            pendingDecisions: [],
        },
    }) as unknown as Game

let games: Game[] = [
    // Yours, waiting for players: Settings, Start, Leave
    fakeGame("LOBBY", me.playerId, [me, olli]),
    // Someone else's, not joined: Join
    fakeGame("LOBBY", pekka.playerId, [pekka]),
    // Someone else's, joined: Leave
    fakeGame("LOBBY", olli.playerId, [olli, me, liisa]),
    // Yours, running: Open game, End game
    fakeGame("RUNNING", me.playerId, [me, olli, pekka, liisa]),
    // Someone else's, running, you play in it: Open game
    fakeGame("RUNNING", liisa.playerId, [liisa, me]),
    // Running without you: no controls
    fakeGame("RUNNING", pekka.playerId, [pekka, olli]),
    // Finished: Results
    fakeGame("FINISHED", olli.playerId, [olli, me]),
    ...debug
        ? [
            fakeGame("LOBBY", me.playerId, [me, debugSeat(2)], "DEBUG"),
            fakeGame("RUNNING", me.playerId, [me, debugSeat(2), debugSeat(3)], "DEBUG"),
        ]
        : [],
]

// The list reloads on every /topic/games message; the fake backend sends one after each change
const listeners = new Set<(body: string) => void>()
const previewConnection: StompConnection = {
    connected: true,
    subscribe: (destination, onMessage) => {
        if (destination !== "/topic/games") return () => {}
        listeners.add(onMessage)
        return () => listeners.delete(onMessage)
    },
}

const update = (id: string, change: (game: Game) => Game) => {
    games = games.map((game) => game.id === id ? change(game) : game)
    setTimeout(() => listeners.forEach((listener) => listener("{}")))
}

const json = (body: unknown, status = 200) => Promise.resolve(Response.json(body, { status }))

globalThis.fetch = (input, init) => {
    const path = new URL(String(input).replace(/^undefined/, ""), location.origin).pathname
    const method = init?.method ?? "GET"
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    console.info("[mock games]", method, path, body ?? "")
    if (path === "/api/me") return json(profileOf(tester, debug))
    if (path === "/api/users") return json(publicProfiles(new URL(String(input).replace(/^undefined/, ""), location.origin)))
    if (path === "/api/games" && method === "GET") return json(games)
    if (path === "/api/games" && method === "POST") {
        const game = fakeGame("LOBBY", me.playerId, [me])
        games = [game, ...games]
        return json(game)
    }
    if (path === "/api/debug/games" && method === "POST") {
        const seats = Array.from({ length: Number(body?.playerCount ?? 2) }, (_, i) => i === 0 ? me : debugSeat(i + 1))
        const game = fakeGame("LOBBY", me.playerId, seats, "DEBUG")
        games = [game, ...games]
        return json(game)
    }
    // /api/games/<id>/<action> or /api/debug/games/<id>
    const [, id = "", action] = path.match(/\/games\/([^/]+)\/?(\w+)?$/) ?? []
    const game = games.find((item) => item.id === id)
    if (!game) return json({ detail: `No mock for ${path}` }, 404)
    if (method === "DELETE" && path.startsWith("/api/debug/games/")) {
        games = games.filter((item) => item.id !== id)
        return Promise.resolve(new Response(null, { status: 204 }))
    }
    switch (action) {
        case "join":
            update(id, (g) => ({ ...g, state: { ...g.state, players: [...g.state.players, { ...me, piece: g.state.players.length }] } }))
            return json(game)
        case "leave":
            update(id, (g) => ({ ...g, state: { ...g.state, players: g.state.players.filter((player) => player.playerId !== me.playerId) } }))
            return Promise.resolve(new Response(null, { status: 204 }))
        case "settings":
            update(id, (g) => ({ ...g, state: { ...g.state, settings: body } }))
            return json(game)
        case "start":
            update(id, (g) => ({ ...g, status: "RUNNING", state: { ...g.state, currentPlayer: g.state.players[0].playerId } }))
            return json(game)
        case "commands":
            if (body?.type === "EndGame") {
                update(id, (g) => ({ ...g, status: "FINISHED", state: { ...g.state, finished: true, winner: null } }))
                return json([])
            }
            return json({ detail: `The mock only handles EndGame here` }, 409)
    }
    return json({ detail: `No mock for ${method} ${path}` }, 404)
}

// Opening a game only shows where the app would go; the game room has its own test page
const OpenedGame = () => {
    const { id } = useParams()
    return (
        <p>
            The app would open game {id} here. <Link to="/games">Back to the list</Link>
        </p>
    )
}

ReactDOM.createRoot(document.getElementById("app")!).render(
    <MemoryRouter initialEntries={["/games"]}>
        <FakeCurrentUser person={tester} debugMode={debug}>
            <StompContext.Provider value={previewConnection}>
                <Routes>
                    <Route path="/games" element={<Games />} />
                    <Route path="/games/:id" element={<OpenedGame />} />
                </Routes>
                <ToastContainer autoClose={1500} position="top-center" />
            </StompContext.Provider>
        </FakeCurrentUser>
    </MemoryRouter>,
)

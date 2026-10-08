// Game list with a stateful fake backend: one game for each status and role, so every list control can be tried without a backend.
// Create, join, leave, settings, start, end game and delete change the fake games; ?debug adds debug games and their controls.
import { useEffect } from "react"
import ReactDOM from "react-dom/client"
import { Link, MemoryRouter, Route, Routes, useParams } from "react-router"
import { ToastContainer } from "react-toastify"
import type { User as FirebaseUser } from "firebase/auth"

import Games from "../src/components/Games.tsx"
import { CurrentUserProvider, useCurrentUser } from "../src/components/CurrentUserContext.tsx"
import { type StompConnection, StompContext } from "../src/components/StompContext.tsx"
import type { Game } from "../src/components/gameApi.ts"

const debug = new URLSearchParams(location.search).has("debug")

const seat = (playerId: string, name: string, piece: number) => ({
    playerId,
    name,
    photoUrl: null,
    piece,
    cash: 75_000,
    position: 1,
    car: false,
    loans: 0,
    out: false,
    heldStockTips: [],
})
const me = seat("tester", "Maija Meikäläinen", 0)
const olli = seat("olli", "Olli Other", 1)
const pekka = seat("pekka", "Pekka Pörssi", 2)
const liisa = seat("liisa", "Liisa Laine", 3)

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
            fakeGame("LOBBY", me.playerId, [me, seat("seat-2", "Seat 2", 1)], "DEBUG"),
            fakeGame("RUNNING", me.playerId, [me, seat("seat-2", "Seat 2", 1), seat("seat-3", "Seat 3", 2)], "DEBUG"),
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
    if (path === "/api/me/capabilities") return json({ debugMode: debug })
    if (path === "/api/games" && method === "GET") return json(games)
    if (path === "/api/games" && method === "POST") {
        const game = fakeGame("LOBBY", me.playerId, [me])
        games = [game, ...games]
        return json(game)
    }
    if (path === "/api/debug/games" && method === "POST") {
        const seats = Array.from({ length: Number(body?.playerCount ?? 2) }, (_, i) => i === 0 ? me : seat(`seat-${i + 1}`, `Seat ${i + 1}`, i))
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

const PreviewGames = () => {
    const { setUser } = useCurrentUser()
    useEffect(() => {
        setUser({ uid: me.playerId, displayName: me.name, photoURL: null, getIdToken: () => Promise.resolve("preview-token") } as unknown as FirebaseUser)
    }, [setUser])
    return <Games />
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
        <CurrentUserProvider>
            <StompContext.Provider value={previewConnection}>
                <Routes>
                    <Route path="/games" element={<PreviewGames />} />
                    <Route path="/games/:id" element={<OpenedGame />} />
                </Routes>
                <ToastContainer autoClose={1500} position="top-center" />
            </StompContext.Provider>
        </CurrentUserProvider>
    </MemoryRouter>,
)

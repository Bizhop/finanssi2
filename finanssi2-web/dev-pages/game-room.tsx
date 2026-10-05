// Mocked running game for checking the board, assets, active card, commands and responsive layout without a backend.
import { useEffect } from "react"
import ReactDOM from "react-dom/client"
import { MemoryRouter, Route, Routes } from "react-router"
import { ToastContainer } from "react-toastify"
import type { User as FirebaseUser } from "firebase/auth"

import GameRoom from "../src/components/GameRoom.tsx"
import { CurrentUserProvider, useCurrentUser } from "../src/components/CurrentUserContext.tsx"
import { type StompConnection, StompContext } from "../src/components/StompContext.tsx"

// Real game data, the same files the backend serves from /api/game-data
import boardFile from "../../finanssi2-backend/src/main/resources/gamedata/pelilauta.json" with { type: "json" }
import titleDeedFile from "../../finanssi2-backend/src/main/resources/gamedata/hallintatodistukset.json" with { type: "json" }
import shareFile from "../../finanssi2-backend/src/main/resources/gamedata/osakkeet.json" with { type: "json" }
import financeNews from "../../finanssi2-backend/src/main/resources/gamedata/finanssilehdet.json" with { type: "json" }
import stockTips from "../../finanssi2-backend/src/main/resources/gamedata/porssivihjeet.json" with { type: "json" }

const player = { photoUrl: null, car: false, loans: 0, out: false, heldStockTips: [] as string[] }
const user = { ...player, uid: "tester", name: "Maija Meikäläinen", piece: 0, cash: 93_000, position: 14, car: true, loans: 1, heldStockTips: ["PV-25"] }
const olli = { ...player, uid: "olli", name: "Olli Other", piece: 1, cash: 64_500, position: 31, loans: 2 }
const pekka = { ...player, uid: "pekka", name: "Pekka Pörssi", piece: 2, cash: 131_000, position: 30 }
// Shares square 30 with Pekka, whose Sahalaitos she just paid rent for
const liisa = { ...player, uid: "liisa", name: "Liisa Laine", piece: 3, cash: 21_000, position: 30, car: true }

// Ownership that the title deeds allow: Ompelimo is mortgaged unbuilt, Investointiyhtiö can only be mortgaged once built,
// Pysäköintitalo can be neither built on nor mortgaged and Sahalaitos cannot be mortgaged
const owned: Record<number, { owner: string; built?: boolean; mortgaged?: boolean }> = {
    3: { owner: user.uid, mortgaged: true },
    9: { owner: user.uid, built: true },
    10: { owner: user.uid },
    12: { owner: olli.uid, mortgaged: true },
    26: { owner: olli.uid, built: true },
    27: { owner: olli.uid },
    8: { owner: liisa.uid },
    18: { owner: liisa.uid, built: true, mortgaged: true },
    22: { owner: pekka.uid, built: true },
    30: { owner: pekka.uid },
}
const shareOwners: Record<string, string> = {
    "OS-PALVELUYHTIO-1": user.uid,
    "OS-PALVELUYHTIO-2": user.uid,
    "OS-KEMIA-1": olli.uid,
    "OS-FINANSSIYHTYMA-1": liisa.uid,
    "OS-RAHASTO-20": liisa.uid,
    "OS-TEKNIIKKA-1": pekka.uid,
    "OS-TEOLLISUUSKONSERNI-1": pekka.uid,
}
const bondOwners: Record<number, string> = { 1: user.uid, 2: olli.uid, 5: pekka.uid }

const finished = new URLSearchParams(location.search).has("finished")
const debug = new URLSearchParams(location.search).has("debug")
const game = {
    mode: debug ? "DEBUG" : "NORMAL",
    id: "preview-game",
    status: finished ? "FINISHED" : "RUNNING",
    creator: user.uid,
    createdAt: Date.now(),
    version: 1,
    state: {
        players: [user, olli, pekka, liisa],
        turnOrder: [user.uid, olli.uid, pekka.uid, liisa.uid],
        // ?turn=<uid> previews another player in turn, e.g. ?turn=olli
        currentPlayer: new URLSearchParams(location.search).get("turn") ?? user.uid,
        settings: { loanLimit: "UNLIMITED", compulsorySaleMinimumBid: "NONE" },
        activeFinanceNews: "FL-01",
        finished,
        winner: finished ? pekka.uid : null,
        finalStandings: finished
            ? [
                { player: pekka.uid, cash: pekka.cash, netWorth: 556_000, completeGroups: [] },
                { player: user.uid, cash: user.cash, netWorth: 293_000, completeGroups: ["PALVELUYHTIO"] },
                { player: olli.uid, cash: olli.cash, netWorth: 240_500, completeGroups: [] },
                { player: liisa.uid, cash: liisa.cash, netWorth: 211_000, completeGroups: [] },
            ]
            : [],
        properties: titleDeedFile.titleDeeds.map((deed) => ({
            square: deed.square,
            owner: owned[deed.square]?.owner ?? null,
            built: owned[deed.square]?.built ?? false,
            mortgaged: owned[deed.square]?.mortgaged ?? false,
        })),
        shares: shareFile.shares.map((share) => ({ id: share.id, owner: shareOwners[share.id] ?? null })),
        bonds: Array.from({ length: 12 }, (_, i) => ({ number: i + 1, owner: bondOwners[i + 1] ?? null })),
        pendingDecisions: [],
    },
}

const board = {
    squares: boardFile.squares,
    groups: boardFile.groups,
    titleDeeds: titleDeedFile.titleDeeds,
    shares: shareFile.shares,
    financeNews,
    stockTips,
}

const turn = (uid: string, dice: [number, number], from: number) => {
    const to = from + dice[0] + dice[1]
    return [
        { type: "TurnStarted", player: uid },
        { type: "DiceRolled", player: uid, dice },
        { type: "PieceMoved", player: uid, from, to },
        { type: "LandedOn", player: uid, square: to },
    ]
}
const events = [
    ...turn(olli.uid, [5, 6], 20),
    { type: "FinanceNewsDrawn", player: olli.uid, card: "FL-01", replaced: null },
    // FL-01 also raises the car tax: every car owner pays 5,000
    { type: "MoneyTransferred", from: user.uid, to: null, amount: 5_000, reason: "FINANCE_NEWS" },
    { type: "MoneyTransferred", from: liisa.uid, to: null, amount: 5_000, reason: "FINANCE_NEWS" },
    { type: "TurnEnded", player: olli.uid },
    ...turn(pekka.uid, [3, 4], 23),
    { type: "TurnEnded", player: pekka.uid },
    ...turn(liisa.uid, [1, 4], 25),
    { type: "RentCharged", player: liisa.uid, owner: pekka.uid, square: 30, amount: 20_000, doubled: false },
    { type: "MoneyTransferred", from: liisa.uid, to: pekka.uid, amount: 20_000, reason: "RENT" },
    { type: "TurnEnded", player: liisa.uid },
    // Maija's current turn: the Stock Tip she draws and keeps stays face up on the board until the turn ends
    ...turn(user.uid, [3, 4], 7),
    { type: "StockTipDrawn", player: user.uid, card: "PV-25", held: true },
].map((event, index) => ({ id: `preview-game:${index + 1}`, seq: index + 1, time: 0, type: event.type, event }))

const PreviewRoom = () => {
    const { setUser } = useCurrentUser()
    useEffect(() => {
        setUser({ uid: user.uid, displayName: user.name, photoURL: null, getIdToken: () => Promise.resolve("preview-token") } as unknown as FirebaseUser)
    }, [setUser])
    return <GameRoom />
}

const previewConnection: StompConnection = { connected: true, subscribe: () => () => {} }

globalThis.fetch = (input, init) => {
    const path = new URL(String(input).replace(/^undefined/, ""), location.origin).pathname
    if (path.startsWith("/api/games/preview-game/events")) return Promise.resolve(Response.json(events))
    if (path === "/api/games/preview-game") {
        return Promise.resolve(Response.json({
            game,
            actingPlayer: game.state.currentPlayer,
            allowedCommands: finished ? [] : [
                "Roll",
                "EndTurn",
                "BuyCar",
                "BuyProperty",
                "BuyShare",
                "Mortgage",
                "Redeem",
                "SellBackProperty",
                "SellBackShare",
                "Build",
                "CallShareholdersMeeting",
                "UseHeldStockTip",
                "EndGame",
            ],
        }))
    }
    if (path === "/api/me/capabilities") return Promise.resolve(Response.json({ debugMode: debug }))
    if (path === "/api/game-data") return Promise.resolve(Response.json(board))
    if (init?.method === "POST") {
        console.info("[mock game] command", init.body)
        return Promise.resolve(Response.json([]))
    }
    return Promise.resolve(Response.json({ detail: `No mock for ${path}` }, { status: 404 }))
}

ReactDOM.createRoot(document.getElementById("app")!).render(
    <MemoryRouter initialEntries={["/games/preview-game"]}>
        <CurrentUserProvider>
            <StompContext.Provider value={previewConnection}>
                <Routes>
                    <Route path="/games/:id" element={<PreviewRoom />} />
                </Routes>
                <ToastContainer autoClose={1500} position="top-center" />
            </StompContext.Provider>
        </CurrentUserProvider>
    </MemoryRouter>,
)

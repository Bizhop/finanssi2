// Mocked running game for checking the board, assets, active card, commands and responsive layout without a backend.
import { useEffect } from "react"
import ReactDOM from "react-dom/client"
import { MemoryRouter, Route, Routes } from "react-router"
import { ToastContainer } from "react-toastify"
import type { User as FirebaseUser } from "firebase/auth"

import GameRoom from "../src/components/GameRoom.tsx"
import { CurrentUserProvider, useCurrentUser } from "../src/components/CurrentUserContext.tsx"
import { type StompConnection, StompContext } from "../src/components/StompContext.tsx"

const user = {
    uid: "tester",
    name: "Maija Meikäläinen",
    photoUrl: null,
    piece: 0,
    cash: 125_000,
    position: 17,
    car: true,
    loans: 1,
    out: false,
    heldStockTips: ["PV-25"],
}
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
        players: [user, { ...user, uid: "olli", name: "Olli Other", piece: 1, cash: 87_000, position: 34, heldStockTips: [], loans: 0 }],
        turnOrder: [user.uid, "olli"],
        currentPlayer: user.uid,
        settings: { loanLimit: "UNLIMITED", compulsorySaleMinimumBid: "NONE" },
        activeFinanceNews: "FL-01",
        finished,
        winner: finished ? "olli" : null,
        finalStandings: finished
            ? [{ player: "olli", cash: 87_000, netWorth: 143_000, completeGroups: [] }, {
                player: user.uid,
                cash: 125_000,
                netWorth: 136_000,
                completeGroups: [],
            }]
            : [],
        properties: [{ square: 11, owner: null, mortgaged: false, built: false }, { square: 17, owner: user.uid, mortgaged: false, built: true }, {
            square: 34,
            owner: "olli",
            mortgaged: true,
            built: false,
        }],
        shares: [{ id: "K-01", owner: user.uid }, { id: "K-02", owner: "olli" }],
        bonds: [{ number: 1, owner: user.uid }, { number: 2, owner: "olli" }, ...Array.from({ length: 10 }, (_, i) => ({ number: i + 3, owner: null }))],
        pendingDecisions: [],
    },
}

const board = {
    squares: Array.from(
        { length: 46 },
        (_, i) => ({ square: i + 1, name: ["Lähtö", "Pörssi", "Asunto Oy", "Pankki"][i % 4], type: "PROPERTY", group: null, price: null, text: null }),
    ),
    groups: [],
    titleDeeds: [{ square: 17, mortgage: { unbuilt: 10000, built: 20000 } }],
    shares: [{ id: "K-01", group: "K", value: 10_000, dividendPercent: 10, dividend: 1_000, buyBack: 5_000 }],
    financeNews: [{
        id: "FL-01",
        type: "FINANCE_NEWS",
        chapters: [{ type: "header", text: "Korkotaso nousee", "font-style": null }, {
            type: null,
            text: "Lainojen korko kaksinkertaistuu.",
            "font-style": "italic",
        }],
    }],
    stockTips: [{
        id: "PV-25",
        type: "STOCK_TIP",
        chapters: [{ type: "header", text: "Muuttuvat markkinat", "font-style": null }, {
            type: null,
            text: "Valitse mihin suuntaan liikut.",
            "font-style": null,
        }],
    }],
}

const events = [
    { type: "TurnStarted", player: user.uid },
    { type: "DiceRolled", player: user.uid, dice: [3, 4] },
    { type: "PieceMoved", player: user.uid, from: 10, to: 17 },
    { type: "LandedOn", player: user.uid, square: 17 },
    { type: "StockTipDrawn", player: user.uid, card: "PV-25", held: true },
    { type: "TurnEnded", player: user.uid },
    { type: "TurnStarted", player: "olli" },
    { type: "DiceRolled", player: "olli", dice: [2, 6] },
    { type: "LandedOn", player: "olli", square: 25 },
    { type: "FinanceNewsDrawn", player: "olli", card: "FL-01", replaced: null },
    { type: "MoneyTransferred", from: "olli", to: null, amount: 5_000, reason: "FINANCE_NEWS" },
    { type: "NotImplemented", player: "olli", square: 26, squareType: "CONSTRUCTION" },
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

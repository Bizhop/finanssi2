// Mocked running game for checking the board, assets, active card, commands and responsive layout without a backend.
import { useEffect, useMemo, useState } from "react"
import ReactDOM from "react-dom/client"
import { MemoryRouter, Route, Routes } from "react-router"
import { ToastContainer } from "react-toastify"
import type { User as FirebaseUser } from "firebase/auth"
import { Box, Button, Container, Divider, Grid, IconButton, Paper, Stack, Tooltip } from "@mui/material"
import HomeIcon from "@mui/icons-material/Home"
import LogoutIcon from "@mui/icons-material/Logout"
import CasinoIcon from "@mui/icons-material/Casino"
import { NavLink } from "react-router"

import GameRoom from "../src/components/GameRoom.tsx"
import { CurrentUserProvider, useCurrentUser } from "../src/components/CurrentUserContext.tsx"
import { type StompConnection, StompContext } from "../src/components/StompContext.tsx"
import { GAME_BOARD_MAX_WIDTH, GAME_ROOM_MAX_WIDTH } from "../src/components/boardLayout.ts"

// Real game data, the same files the backend serves from /api/game-data
import boardFile from "../../finanssi2-backend/src/main/resources/gamedata/pelilauta.json" with { type: "json" }
import titleDeedFile from "../../finanssi2-backend/src/main/resources/gamedata/hallintatodistukset.json" with { type: "json" }
import shareFile from "../../finanssi2-backend/src/main/resources/gamedata/osakkeet.json" with { type: "json" }
import financeNews from "../../finanssi2-backend/src/main/resources/gamedata/finanssilehdet.json" with { type: "json" }
import stockTips from "../../finanssi2-backend/src/main/resources/gamedata/porssivihjeet.json" with { type: "json" }

const player = { photoUrl: null, car: false, loans: 0, out: false, heldStockTips: [] as string[] }
// ?afterRoll previews Maija's turn after rolling: she has moved on to Pörssivihje (20) and drawn a second card to keep
const afterRoll = new URLSearchParams(location.search).has("afterRoll")
// No car, so the car icon offers to buy one and she rolls one die. By default her turn starts on square 40 (a construction square
// in the head office), so before rolling she may both build and buy from the bank.
const user = {
    ...player,
    uid: "tester",
    name: "Maija Meikäläinen",
    piece: 0,
    // Enough for the €125,000 Finanssiyhtymä takeover with a €20,000 or €30,000 brokerage fee, not more
    cash: 158_000,
    position: afterRoll ? 20 : 40,
    loans: 1,
    heldStockTips: afterRoll ? ["PV-25", "PV-01"] : ["PV-25"],
}
const olli = { ...player, uid: "olli", name: "Olli Other", piece: 1, cash: 64_500, position: 31, loans: 2 }
// Olli and Pekka have no car either, so they roll one die; Liisa rolls two
const pekka = { ...player, uid: "pekka", name: "Pekka Pörssi", piece: 2, cash: 131_000, position: 30 }
// Shares square 30 with Pekka, whose Sahalaitos she just paid rent for
const liisa = { ...player, uid: "liisa", name: "Liisa Laine", piece: 3, cash: 21_000, position: 30, car: true }

// Ownership that the title deeds allow: Ompelimo is mortgaged unbuilt, Investointiyhtiö can only be mortgaged once built,
// Pysäköintitalo can be neither built on nor mortgaged and Sahalaitos cannot be mortgaged
const owned: Record<number, { owner: string; built?: boolean; mortgaged?: boolean }> = {
    3: { owner: user.uid, mortgaged: true },
    9: { owner: user.uid, built: true },
    // Finanssiyhtymä is split between Maija and Liisa with nothing left in the bank, so Maija may call a shareholders' meeting
    19: { owner: user.uid },
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
    "OS-FINANSSIYHTYMA-2": user.uid,
    "OS-RAHASTO-20": liisa.uid,
    "OS-TEKNIIKKA-1": pekka.uid,
    "OS-TEOLLISUUSKONSERNI-1": pekka.uid,
}
const bondOwners: Record<number, string> = { 1: user.uid, 2: olli.uid, 5: pekka.uid }

const finished = new URLSearchParams(location.search).has("finished")
const debug = new URLSearchParams(location.search).has("debug")
const appLayout = location.pathname.endsWith("game-room-layout.html")

// Visual copy of the app menu, without initializing Firebase in this mock-only page.
const PreviewHeader = () => (
    <Box>
        <Grid container spacing={1}>
            <Grid size={1} sx={{ textAlign: "center" }}>
                <NavLink to="/">
                    <Tooltip title="Front Page">
                        <IconButton size="small">
                            <HomeIcon />
                        </IconButton>
                    </Tooltip>
                </NavLink>
            </Grid>
            <Grid size={1} sx={{ textAlign: "center" }}>
                <NavLink to="/games">
                    <Tooltip title="Games">
                        <IconButton size="small">
                            <CasinoIcon />
                        </IconButton>
                    </Tooltip>
                </NavLink>
            </Grid>
            <Grid size={1} offset="auto">
                <Tooltip title="Log out">
                    <IconButton color="error" size="small">
                        <LogoutIcon />
                    </IconButton>
                </Tooltip>
            </Grid>
        </Grid>
    </Box>
)
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
        settings: { loanLimit: "UNLIMITED", compulsorySaleMinimumBid: "HALF_NOMINAL_PRICE", shareholdersMeeting: "ALL_ASSETS_BOUGHT" },
        // ?news=<id> shows another Finance News card face up, e.g. ?news=FL-19 for the longest text
        activeFinanceNews: new URLSearchParams(location.search).get("news") ?? "FL-01",
        phase: afterRoll ? "AFTER_ROLL" : "BEFORE_ROLL",
        boughtThisTurn: false,
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

const turn = (uid: string, dice: number[], from: number) => {
    const to = from + dice.reduce((sum, value) => sum + value, 0)
    return [
        { type: "TurnStarted", player: uid },
        { type: "DiceRolled", player: uid, dice },
        { type: "PieceMoved", player: uid, from, to },
        { type: "LandedOn", player: uid, square: to },
    ]
}
const events = [
    // Maija's previous turn: with ?afterRoll from the branch office (11) to Rakennusprojekti Oy (17), otherwise from the bank entrance
    // (34), where everyone rolls one die, into the head office to square 40
    ...afterRoll ? turn(user.uid, [6], 11) : turn(user.uid, [6], 34),
    { type: "NotImplemented", player: user.uid, square: afterRoll ? 17 : 40, squareType: "CONSTRUCTION" },
    { type: "TurnEnded", player: user.uid },
    ...turn(olli.uid, [5], 26),
    { type: "FinanceNewsDrawn", player: olli.uid, card: "FL-01", replaced: null },
    // FL-01 also raises the car tax: every car owner pays 5,000
    { type: "MoneyTransferred", from: liisa.uid, to: null, amount: 5_000, reason: "FINANCE_NEWS" },
    { type: "TurnEnded", player: olli.uid },
    ...turn(pekka.uid, [2], 28),
    { type: "TurnEnded", player: pekka.uid },
    ...turn(liisa.uid, [1, 4], 25),
    { type: "RentCharged", player: liisa.uid, owner: pekka.uid, square: 30, amount: 20_000, doubled: false },
    { type: "MoneyTransferred", from: liisa.uid, to: pekka.uid, amount: 20_000, reason: "RENT" },
    { type: "TurnEnded", player: liisa.uid },
    // Maija's current turn, before rolling unless ?afterRoll
    { type: "TurnStarted", player: user.uid },
    ...afterRoll
        ? [
            ...turn(user.uid, [3], 17).slice(1),
            // Stays face up on the board until the turn ends
            // ?tip=<id> shows another Stock Tip, e.g. ?afterRoll&tip=PV-31 for the longest text (not one she keeps)
            new URLSearchParams(location.search).has("tip")
                ? { type: "StockTipDrawn", player: user.uid, card: new URLSearchParams(location.search).get("tip"), held: false }
                : { type: "StockTipDrawn", player: user.uid, card: "PV-01", held: true },
        ]
        : [],
].map((event, index) => ({ id: `preview-game:${index + 1}`, seq: index + 1, time: 0, type: event.type, event }))

const PreviewRoom = () => {
    const { setUser } = useCurrentUser()
    const [connected, setConnected] = useState(true)
    const [viewer, setViewer] = useState(false)
    const previewConnection = useMemo<StompConnection>(() => ({
        connected,
        subscribe: (destination, onMessage) => {
            if (connected && destination === chatTopic) chatSubscribers.add(onMessage)
            return () => chatSubscribers.delete(onMessage)
        },
    }), [connected])
    useEffect(() => {
        setUser(
            {
                uid: viewer ? "viewer" : user.uid,
                displayName: viewer ? "Read Only Viewer" : user.name,
                photoURL: null,
                getIdToken: () => Promise.resolve("preview-token"),
            } as unknown as FirebaseUser,
        )
    }, [setUser, viewer])
    const sendIncoming = () =>
        publishFakeGameMessage({
            id: String(nextChatId++).padStart(19, "0"),
            username: "olli@example.com",
            name: "Olli Other",
            message: "Live message from another player",
            timestamp: Date.now(),
            photoUrl: null,
        })
    return (
        <StompContext.Provider value={previewConnection}>
            <Stack spacing={1} sx={{ minHeight: 0, flex: 1 }}>
                <Stack
                    direction="row"
                    spacing={1}
                    sx={{
                        flexWrap: "wrap",
                        ...(appLayout &&
                            {
                                position: "fixed",
                                top: 16,
                                left: "50%",
                                transform: "translateX(-50%)",
                                zIndex: 1300,
                                bgcolor: "background.paper",
                                p: 1,
                                borderRadius: 1,
                                boxShadow: 2,
                            }),
                    }}
                >
                    <Button size="small" onClick={sendIncoming}>Simulate incoming chat</Button>
                    <Button size="small" onClick={() => setConnected((current) => !current)}>{connected ? "Disconnect" : "Reconnect"}</Button>
                    <Button size="small" onClick={() => setViewer((current) => !current)}>{viewer ? "Use seated account" : "View as spectator"}</Button>
                </Stack>
                <GameRoom />
            </Stack>
        </StompContext.Provider>
    )
}

let nextChatId = 1
const chatTopic = "/topic/games/preview-game/chat"
const chatSubscribers = new Set<(body: string) => void>()
const gameChatHistory = Array.from({ length: 27 }, (_, index) => ({
    id: String(nextChatId++).padStart(19, "0"),
    username: index % 2 ? "olli@example.com" : "tester@example.com",
    name: index % 2 ? "Olli Other" : "Maija Meikäläinen",
    message: index % 6 === 4 ? "A longer game-room message that wraps across lines while the chat list scrolls independently." : `Game message ${index + 1}`,
    timestamp: Date.now() - (27 - index) * 60_000,
    photoUrl: null,
}))

const publishFakeGameMessage = (message: (typeof gameChatHistory)[number]) => {
    gameChatHistory.push(message)
    chatSubscribers.forEach((callback) => callback(JSON.stringify(message)))
}

globalThis.fetch = (input, init) => {
    const path = new URL(String(input).replace(/^undefined/, ""), location.origin).pathname
    if (path === "/api/games/preview-game/chat") {
        if (init?.method === "POST") {
            const body = JSON.parse(String(init.body)) as { message: string }
            const saved = {
                id: String(nextChatId++).padStart(19, "0"),
                username: "tester@example.com",
                name: "Maija Meikäläinen",
                message: body.message,
                timestamp: Date.now(),
                photoUrl: null,
            }
            publishFakeGameMessage(saved)
            return Promise.resolve(Response.json(saved))
        }
        const params = new URL(String(input).replace(/^undefined/, ""), location.origin).searchParams
        const before = params.get("before")
        const size = Number(params.get("size") ?? 20)
        const page = gameChatHistory.filter((message) => before === null || message.id < before).slice(-size).reverse()
        return Promise.resolve(Response.json(page))
    }
    if (path.startsWith("/api/games/preview-game/events")) return Promise.resolve(Response.json(events))
    if (path === "/api/games/preview-game") {
        return Promise.resolve(Response.json({
            game,
            actingPlayer: game.state.currentPlayer,
            // What the backend allows Maija: building and buying (on 40) only before the roll, ending the turn only after it.
            // FL-01 is active, so the bank grants no new loans.
            allowedCommands: finished || game.state.currentPlayer !== user.uid
                ? []
                : afterRoll
                ? ["EndTurn", "RepayLoan", "Mortgage", "SellBackProperty", "SellBackShare", "UseHeldStockTip", "Resign"]
                : [
                    "Roll",
                    "BuyCar",
                    "RepayLoan",
                    "BuyProperty",
                    "BuyShare",
                    "Build",
                    "Mortgage",
                    "Redeem",
                    "SellBackProperty",
                    "SellBackShare",
                    "UseHeldStockTip",
                    "CallShareholdersMeeting",
                    "Resign",
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
            {appLayout
                ? (
                    <Container
                        maxWidth={false}
                        component={Paper}
                        sx={{
                            height: "100%",
                            display: "flex",
                            flexDirection: "column",
                            pb: 2,
                            "@media (min-width: 2200px) and (min-height: 1100px)": { maxWidth: GAME_ROOM_MAX_WIDTH },
                            "@media (min-width: 1536px) and (max-width: 2199.95px)": { maxWidth: GAME_BOARD_MAX_WIDTH },
                            "@media (min-width: 2200px) and (max-height: 1099.95px)": { maxWidth: GAME_BOARD_MAX_WIDTH },
                        }}
                    >
                        <Stack direction="column" sx={{ flex: 1, minHeight: 0 }}>
                            <PreviewHeader />
                            <Divider />
                            <Routes>
                                <Route path="/games/:id" element={<PreviewRoom />} />
                            </Routes>
                        </Stack>
                    </Container>
                )
                : (
                    <Routes>
                        <Route path="/games/:id" element={<PreviewRoom />} />
                    </Routes>
                )}
            <ToastContainer autoClose={1500} position="top-center" />
        </CurrentUserProvider>
    </MemoryRouter>,
)

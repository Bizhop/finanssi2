import { useCallback, useEffect, useRef, useState } from "react"
import { Link, useNavigate, useParams } from "react-router"
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Grid,
    Stack,
    TextField,
    Tooltip,
    Typography,
} from "@mui/material"
import { toast } from "react-toastify"

import { CardFace, GameBoard } from "./GameBoard.tsx"
import { PlayerPanel } from "./PlayerPanel.tsx"
import { useCurrentUser } from "./CurrentUserContext.tsx"
import { useStompConnected, useStompSubscription } from "./StompContext.tsx"
import { Game, gameApi, GameApiError, GameBoardData, GameView } from "./gameApi.ts"
import { describeEvent, GameLogEntry } from "./gameEvents.ts"

type GameUpdate = { version: number; events: GameLogEntry[] }

const apiError = (reason: unknown) => reason instanceof Error ? reason.message : "The request failed"

const GameRoomContent = () => {
    const { id } = useParams()
    const { user, debugMode, capabilitiesReady, clearDebugAccess } = useCurrentUser()
    const navigate = useNavigate()
    // The GameRoom wrapper remounts this per game and user; late responses after unmount must not toast or navigate.
    const mounted = useRef(true)
    const refreshSequence = useRef(0)
    const busyRef = useRef(false)
    const connected = useStompConnected()
    const [view, setView] = useState<GameView | null>(null)
    const [board, setBoard] = useState<GameBoardData | null>(null)
    const [events, setEvents] = useState<GameLogEntry[]>([])
    const [error, setError] = useState<string | null>(null)
    const [commandBusy, setCommandBusy] = useState(false)
    const [bid, setBid] = useState("0")
    const [choiceOpen, setChoiceOpen] = useState(false)
    const [endGameOpen, setEndGameOpen] = useState(false)
    const [diceInput, setDiceInput] = useState("")
    const [nextNews, setNextNews] = useState("")
    const [nextTip, setNextTip] = useState("")
    const [deleteOpen, setDeleteOpen] = useState(false)
    const [buySquare, setBuySquare] = useState("")
    const [meetingGroup, setMeetingGroup] = useState("")
    const [meetingFee, setMeetingFee] = useState(20000)

    const refresh = useCallback(async () => {
        if (!user || !id) return
        const sequence = ++refreshSequence.current
        try {
            const latest = await gameApi<GameView>(user, `/api/games/${id}`)
            if (!mounted.current || sequence !== refreshSequence.current) return
            setView(latest)
            setError(null)
        } catch (reason) {
            if (!mounted.current || sequence !== refreshSequence.current) return
            if (reason instanceof GameApiError && (reason.status === 403 || reason.status === 404)) {
                if (reason.status === 403) clearDebugAccess()
                setView(null)
                navigate("/games", { replace: true })
            }
            setError(apiError(reason))
        }
    }, [id, user, clearDebugAccess, navigate])

    const refreshEvents = useCallback(async (after = 0) => {
        if (!user || !id) return
        try {
            const latest = await gameApi<GameLogEntry[]>(user, `/api/games/${id}/events?after=${after}`)
            if (!mounted.current) return
            setEvents((current) => [...new Map([...current, ...latest].map((entry) => [entry.seq, entry])).values()].sort((a, b) => a.seq - b.seq))
        } catch (reason) {
            if (mounted.current) toast(apiError(reason), { type: "error" })
        }
    }, [id, user])

    useEffect(() => {
        mounted.current = true
        return () => {
            mounted.current = false
        }
    }, [])

    useEffect(() => {
        void refresh()
        void refreshEvents()
        if (user) {
            void gameApi<GameBoardData>(user, "/api/game-data").then((data) => {
                if (mounted.current) setBoard(data)
            }).catch((reason) => setError(apiError(reason)))
        }
    }, [refresh, refreshEvents, user])

    const receiveUpdate = useCallback((body: string) => {
        if (!mounted.current) return
        try {
            const update = JSON.parse(body) as GameUpdate & { deleted?: boolean }
            if (update.deleted) {
                navigate("/games", { replace: true })
                return
            }
            const currentSequence = events.at(-1)?.seq ?? 0
            const hasGap = update.events.some((entry, index) => entry.seq > currentSequence + index + 1)
            if (hasGap) void refreshEvents(currentSequence)
            else setEvents((current) => [...new Map([...current, ...update.events].map((entry) => [entry.seq, entry])).values()].sort((a, b) => a.seq - b.seq))
            // State is not included in broadcasts, so reload it after every event update.
            void refresh()
        } catch (reason) {
            console.error("Invalid game update", reason)
            void refresh()
            void refreshEvents(events.at(-1)?.seq ?? 0)
        }
    }, [events, refresh, refreshEvents, navigate])

    useStompSubscription(`/topic/games/${id}`, receiveUpdate)

    useEffect(() => {
        if (connected) {
            void refresh()
            void refreshEvents(events.at(-1)?.seq ?? 0)
        }
    }, [connected])

    /** Runs an action, then reloads the game unless the action leaves it (`reload` false) */
    const mutate = async (action: () => Promise<unknown>, reload = true) => {
        if (busyRef.current) return
        busyRef.current = true
        setCommandBusy(true)
        try {
            await action()
            if (!mounted.current || !reload) return
            await refresh()
            await refreshEvents(events.at(-1)?.seq ?? 0)
        } catch (reason) {
            if (!mounted.current) return
            toast(apiError(reason), { type: "error" })
            if (reason instanceof GameApiError && reason.status === 403 && view?.game.mode === "DEBUG") {
                clearDebugAccess()
                setView(null)
                navigate("/games", { replace: true })
            } else {
                // A conflict reloads the view; the user must submit a new action for its actor/version.
                await refresh()
            }
        } finally {
            busyRef.current = false
            setCommandBusy(false)
        }
    }

    const sendCommand = async (command: Record<string, unknown>) => {
        if (!user || !id || !view || busyRef.current) return
        const debug = view.game.mode === "DEBUG"
        if (debug && !debugMode) return
        const dice = diceInput.trim() ? diceInput.trim().split(/[\s,]+/).map(Number) : undefined
        if (debug && dice && (dice.length > 32 || dice.some((value) => !Number.isInteger(value) || value < 1 || value > 6))) {
            toast("Dice must contain at most 32 values from 1 to 6", { type: "error" })
            return
        }
        await mutate(async () => {
            await gameApi(user, debug ? `/api/debug/games/${id}/commands` : `/api/games/${id}/commands`, {
                method: "POST",
                body: JSON.stringify(debug ? { actor: view.actingPlayer, expectedVersion: view.game.version, command, dice } : command),
            })
            // Cleared only once used, so a rejected command can be retried with the same dice
            setDiceInput("")
        })
    }

    const selectCard = (deck: string, card: string) => {
        if (!user || !id || !view || !debugMode || !card) return
        void mutate(() =>
            gameApi(user, `/api/debug/games/${id}/next-card`, {
                method: "PUT",
                body: JSON.stringify({ deck, card, expectedVersion: view.game.version }),
            })
        )
    }

    useEffect(() => {
        if (view?.game.mode === "DEBUG" && capabilitiesReady && !debugMode) {
            setView(null)
            navigate("/games", { replace: true })
        }
    }, [view, debugMode, capabilitiesReady, navigate])

    if (!user) return <Alert severity="info" sx={{ mt: 2 }}>Sign in to open this game.</Alert>
    if (!view && !error) {
        return (
            <Box sx={{ display: "flex", justifyContent: "center", p: 5 }}>
                <CircularProgress />
            </Box>
        )
    }
    if (error && !view) return <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>
    if (!view) return null
    if (view.game.mode === "DEBUG" && !debugMode) return <CircularProgress />

    const { game, allowedCommands } = view
    const actingUid = game.mode === "DEBUG" ? view.actingPlayer : user.uid
    const actingPlayer = game.state.players.find((player) => player.uid === actingUid)
    const pending = game.state.pendingDecisions[0]
    const decisionActor = pending?.player === actingUid
    // Whoever the game is waiting for: the player of a pending decision, otherwise the player in turn
    const expectedUid = pending?.player ?? game.state.currentPlayer
    const squareByNumber = new Map(board?.squares.map((square) => [square.square, square]) ?? [])
    const simpleCommands = ["Roll", "EndTurn", "BuyCar", "SellCar", "TakeLoan", "RepayLoan", "Pay", "DeclareBankruptcy", "Pass", "Resign"]
    const playerName = (uid: string) => game.state.players.find((player) => player.uid === uid)?.name ?? "a former player"
    // Only a Stock Tip drawn during the current turn stays face up on the board
    const turnStockTip = events.slice(events.findLastIndex((entry) => entry.type === "TurnStarted") + 1).findLast((entry) => entry.type === "StockTipDrawn")
    const usableStockTips = !pending && allowedCommands.includes("UseHeldStockTip") ? actingPlayer?.heldStockTips.filter((id) => id === "PV-25") ?? [] : []
    const commands = pending ? allowedCommands.filter((command) => command === "Resign") : allowedCommands.filter((command) => simpleCommands.includes(command))

    return (
        <Stack component="fieldset" disabled={commandBusy} spacing={2} sx={{ border: 0, m: 0, px: 0, flex: 1, minHeight: 0, py: 2, overflowY: "auto" }}>
            <Stack direction="row" sx={{ justifyContent: "flex-end" }}>
                <Button component={Link} to="/games">All games</Button>
            </Stack>
            {game.mode === "DEBUG" && debugMode && (
                <>
                    <Alert severity="info">Controlling: {actingPlayer?.name ?? "—"}</Alert>
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                        <TextField
                            size="small"
                            label="Dice for next submitted command"
                            placeholder="e.g. 3, 6"
                            value={diceInput}
                            onChange={(event) => setDiceInput(event.target.value)}
                            helperText="Optional; unused values are discarded."
                        />
                        {game.status === "RUNNING" && !pending && (
                            <>
                                <TextField
                                    select
                                    size="small"
                                    label="Next Finance News"
                                    value={nextNews}
                                    onChange={(event) => setNextNews(event.target.value)}
                                    slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
                                >
                                    <option value="">Choose…</option>
                                    {board?.financeNews.map((card) => <option key={card.id} value={card.id}>{card.id} · {card.chapters[0]?.text}</option>)}
                                </TextField>
                                <Button disabled={!nextNews || commandBusy} onClick={() => selectCard("FINANCE_NEWS", nextNews)}>Set next news</Button>
                                <TextField
                                    select
                                    size="small"
                                    label="Next Stock Tip"
                                    value={nextTip}
                                    onChange={(event) => setNextTip(event.target.value)}
                                    slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
                                >
                                    <option value="">Choose…</option>
                                    {board?.stockTips.filter((card) => !game.state.players.some((player) => player.heldStockTips.includes(card.id))).map((
                                        card,
                                    ) => <option key={card.id} value={card.id}>{card.id} · {card.chapters[0]?.text}</option>)}
                                </TextField>
                                <Button disabled={!nextTip || commandBusy} onClick={() => selectCard("STOCK_TIP", nextTip)}>Set next tip</Button>
                            </>
                        )}
                        <Button color="error" onClick={() => setDeleteOpen(true)}>Delete debug game</Button>
                    </Stack>
                </>
            )}
            {error && <Alert severity="warning">{error}</Alert>}
            <Grid container spacing={2}>
                <Grid size={{ xs: 12, lg: 8 }}>
                    <GameBoard
                        game={game}
                        board={board}
                        lastStockTip={turnStockTip
                            ? {
                                card: String(turnStockTip.event.card),
                                drawnBy: playerName(String(turnStockTip.event.player)),
                                held: Boolean(turnStockTip.event.held),
                            }
                            : null}
                    />
                </Grid>
                <Grid size={{ xs: 12, lg: 4 }}>
                    <Stack spacing={1.5}>
                        {game.state.players.map((player) => (
                            <PlayerPanel
                                key={player.uid}
                                player={player}
                                game={game}
                                board={board}
                                you={player.uid === user.uid}
                                inTurn={game.status === "RUNNING" && player.uid === game.state.currentPlayer}
                                expected={game.status !== "RUNNING" || expectedUid !== player.uid
                                    ? null
                                    : pending
                                    ? player.uid === actingUid ? "Your decision" : "Deciding"
                                    : player.uid === actingUid
                                    ? "Your move"
                                    : "To move"}
                            />
                        ))}
                        <Card variant="outlined">
                            <CardContent>
                                <Typography sx={{ fontWeight: 700 }}>Event log</Typography>
                                <Stack spacing={0.5} sx={{ maxHeight: 480, overflowY: "auto", mt: 0.5 }}>
                                    {events.slice(-200).reverse().map((entry) => (
                                        <EventLine key={entry.seq} entry={entry} text={describeEvent(entry, { playerName, board })} board={board} />
                                    ))}
                                </Stack>
                            </CardContent>
                        </Card>
                        {game.state.finished && (
                            <Alert severity="success">
                                {game.state.winner
                                    ? `${game.state.players.find((p) => p.uid === game.state.winner)?.name} wins.`
                                    : "The game was closed without a winner."}
                            </Alert>
                        )}
                    </Stack>
                </Grid>
            </Grid>
            {pending && (
                <Alert severity="warning">
                    Waiting for {game.state.players.find((player) => player.uid === pending.player)?.name ?? "a player"} to resolve{" "}
                    {pending.type.replace(/([A-Z])/g, " $1").trim()}.
                </Alert>
            )}
            {decisionActor && pending && (
                <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
                    <Typography sx={{ fontWeight: 600, width: "100%" }}>Your decision</Typography>
                    {decisionCommands(
                        pending,
                        allowedCommands,
                        sendCommand,
                        bid,
                        setBid,
                        () => setChoiceOpen(true),
                        game.state.properties,
                        game.state.shares,
                        game.state.bonds,
                        board,
                    )}
                </Stack>
            )}
            {(commands.length > 0 || usableStockTips.length > 0) && (
                <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
                    <Typography sx={{ fontWeight: 600, width: "100%" }}>Actions</Typography>
                    {commands.map((command) => (
                        <Button
                            key={command}
                            variant={command === "Roll" ? "contained" : "outlined"}
                            disabled={commandBusy}
                            onClick={() => void sendCommand({ type: command })}
                        >
                            {commandLabel(command)}
                        </Button>
                    ))}
                    {usableStockTips.map((id) => {
                        const card = board?.stockTips.find((item) => item.id === id)
                        return (
                            <Tooltip
                                key={id}
                                slotProps={{ tooltip: { sx: { p: 0, maxWidth: "none", backgroundColor: "transparent", boxShadow: 6 } } }}
                                title={
                                    <Box sx={{ width: 240, minHeight: 327, display: "flex", fontSize: 15 }}>
                                        <CardFace card={card} fallback="Stock Tip" />
                                    </Box>
                                }
                            >
                                <Button variant="outlined" disabled={commandBusy} onClick={() => void sendCommand({ type: "UseHeldStockTip", card: id })}>
                                    Use “{card?.chapters.find((chapter) => chapter.type === "header")?.text ?? "Stock Tip"}”
                                </Button>
                            </Tooltip>
                        )
                    })}
                </Stack>
            )}
            {game.status === "RUNNING" && !pending && game.state.currentPlayer === actingUid && (
                <Stack spacing={1.25}>
                    <Typography variant="h6">Your assets and actions</Typography>
                    {allowedCommands.includes("BuyProperty") && (
                        <Stack direction="row" spacing={1}>
                            <TextField
                                select
                                size="small"
                                label="Bank property"
                                value={buySquare}
                                onChange={(event) => setBuySquare(event.target.value)}
                                slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
                            >
                                <option value="">Choose…</option>
                                {game.state.properties.filter((property) => property.owner === null).map((property) => (
                                    <option key={property.square} value={property.square}>
                                        {squareByNumber.get(property.square)?.name ?? property.square}
                                    </option>
                                ))}
                            </TextField>
                            <Button disabled={!buySquare || commandBusy} onClick={() => void sendCommand({ type: "BuyProperty", square: Number(buySquare) })}>
                                Buy property
                            </Button>
                        </Stack>
                    )}
                    {allowedCommands.includes("BuyShare") && (
                        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
                            {game.state.shares.filter((share) => !share.owner).map((share) => (
                                <Button
                                    key={share.id}
                                    onClick={() => void sendCommand({ type: "BuyShare", share: share.id })}
                                >
                                    Buy {share.id} (€{board?.shares.find((item) => item.id === share.id)?.value.toLocaleString() ?? "?"})
                                </Button>
                            ))}
                        </Stack>
                    )}
                    {allowedCommands.includes("Mortgage") && (
                        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
                            {game.state.properties.filter((property) =>
                                property.owner === actingUid && !property.mortgaged &&
                                board?.titleDeeds.find((deed) => deed.square === property.square)?.mortgage?.[property.built ? "built" : "unbuilt"] != null
                            ).map((property) => (
                                <Button
                                    key={property.square}
                                    onClick={() => void sendCommand({ type: "Mortgage", square: property.square })}
                                >
                                    Mortgage {squareByNumber.get(property.square)?.name}
                                </Button>
                            ))}
                        </Stack>
                    )}
                    {allowedCommands.includes("Redeem") && (
                        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
                            {game.state.properties.filter((property) => property.owner === actingUid && property.mortgaged).map((property) => (
                                <Button
                                    key={property.square}
                                    onClick={() => void sendCommand({ type: "Redeem", square: property.square })}
                                >
                                    Redeem {squareByNumber.get(property.square)?.name}
                                </Button>
                            ))}
                        </Stack>
                    )}
                    {allowedCommands.includes("SellBackProperty") && (
                        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
                            {game.state.properties.filter((property) =>
                                property.owner === actingUid && !property.mortgaged &&
                                board?.titleDeeds.find((deed) => deed.square === property.square)?.buyBack?.[property.built ? "built" : "unbuilt"] != null
                            ).map((property) => (
                                <Button
                                    key={property.square}
                                    onClick={() => void sendCommand({ type: "SellBackProperty", square: property.square })}
                                >
                                    Sell {squareByNumber.get(property.square)?.name}
                                </Button>
                            ))}
                        </Stack>
                    )}
                    {allowedCommands.includes("SellBackShare") && (
                        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
                            {game.state.shares.filter((share) => share.owner === actingUid).map((share) => (
                                <Button
                                    key={share.id}
                                    onClick={() => void sendCommand({ type: "SellBackShare", share: share.id })}
                                >
                                    Sell {share.id}
                                </Button>
                            ))}
                        </Stack>
                    )}
                    {allowedCommands.includes("Build") &&
                        game.state.properties.filter((property) =>
                            property.owner === actingUid && !property.built && !property.mortgaged &&
                            board?.titleDeeds.some((deed) => deed.square === property.square && deed.building)
                        ).map((property) => (
                            <Button key={property.square} onClick={() => void sendCommand({ type: "Build", squares: [property.square] })}>
                                Build on {squareByNumber.get(property.square)?.name ?? property.square}
                            </Button>
                        ))}
                    {allowedCommands.includes("CallShareholdersMeeting") && (
                        <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                            <TextField
                                select
                                size="small"
                                label="Takeover group"
                                value={meetingGroup}
                                onChange={(event) => setMeetingGroup(event.target.value)}
                                slotProps={{ select: { native: true } }}
                            >
                                <option value="">Choose…</option>
                                {(board?.groups ?? []).map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}
                            </TextField>
                            <TextField
                                select
                                size="small"
                                label="Brokerage fee"
                                value={meetingFee}
                                onChange={(event) => setMeetingFee(Number(event.target.value))}
                                slotProps={{ select: { native: true } }}
                            >
                                {[20000, 30000, 40000, 50000, 60000, 70000, 80000, 90000, 100000, 110000, 120000].map((fee) => (
                                    <option key={fee} value={fee}>€{fee.toLocaleString()}</option>
                                ))}
                            </TextField>
                            <Button
                                disabled={!meetingGroup}
                                onClick={() => void sendCommand({ type: "CallShareholdersMeeting", group: meetingGroup, brokerageFee: meetingFee })}
                            >
                                Call meeting
                            </Button>
                        </Stack>
                    )}
                </Stack>
            )}
            {game.state.finished && game.state.finalStandings.length > 0 && (
                <>
                    <Typography variant="h6">Final standings</Typography>
                    {[...game.state.finalStandings].sort((a, b) => b.netWorth - a.netWorth).map((standing, index) => (
                        <Typography key={standing.player}>
                            {index + 1}. {game.state.players.find((p) => p.uid === standing.player)?.name ?? standing.player}{" "}
                            — €{standing.netWorth.toLocaleString()} net worth (€{standing.cash.toLocaleString()} cash)
                        </Typography>
                    ))}
                </>
            )}
            {allowedCommands.includes("EndGame") && <Button color="error" onClick={() => setEndGameOpen(true)}>End game</Button>}
            <Dialog open={choiceOpen} onClose={() => setChoiceOpen(false)}>
                <DialogTitle>Choose a Stock Tip effect</DialogTitle>
                <DialogContent>
                    <Stack spacing={1} sx={{ pt: 1 }}>
                        {pending?.type === "StockTipChoice" && (
                            <CardChapters card={board?.stockTips.find((item) => item.id === pending.card)} fallback={String(pending.card ?? "Stock Tip")} />
                        )}
                        {Array.isArray(pending?.options) && pending.options.map((option) => (
                            <Button
                                key={String(option)}
                                disabled={commandBusy}
                                onClick={() => {
                                    setChoiceOpen(false)
                                    void sendCommand({ type: "ChooseStockTipOption", option })
                                }}
                            >
                                {String(option)}
                            </Button>
                        ))}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setChoiceOpen(false)}>Cancel</Button>
                </DialogActions>
            </Dialog>
            <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)}>
                <DialogTitle>Delete this debug game?</DialogTitle>
                <DialogContent>This removes the game and all its history.</DialogContent>
                <DialogActions>
                    <Button onClick={() => setDeleteOpen(false)}>Cancel</Button>
                    <Button
                        color="error"
                        disabled={commandBusy}
                        onClick={() => {
                            if (!user || !id) return
                            setDeleteOpen(false)
                            void mutate(async () => {
                                await gameApi<void>(user, `/api/debug/games/${id}`, { method: "DELETE" })
                                navigate("/games", { replace: true })
                            }, false)
                        }}
                    >
                        Delete
                    </Button>
                </DialogActions>
            </Dialog>
            <Dialog open={endGameOpen} onClose={() => setEndGameOpen(false)}>
                <DialogTitle>End this game?</DialogTitle>
                <DialogContent>The game will be closed without a winner.</DialogContent>
                <DialogActions>
                    <Button onClick={() => setEndGameOpen(false)}>Cancel</Button>
                    <Button
                        color="error"
                        disabled={commandBusy}
                        onClick={() => {
                            setEndGameOpen(false)
                            void sendCommand({ type: "EndGame" })
                        }}
                    >
                        End game
                    </Button>
                </DialogActions>
            </Dialog>
        </Stack>
    )
}

const CardChapters = ({ card, fallback }: { card: GameBoardData["financeNews"][number] | undefined; fallback: string }) => (
    <Stack spacing={0.25}>
        {card?.chapters.map((chapter, index) => (
            <Typography
                key={index}
                variant="body2"
                sx={{ fontWeight: chapter.type === "header" ? 700 : undefined, fontStyle: chapter["font-style"] === "italic" ? "italic" : undefined }}
            >
                {chapter.text}
            </Typography>
        )) ?? <Typography variant="body2">{fallback}</Typography>}
    </Stack>
)

/** A log line; card draws also show the card text, turn starts separate turns */
const EventLine = ({ entry, text, board }: { entry: GameLogEntry; text: string; board: GameBoardData | null }) => {
    const cards = entry.type === "FinanceNewsDrawn" ? board?.financeNews : entry.type === "StockTipDrawn" ? board?.stockTips : undefined
    const card = cards?.find((item) => item.id === entry.event.card)
    const turnStart = entry.type === "TurnStarted"
    return (
        <Box sx={turnStart ? { borderTop: 1, borderColor: "divider", pt: 0.5 } : undefined}>
            <Typography
                variant="body2"
                color={entry.type === "NotImplemented" && !["BRANCH_OFFICE", "CONSTRUCTION"].includes(String(entry.event.squareType))
                    ? "warning.main"
                    : turnStart
                    ? "text.primary"
                    : "text.secondary"}
                sx={{ fontWeight: turnStart ? 600 : undefined }}
            >
                <Box component="span" sx={{ color: "text.disabled", mr: 0.75 }}>{entry.seq}</Box>
                {text}
            </Typography>
            {card && (
                <Box sx={{ pl: 2, borderLeft: 2, borderColor: "divider", ml: 0.5, my: 0.25 }}>
                    <CardChapters card={card} fallback={card.id} />
                </Box>
            )}
        </Box>
    )
}

const commandLabel = (command: string) => ({
    EndTurn: "End turn",
    BuyCar: "Buy car",
    SellCar: "Sell car",
    TakeLoan: "Take loan",
    RepayLoan: "Repay loan",
    DeclareBankruptcy: "Declare bankruptcy",
}[command] ?? command)

const decisionCommands = (
    pending: Game["state"]["pendingDecisions"][number],
    allowed: string[],
    send: (command: Record<string, unknown>) => Promise<void>,
    bid: string,
    setBid: (value: string) => void,
    openChoice: () => void,
    properties: Game["state"]["properties"],
    shares: Game["state"]["shares"],
    bonds: Game["state"]["bonds"],
    board: GameBoardData | null,
) => {
    switch (pending.type) {
        case "RaiseFunds":
            return (
                <>
                    {allowed.includes("TakeLoan") && <Button onClick={() => void send({ type: "TakeLoan" })}>Take loan</Button>}
                    {allowed.includes("SellCar") && <Button onClick={() => void send({ type: "SellCar" })}>Sell car</Button>}
                    {allowed.includes("Mortgage") && properties.filter((property) =>
                        property.owner === pending.player && !property.mortgaged &&
                        board?.titleDeeds.find((deed) => deed.square === property.square)?.mortgage?.[property.built ? "built" : "unbuilt"] != null
                    ).map((property) => (
                        <Button key={`mortgage-${property.square}`} onClick={() => void send({ type: "Mortgage", square: property.square })}>
                            Mortgage {board?.squares.find((square) => square.square === property.square)?.name ?? property.square}
                        </Button>
                    ))}
                    {allowed.includes("SellBackProperty") && properties.filter((property) =>
                        property.owner === pending.player && !property.mortgaged &&
                        board?.titleDeeds.find((deed) => deed.square === property.square)?.buyBack?.[property.built ? "built" : "unbuilt"] != null
                    ).map((property) => (
                        <Button
                            key={`sell-${property.square}`}
                            onClick={() => void send({ type: "SellBackProperty", square: property.square })}
                        >
                            Sell {board?.squares.find((square) => square.square === property.square)?.name ?? property.square}
                        </Button>
                    ))}
                    {allowed.includes("SellBackShare") && shares.filter((share) => share.owner === pending.player).map((share) => (
                        <Button
                            key={share.id}
                            onClick={() => void send({ type: "SellBackShare", share: share.id })}
                        >
                            Sell {share.id}
                        </Button>
                    ))}
                    {allowed.includes("Pay") && <Button onClick={() => void send({ type: "Pay" })}>Pay now</Button>}
                    {allowed.includes("DeclareBankruptcy") && (
                        <Button
                            color="error"
                            onClick={() => void send({ type: "DeclareBankruptcy" })}
                        >
                            Declare bankruptcy
                        </Button>
                    )}
                </>
            )
        case "BondOffer":
            return (
                <>
                    {allowed.includes("BuyBond") && bonds.filter((bond) => !bond.owner).map((bond) => (
                        <Button
                            key={bond.number}
                            onClick={() => void send({ type: "BuyBond", number: bond.number })}
                        >
                            Buy bond {bond.number}
                        </Button>
                    ))}
                    <Button onClick={() => void send({ type: "Pass" })}>Pass</Button>
                </>
            )
        case "BondAuction":
        case "AssetAuction":
            return (
                <>
                    <TextField size="small" label="Bid" type="number" value={bid} onChange={(event) => setBid(event.target.value)} />
                    <Button
                        disabled={Number(bid) < 0}
                        onClick={() => void send({ type: pending.type === "BondAuction" ? "BidBond" : "BidAsset", amount: Number(bid) })}
                    >
                        Submit bid / pass
                    </Button>
                </>
            )
        case "NewsDirection":
            return (
                <>
                    <Button onClick={() => void send({ type: "ChooseNewsDirection", forward: true })}>Forward</Button>
                    <Button onClick={() => void send({ type: "ChooseNewsDirection", forward: false })}>Backward</Button>
                </>
            )
        case "StockTipChoice":
            return <Button onClick={openChoice}>Choose card option</Button>
        default:
            return null
    }
}

const GameRoom = () => {
    const { id } = useParams()
    const { user } = useCurrentUser()
    return <GameRoomContent key={id + ":" + user?.uid} />
}

export default GameRoom

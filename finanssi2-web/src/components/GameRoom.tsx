import { useCallback, useEffect, useRef, useState } from "react"
import { useNavigate, useParams } from "react-router"
import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Stack, TextField, Typography } from "@mui/material"
import { toast } from "react-toastify"

import FlagOutlined from "@mui/icons-material/FlagOutlined"

import { GameBoard } from "./GameBoard.tsx"
import Chat from "./Chat.tsx"
import { GameWindow, type GameWindowTab } from "./GameWindow.tsx"
import { ShareCard, TitleDeedCard } from "./cards.tsx"
import { bankSalesOpen, buildAction, ConfirmDialog, type ConfirmRequest, meetingGroups, type PlayerControls, takeoverSum, twoDiceAtMost } from "./actions.tsx"
import { PlayerPanel } from "./PlayerPanel.tsx"
import { useCurrentUser } from "./CurrentUserContext.tsx"
import { useStompConnected, useStompSubscription } from "./StompContext.tsx"
import { Game, gameApi, GameApiError, GameBoardData, GameView } from "./gameApi.ts"
import { describeEvent, GameLogEntry } from "./gameEvents.ts"
import { BOARD_ASPECT_RATIO } from "./boardLayout.ts"

type GameUpdate = { version: number; events: GameLogEntry[] }

const STACKED_GAME_LAYOUT = "@media (min-width: 1536px) and (max-width: 2199.95px)"
const SHORT_WIDE_GAME_LAYOUT = "@media (min-width: 2200px) and (max-height: 1099.95px)"

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
    const [diceInput, setDiceInput] = useState("")
    const [nextNews, setNextNews] = useState("")
    const [nextTip, setNextTip] = useState("")
    const [confirmRequest, setConfirmRequest] = useState<ConfirmRequest | null>(null)
    const [buyShareOpen, setBuyShareOpen] = useState(false)
    // The group of the shareholders' meeting being called; null when the dialog is closed
    const [meetingGroup, setMeetingGroup] = useState<string | null>(null)
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
    const playerName = (uid: string) => game.state.players.find((player) => player.uid === uid)?.name ?? "a former player"
    // Only a Stock Tip drawn during the current turn stays face up on the board
    const turnStockTip = events.slice(events.findLastIndex((entry) => entry.type === "TurnStarted") + 1).findLast((entry) => entry.type === "StockTipDrawn")
    const deedOf = (square: number) => board?.titleDeeds.find((deed) => deed.square === square)
    const buildable = game.state.properties.filter((property) => {
        const deed = deedOf(property.square)
        return property.owner === actingUid && deed != null && buildAction(allowedCommands, property, deed) != null
    }).map((property) => property.square)
    const purchasable = bankSalesOpen(game, actingUid, "property")
        ? game.state.properties.filter((property) => property.owner === null).map((property) => property.square)
        : []
    const cash = actingPlayer?.cash ?? 0
    const requestBuy = (square: number) => {
        const deed = deedOf(square)
        if (!deed) return
        setConfirmRequest({
            title: `Buy ${deed.name}?`,
            body: (
                <Stack spacing={1.5} sx={{ alignItems: "center", fontSize: 13 }}>
                    <TitleDeedCard deed={deed} groupName={board?.groups.find((group) => group.id === deed.group)?.name} />
                    <span>
                        The bank sells it for €{deed.price.toLocaleString()}.
                        {deed.price > cash && ` You have €${cash.toLocaleString()}: raise €${(deed.price - cash).toLocaleString()} more first.`}
                    </span>
                </Stack>
            ),
            confirmLabel: "Buy",
            command: { type: "BuyProperty", square },
            disabled: deed.price > cash,
        })
    }
    const meetings = actingUid ? meetingGroups(game, board, actingUid).map((group) => ({ group, takeover: takeoverSum(game, board, group, actingUid) })) : []
    const meeting = meetings.find((item) => item.group === meetingGroup)
    const requestBuild = (square: number) => {
        const property = game.state.properties.find((item) => item.square === square)
        const deed = deedOf(square)
        const action = property && deed && buildAction(allowedCommands, property, deed)
        if (action) setConfirmRequest(action.request)
    }
    const contextActions = game.status === "RUNNING" && !pending && (
        <>
            {allowedCommands.includes("Resign") && (
                <Button
                    size="small"
                    color="error"
                    startIcon={<FlagOutlined />}
                    sx={{ ml: "auto" }}
                    onClick={() =>
                        setConfirmRequest({
                            title: "Resign from the game?",
                            body: "Your assets return to the bank and you are out of the game.",
                            confirmLabel: "Resign",
                            command: { type: "Resign" },
                            danger: true,
                        })}
                >
                    Resign
                </Button>
            )}
        </>
    )
    const controls: PlayerControls = {
        allowed: allowedCommands,
        busy: commandBusy,
        send: (command) => void sendCommand(command),
        confirm: setConfirmRequest,
        contextActions: contextActions || undefined,
        onBuyShare: bankSalesOpen(game, actingUid, "share") ? () => setBuyShareOpen(true) : undefined,
    }
    const gameActivityTabs: GameWindowTab[] = [
        {
            label: "Chat",
            content: ({ expanded }) => (
                <Chat
                    user={user}
                    gameId={id}
                    embedded
                    compact
                    expanded={expanded}
                    canSend={game.mode === "DEBUG" ? game.creator === user.uid : game.state.players.some((player) => player.uid === user.uid)}
                />
            ),
        },
        {
            label: "Event log",
            content: () => (
                <Stack spacing={0.5} sx={{ height: "100%", minHeight: 0, overflowY: "auto" }}>
                    {events.slice(-200).reverse().map((entry) => (
                        <EventLine key={entry.seq} entry={entry} text={describeEvent(entry, { playerName, board })} board={board} />
                    ))}
                </Stack>
            ),
        },
        ...(game.mode === "DEBUG" && debugMode
            ? [{
                label: "Debug",
                content: () => (
                    <Stack spacing={1} sx={{ height: "100%", minHeight: 0, overflowY: "auto" }}>
                        <Alert severity="info">Controlling: {actingPlayer?.name ?? "—"}</Alert>
                        <Stack
                            direction={{ xs: "column", sm: "row" }}
                            spacing={1}
                            useFlexGap
                            sx={{ alignItems: "flex-start", flexWrap: "wrap" }}
                        >
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
                        </Stack>
                    </Stack>
                ),
            }]
            : []),
    ]

    return (
        <Stack component="fieldset" disabled={commandBusy} spacing={2} sx={{ border: 0, m: 0, px: 0, flex: 1, minHeight: 0, py: 2, overflowY: "auto" }}>
            {error && <Alert severity="warning">{error}</Alert>}
            <Grid container spacing={2} sx={{ alignItems: "stretch" }}>
                <Grid
                    size={{ xs: 12, xl: 8 }}
                    sx={{
                        alignSelf: "start",
                        [STACKED_GAME_LAYOUT]: { flexBasis: "100%", maxWidth: "100%" },
                        [SHORT_WIDE_GAME_LAYOUT]: { flexBasis: "100%", maxWidth: "100%" },
                    }}
                >
                    <GameBoard
                        game={game}
                        board={board}
                        buildable={buildable}
                        onBuild={requestBuild}
                        purchasable={purchasable}
                        onBuy={requestBuy}
                        meetings={meetings}
                        onMeeting={(group) => {
                            setMeetingGroup(group)
                            setMeetingFee(20000)
                        }}
                        turnStockTip={turnStockTip ? String(turnStockTip.event.card) : null}
                    />
                </Grid>
                <Grid
                    size={{ xs: 12, xl: 4 }}
                    sx={{
                        display: "flex",
                        minHeight: 0,
                        alignSelf: { xl: "start" },
                        aspectRatio: { xl: BOARD_ASPECT_RATIO / 2 },
                        [STACKED_GAME_LAYOUT]: { flexBasis: "100%", maxWidth: "100%", alignSelf: "stretch", aspectRatio: "auto" },
                        [SHORT_WIDE_GAME_LAYOUT]: { flexBasis: "100%", maxWidth: "100%", alignSelf: "stretch", aspectRatio: "auto" },
                    }}
                >
                    <Stack
                        spacing={1.5}
                        sx={{
                            width: "100%",
                            minHeight: 0,
                            height: { xs: "auto", xl: "100%" },
                            [STACKED_GAME_LAYOUT]: { height: "auto" },
                            [SHORT_WIDE_GAME_LAYOUT]: { height: "auto" },
                        }}
                    >
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
                                controls={game.status === "RUNNING" && player.uid === actingUid ? controls : undefined}
                            />
                        ))}
                        {game.state.finished && (
                            <Alert severity="success">
                                {game.state.winner
                                    ? `${game.state.players.find((p) => p.uid === game.state.winner)?.name} wins.`
                                    : "The game was closed without a winner."}
                            </Alert>
                        )}
                        <GameWindow
                            fill
                            tabs={gameActivityTabs}
                        />
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
            <ConfirmDialog
                request={confirmRequest}
                busy={commandBusy}
                onClose={() => setConfirmRequest(null)}
                onConfirm={(command) => void sendCommand(command)}
            />
            <Dialog open={buyShareOpen} onClose={() => setBuyShareOpen(false)} maxWidth="lg">
                <DialogTitle>Buy a share from the bank</DialogTitle>
                <DialogContent sx={{ fontSize: 13 }}>
                    <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: "wrap", py: 1 }}>
                        {game.state.shares.filter((share) => share.owner === null).map((owned) => {
                            const share = board?.shares.find((item) => item.id === owned.id)
                            return share && (
                                <Stack key={share.id} spacing={0.75}>
                                    <ShareCard share={share} board={board} />
                                    <Button
                                        variant="contained"
                                        size="small"
                                        // Shown for every share for sale; one the player can't afford yet can't be bought
                                        disabled={commandBusy || share.value > cash}
                                        onClick={() => {
                                            setBuyShareOpen(false)
                                            void sendCommand({ type: "BuyShare", share: share.id })
                                        }}
                                    >
                                        Buy €{share.value.toLocaleString()}
                                    </Button>
                                </Stack>
                            )
                        })}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setBuyShareOpen(false)}>Cancel</Button>
                </DialogActions>
            </Dialog>
            <Dialog open={meeting != null} onClose={() => setMeetingGroup(null)} maxWidth="xs" fullWidth>
                <DialogTitle>Shareholders' meeting: {board?.groups.find((group) => group.id === meetingGroup)?.name}</DialogTitle>
                {meeting && (
                    <DialogContent>
                        <Stack spacing={2} sx={{ pt: 1 }}>
                            <Typography variant="body2">
                                Taking over the other players' properties and shares costs €{meeting.takeover.toLocaleString()}, plus the brokerage fee. You
                                have €{cash.toLocaleString()}.
                            </Typography>
                            <TextField
                                select
                                size="small"
                                label="Brokerage fee"
                                value={meetingFee}
                                onChange={(event) => setMeetingFee(Number(event.target.value))}
                                slotProps={{ select: { native: true } }}
                            >
                                {[20000, 30000, 40000, 50000, 60000, 70000, 80000, 90000, 100000, 110000, 120000].map((fee) => (
                                    <option key={fee} value={fee} disabled={meeting.takeover + fee > cash}>
                                        €{fee.toLocaleString()}:{" "}
                                        {Math.round(twoDiceAtMost(fee / 10000) * 100)}% chance{meeting.takeover + fee > cash ? " (not enough cash)" : ""}
                                    </option>
                                ))}
                            </TextField>
                            <Typography variant="body2" color={meeting.takeover + meetingFee > cash ? "error" : "text.secondary"}>
                                {meeting.takeover + meetingFee > cash
                                    ? `You need €${(meeting.takeover + meetingFee).toLocaleString()}: raise €${
                                        (meeting.takeover + meetingFee - cash).toLocaleString()
                                    } more first, e.g. by mortgaging or selling.`
                                    : `The takeover succeeds on a dice total of ${meetingFee / 10000} or less.`}
                            </Typography>
                        </Stack>
                    </DialogContent>
                )}
                <DialogActions>
                    <Button onClick={() => setMeetingGroup(null)}>Cancel</Button>
                    <Button
                        variant="contained"
                        disabled={!meeting || meeting.takeover + meetingFee > cash || commandBusy}
                        onClick={() => {
                            setMeetingGroup(null)
                            void sendCommand({ type: "CallShareholdersMeeting", group: meetingGroup, brokerageFee: meetingFee })
                        }}
                    >
                        Call meeting
                    </Button>
                </DialogActions>
            </Dialog>
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

import { useCallback, useEffect, useRef, useState } from "react"
import { useNavigate } from "react-router"
import {
    Alert,
    Avatar,
    Box,
    Button,
    Card,
    CardActions,
    CardContent,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    InputLabel,
    MenuItem,
    Select,
    Stack,
    Typography,
} from "@mui/material"
import AddIcon from "@mui/icons-material/Add"
import { toast } from "react-toastify"

import { useCurrentUser } from "./CurrentUserContext.tsx"
import { useStompConnected, useStompSubscription } from "./StompContext.tsx"
import { Game, gameApi, GameApiError } from "./gameApi.ts"

const Games = () => {
    const { user, debugMode, refreshCapabilities, clearDebugAccess } = useCurrentUser()
    const userRef = useRef(user)
    userRef.current = user
    const requestRef = useRef(0)
    const busyRef = useRef(false)
    const connected = useStompConnected()
    const navigate = useNavigate()
    const [games, setGames] = useState<Game[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [settingsGame, setSettingsGame] = useState<Game | null>(null)
    const [loanLimit, setLoanLimit] = useState("UNLIMITED")
    const [minimumBid, setMinimumBid] = useState("NONE")
    const [debugCreateOpen, setDebugCreateOpen] = useState(false)
    const [playerCount, setPlayerCount] = useState(2)
    const [busyGame, setBusyGame] = useState<string | null>(null)
    // Creator controls that remove a game from play: ending a running game, or deleting a debug game
    const [closing, setClosing] = useState<{ game: Game; action: "end" | "delete" } | null>(null)

    const refresh = useCallback(() => {
        if (!user) return Promise.resolve()
        const request = ++requestRef.current
        return gameApi<Game[]>(user, "/api/games")
            .then((items) => {
                if (userRef.current !== user || request !== requestRef.current) return
                setGames(items)
                setError(null)
            })
            .catch((reason: unknown) => {
                if (userRef.current === user && request === requestRef.current) setError(reason instanceof Error ? reason.message : "Unable to load games")
            })
            .finally(() => {
                if (userRef.current === user && request === requestRef.current) setLoading(false)
            })
    }, [user])

    useEffect(() => {
        void refresh()
    }, [refresh])

    useEffect(() => {
        if (connected) {
            void refresh()
            void refreshCapabilities()
        }
    }, [connected, refresh, refreshCapabilities])

    useEffect(() => {
        setGames([])
        setLoading(true)
        setSettingsGame(null)
        setDebugCreateOpen(false)
    }, [user])

    useStompSubscription("/topic/games", () => void refresh())

    const run = async (id: string | null, action: () => Promise<unknown>, then?: (value: unknown) => void) => {
        if (busyRef.current) return
        busyRef.current = true
        setBusyGame(id ?? "new")
        try {
            const result = await action()
            if (userRef.current !== user) return
            then?.(result)
            await refresh()
        } catch (reason) {
            if (reason instanceof GameApiError && reason.status === 403) {
                clearDebugAccess()
                setDebugCreateOpen(false)
            }
            toast(reason instanceof Error ? reason.message : "The request failed", { type: "error" })
        } finally {
            busyRef.current = false
            setBusyGame(null)
        }
    }

    const createGame = () => {
        if (!user) return
        void run(null, () => gameApi<Game>(user, "/api/games", { method: "POST" }))
    }

    const createDebug = () => {
        if (!user) return
        void run(null, () =>
            gameApi<Game>(user, "/api/debug/games", {
                method: "POST",
                body: JSON.stringify({ playerCount, settings: { loanLimit, compulsorySaleMinimumBid: minimumBid } }),
            })).then(() => setDebugCreateOpen(false))
    }

    const joinGame = (game: Game) => {
        if (!user) return
        void run(game.id, () => gameApi(user, `/api/games/${game.id}/join`, { method: "POST" }), () => navigate(`/games/${game.id}`))
    }

    const leaveGame = (game: Game) => {
        if (!user) return
        void run(game.id, () => gameApi(user, `/api/games/${game.id}/leave`, { method: "POST" }))
    }

    const startGame = (game: Game) => {
        if (!user) return
        void run(game.id, () => gameApi(user, `/api/games/${game.id}/start`, { method: "POST" }), () => navigate(`/games/${game.id}`))
    }

    const closeGame = () => {
        if (!user || !closing) return
        const { game, action } = closing
        setClosing(null)
        void run(
            game.id,
            () =>
                action === "delete"
                    ? gameApi<void>(user, `/api/debug/games/${game.id}`, { method: "DELETE" })
                    : gameApi(user, `/api/games/${game.id}/commands`, { method: "POST", body: JSON.stringify({ type: "EndGame" }) }),
        )
    }

    const openSettings = (game: Game) => {
        setLoanLimit(game.state.settings.loanLimit)
        setMinimumBid(game.state.settings.compulsorySaleMinimumBid)
        setSettingsGame(game)
    }

    const saveSettings = () => {
        if (!user || !settingsGame) return
        const game = settingsGame
        void run(game.id, () =>
            gameApi(user, `/api/games/${game.id}/settings`, {
                method: "PUT",
                body: JSON.stringify({ loanLimit, compulsorySaleMinimumBid: minimumBid }),
            })).then(() => setSettingsGame(null))
    }

    if (!user) return <Alert severity="info" sx={{ mt: 2 }}>Sign in to create or join a game.</Alert>

    return (
        <Stack spacing={2} sx={{ flex: 1, minHeight: 0, py: 2 }}>
            <Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between" }}>
                <Typography variant="h5">Games</Typography>
                <Stack direction="row" spacing={1}>
                    {debugMode && (
                        <Button
                            disabled={busyGame !== null}
                            onClick={() => {
                                setLoanLimit("UNLIMITED")
                                setMinimumBid("NONE")
                                setDebugCreateOpen(true)
                            }}
                        >
                            Create debug game
                        </Button>
                    )}
                    <Button variant="contained" startIcon={<AddIcon />} disabled={busyGame !== null} onClick={createGame}>
                        Create game
                    </Button>
                </Stack>
            </Stack>
            {error && <Alert severity="error" action={<Button color="inherit" onClick={() => void refresh()}>Retry</Button>}>{error}</Alert>}
            {loading
                ? (
                    <Box sx={{ display: "flex", justifyContent: "center", py: 5 }}>
                        <CircularProgress />
                    </Box>
                )
                : games.length === 0
                ? <Alert severity="info">No games yet. Create one to start playing.</Alert>
                : (
                    <Stack spacing={1.5}>
                        {games.filter((game) => game.mode !== "DEBUG" || debugMode).map((game) => {
                            const member = game.state.players.some((player) => player.uid === user.uid)
                            const creator = game.creator === user.uid
                            return (
                                <Card key={game.id} variant="outlined">
                                    <CardContent>
                                        <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ justifyContent: "space-between" }}>
                                            <Box>
                                                <Typography variant="h6">{game.mode === "DEBUG" ? "Debug game" : "Game"} {game.id.slice(-6)}</Typography>
                                                <Typography variant="body2" color="text.secondary">
                                                    {game.status === "LOBBY" ? "Waiting for players" : game.status === "RUNNING" ? "In progress" : "Finished"}
                                                    {creator
                                                        ? " · You are the creator"
                                                        : ` · Created by ${
                                                            game.state.players.find((player) => player.uid === game.creator)?.name ?? "another player"
                                                        }`}
                                                </Typography>
                                            </Box>
                                            <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                                                {game.state.players.map((player) => (
                                                    <Avatar
                                                        key={player.uid}
                                                        src={player.photoUrl ?? undefined}
                                                        alt={player.name}
                                                        sx={{ width: 32, height: 32 }}
                                                    >
                                                        {player.name.slice(0, 1)}
                                                    </Avatar>
                                                ))}
                                                <Typography variant="caption">
                                                    {game.state.players.length} player{game.state.players.length === 1 ? "" : "s"}
                                                </Typography>
                                            </Stack>
                                        </Stack>
                                    </CardContent>
                                    <CardActions>
                                        {game.mode !== "DEBUG" && game.status === "LOBBY" && !member && (
                                            <Button
                                                disabled={busyGame !== null}
                                                onClick={() => joinGame(game)}
                                            >
                                                Join
                                            </Button>
                                        )}
                                        {game.status === "LOBBY" && member && creator && (
                                            <>
                                                <Button disabled={busyGame !== null} onClick={() => openSettings(game)}>Settings</Button>
                                                <Button disabled={busyGame !== null || game.state.players.length < 2} onClick={() => startGame(game)}>
                                                    Start
                                                </Button>
                                                {game.mode !== "DEBUG" && <Button disabled={busyGame !== null} onClick={() => leaveGame(game)}>Leave</Button>}
                                            </>
                                        )}
                                        {game.mode !== "DEBUG" && game.status === "LOBBY" && member && !creator && (
                                            <Button
                                                disabled={busyGame !== null}
                                                onClick={() => leaveGame(game)}
                                            >
                                                Leave
                                            </Button>
                                        )}
                                        {member && (game.status !== "LOBBY" || game.mode === "DEBUG") && (
                                            <Button onClick={() => navigate(`/games/${game.id}`)}>
                                                {game.status === "FINISHED" ? "Results" : "Open game"}
                                            </Button>
                                        )}
                                        {creator && game.mode !== "DEBUG" && game.status === "RUNNING" && (
                                            <Button
                                                color="error"
                                                sx={{ ml: "auto" }}
                                                disabled={busyGame !== null}
                                                onClick={() => setClosing({ game, action: "end" })}
                                            >
                                                End game
                                            </Button>
                                        )}
                                        {creator && game.mode === "DEBUG" && (
                                            <Button
                                                color="error"
                                                sx={{ ml: "auto" }}
                                                disabled={busyGame !== null}
                                                onClick={() => setClosing({ game, action: "delete" })}
                                            >
                                                Delete
                                            </Button>
                                        )}
                                    </CardActions>
                                </Card>
                            )
                        })}
                    </Stack>
                )}
            <Dialog open={debugMode && debugCreateOpen} onClose={() => setDebugCreateOpen(false)} fullWidth maxWidth="xs">
                <DialogTitle>Create debug game</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{ pt: 1 }}>
                        <FormControl fullWidth>
                            <InputLabel id="debug-seat-label">Seats</InputLabel>
                            <Select
                                labelId="debug-seat-label"
                                label="Seats"
                                value={playerCount}
                                onChange={(event) => setPlayerCount(Number(event.target.value))}
                            >
                                {[2, 3, 4, 5, 6].map((count) => <MenuItem key={count} value={count}>{count}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <FormControl fullWidth>
                            <InputLabel id="debug-loan-label">Bank loans</InputLabel>
                            <Select labelId="debug-loan-label" label="Bank loans" value={loanLimit} onChange={(event) => setLoanLimit(event.target.value)}>
                                <MenuItem value="UNLIMITED">Unlimited (recommended)</MenuItem>
                                <MenuItem value="OFFICIAL">Official limit: 6 total</MenuItem>
                            </Select>
                        </FormControl>
                        <FormControl fullWidth>
                            <InputLabel id="debug-bid-label">Forced sale minimum bid</InputLabel>
                            <Select
                                labelId="debug-bid-label"
                                label="Forced sale minimum bid"
                                value={minimumBid}
                                onChange={(event) => setMinimumBid(event.target.value)}
                            >
                                <MenuItem value="NONE">No minimum</MenuItem>
                                <MenuItem value="HALF_NOMINAL_PRICE">Half nominal price</MenuItem>
                            </Select>
                        </FormControl>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDebugCreateOpen(false)}>Cancel</Button>
                    <Button disabled={busyGame !== null} onClick={createDebug}>Create</Button>
                </DialogActions>
            </Dialog>
            <Dialog open={closing !== null} onClose={() => setClosing(null)}>
                <DialogTitle>{closing?.action === "delete" ? "Delete this debug game?" : "End this game?"}</DialogTitle>
                <DialogContent>
                    {closing?.action === "delete" ? "This removes the game and all its history." : "The game will be closed without a winner."}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setClosing(null)}>Cancel</Button>
                    <Button variant="contained" color="error" disabled={busyGame !== null} onClick={closeGame}>
                        {closing?.action === "delete" ? "Delete" : "End game"}
                    </Button>
                </DialogActions>
            </Dialog>
            <Dialog open={settingsGame !== null} onClose={() => setSettingsGame(null)} fullWidth maxWidth="xs">
                <DialogTitle>Game settings</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{ pt: 1 }}>
                        <FormControl fullWidth>
                            <InputLabel id="loan-limit-label">Bank loans</InputLabel>
                            <Select labelId="loan-limit-label" label="Bank loans" value={loanLimit} onChange={(event) => setLoanLimit(event.target.value)}>
                                <MenuItem value="UNLIMITED">Unlimited (recommended)</MenuItem>
                                <MenuItem value="OFFICIAL">Official limit: 6 total</MenuItem>
                            </Select>
                        </FormControl>
                        <FormControl fullWidth>
                            <InputLabel id="sale-minimum-label">Forced sale minimum bid</InputLabel>
                            <Select
                                labelId="sale-minimum-label"
                                label="Forced sale minimum bid"
                                value={minimumBid}
                                onChange={(event) => setMinimumBid(event.target.value)}
                            >
                                <MenuItem value="NONE">No minimum (official rule)</MenuItem>
                                <MenuItem value="HALF_NOMINAL_PRICE">Half nominal price</MenuItem>
                            </Select>
                        </FormControl>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setSettingsGame(null)}>Cancel</Button>
                    <Button variant="contained" disabled={busyGame !== null} onClick={saveSettings}>Save</Button>
                </DialogActions>
            </Dialog>
        </Stack>
    )
}

export default Games

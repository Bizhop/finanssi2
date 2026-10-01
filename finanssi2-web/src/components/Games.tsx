import { useCallback, useEffect, useState } from "react"
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
import { useStompSubscription } from "./StompContext.tsx"
import { Game, gameApi } from "./gameApi.ts"

const Games = () => {
    const { user } = useCurrentUser()
    const navigate = useNavigate()
    const [games, setGames] = useState<Game[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [settingsGame, setSettingsGame] = useState<Game | null>(null)
    const [loanLimit, setLoanLimit] = useState("UNLIMITED")
    const [minimumBid, setMinimumBid] = useState("NONE")
    const [busyGame, setBusyGame] = useState<string | null>(null)

    const refresh = useCallback(() => {
        if (!user) return Promise.resolve()
        return gameApi<Game[]>(user, "/api/games")
            .then((items) => {
                setGames(items)
                setError(null)
            })
            .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Unable to load games"))
            .finally(() => setLoading(false))
    }, [user])

    useEffect(() => {
        void refresh()
    }, [refresh])

    useStompSubscription("/topic/games", () => void refresh())

    const run = async (id: string | null, action: () => Promise<unknown>, then?: (value: unknown) => void) => {
        setBusyGame(id ?? "new")
        try {
            const result = await action()
            then?.(result)
            await refresh()
        } catch (reason) {
            toast(reason instanceof Error ? reason.message : "The request failed", { type: "error" })
        } finally {
            setBusyGame(null)
        }
    }

    const createGame = () => {
        if (!user) return
        void run(null, () => gameApi<Game>(user, "/api/games", { method: "POST" }))
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

    const openSettings = (game: Game) => {
        setLoanLimit(game.state.settings.loanLimit)
        setMinimumBid(game.state.settings.compulsorySaleMinimumBid)
        setSettingsGame(game)
    }

    const saveSettings = () => {
        if (!user || !settingsGame) return
        const game = settingsGame
        void run(game.id, () => gameApi(user, `/api/games/${game.id}/settings`, {
            method: "PUT",
            body: JSON.stringify({ loanLimit, compulsorySaleMinimumBid: minimumBid }),
        })).then(() => setSettingsGame(null))
    }

    if (!user) return <Alert severity="info" sx={{ mt: 2 }}>Sign in to create or join a game.</Alert>

    return (
        <Stack spacing={2} sx={{ flex: 1, minHeight: 0, py: 2 }}>
            <Stack direction="row" alignItems="center" justifyContent="space-between">
                <Typography variant="h5">Games</Typography>
                <Button variant="contained" startIcon={<AddIcon />} disabled={busyGame !== null} onClick={createGame}>
                    Create game
                </Button>
            </Stack>
            {error && <Alert severity="error" action={<Button color="inherit" onClick={() => void refresh()}>Retry</Button>}>{error}</Alert>}
            {loading
                ? <Box sx={{ display: "flex", justifyContent: "center", py: 5 }}><CircularProgress /></Box>
                : games.length === 0
                ? <Alert severity="info">No games yet. Create one to start playing.</Alert>
                : <Stack spacing={1.5}>
                    {games.map((game) => {
                        const member = game.state.players.some((player) => player.uid === user.uid)
                        const creator = game.creator === user.uid
                        return (
                            <Card key={game.id} variant="outlined">
                                <CardContent>
                                    <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={1}>
                                        <Box>
                                            <Typography variant="h6">Game {game.id.slice(-6)}</Typography>
                                            <Typography variant="body2" color="text.secondary">
                                                {game.status === "LOBBY" ? "Waiting for players" : game.status === "RUNNING" ? "In progress" : "Finished"}
                                                {creator ? " · You are the creator" : ` · Created by ${game.state.players.find((player) => player.uid === game.creator)?.name ?? "another player"}`}
                                            </Typography>
                                        </Box>
                                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                                            {game.state.players.map((player) => (
                                                <Avatar key={player.uid} src={player.photoUrl ?? undefined} alt={player.name} sx={{ width: 32, height: 32 }}>
                                                    {player.name.slice(0, 1)}
                                                </Avatar>
                                            ))}
                                            <Typography variant="caption">{game.state.players.length} player{game.state.players.length === 1 ? "" : "s"}</Typography>
                                        </Stack>
                                    </Stack>
                                </CardContent>
                                <CardActions>
                                    {game.status === "LOBBY" && !member && <Button disabled={busyGame !== null} onClick={() => joinGame(game)}>Join</Button>}
                                    {game.status === "LOBBY" && member && creator && <>
                                        <Button disabled={busyGame !== null} onClick={() => openSettings(game)}>Settings</Button>
                                        <Button disabled={busyGame !== null || game.state.players.length < 2} onClick={() => startGame(game)}>Start</Button>
                                        <Button disabled={busyGame !== null} onClick={() => leaveGame(game)}>Leave</Button>
                                    </>}
                                    {game.status === "LOBBY" && member && !creator && <Button disabled={busyGame !== null} onClick={() => leaveGame(game)}>Leave</Button>}
                                    {member && game.status !== "LOBBY" && <Button onClick={() => navigate(`/games/${game.id}`)}>
                                        {game.status === "FINISHED" ? "Results" : "Open game"}
                                    </Button>}
                                </CardActions>
                            </Card>
                        )
                    })}
                </Stack>}
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
                            <Select labelId="sale-minimum-label" label="Forced sale minimum bid" value={minimumBid} onChange={(event) => setMinimumBid(event.target.value)}>
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

import { useState } from "react"
import {
    Alert,
    Box,
    Button,
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

import { useCurrentUser } from "../auth/CurrentUserContext.tsx"
import { usePublicProfiles } from "../auth/PublicProfiles.tsx"
import type { Game } from "../api/gameApi.ts"
import { CloseGameDialog } from "./games/CloseGameDialog.tsx"
import { GameCard } from "./games/GameCard.tsx"
import { RuleSettings } from "./games/RuleSettings.tsx"
import { useGames } from "./games/useGames.ts"

const DEFAULT_RULES = { loanLimit: "UNLIMITED", compulsorySaleMinimumBid: "HALF_NOMINAL_PRICE", shareholdersMeeting: "ALL_ASSETS_BOUGHT" }

export const Games = () => {
    const { user, profile, debugMode } = useCurrentUser()
    const [debugCreateOpen, setDebugCreateOpen] = useState(false)
    const games = useGames(() => setDebugCreateOpen(false))
    const profiles = usePublicProfiles(games.games.flatMap((game) => game.state.players.map((player) => player.playerId)))
    const { busy } = games
    const [settingsGame, setSettingsGame] = useState<Game | null>(null)
    const [loanLimit, setLoanLimit] = useState(DEFAULT_RULES.loanLimit)
    const [minimumBid, setMinimumBid] = useState(DEFAULT_RULES.compulsorySaleMinimumBid)
    const [meetingRule, setMeetingRule] = useState(DEFAULT_RULES.shareholdersMeeting)
    const [playerCount, setPlayerCount] = useState(2)
    // Creator controls that remove a game from play: ending a running game, or deleting a debug game
    const [closing, setClosing] = useState<{ game: Game; action: "end" | "delete" } | null>(null)

    const rules = () => ({ loanLimit, compulsorySaleMinimumBid: minimumBid, shareholdersMeeting: meetingRule })

    const openDebugCreate = () => {
        setLoanLimit(DEFAULT_RULES.loanLimit)
        setMinimumBid(DEFAULT_RULES.compulsorySaleMinimumBid)
        setMeetingRule(DEFAULT_RULES.shareholdersMeeting)
        setDebugCreateOpen(true)
    }

    const createDebug = () => {
        void games.createDebugGame(playerCount, rules()).then((ok) => {
            if (ok) setDebugCreateOpen(false)
        })
    }

    const openSettings = (game: Game) => {
        setLoanLimit(game.state.settings.loanLimit)
        setMinimumBid(game.state.settings.compulsorySaleMinimumBid)
        setMeetingRule(game.state.settings.shareholdersMeeting ?? DEFAULT_RULES.shareholdersMeeting)
        setSettingsGame(game)
    }

    const saveSettings = () => {
        if (!settingsGame) return
        void games.saveSettings(settingsGame, rules()).then((ok) => {
            if (ok) setSettingsGame(null)
        })
    }

    const closeGame = () => {
        if (!closing) return
        const { game, action } = closing
        setClosing(null)
        void games.closeGame(game, action)
    }

    if (!user) return <Alert severity="info" sx={{ mt: 2 }}>Sign in to create or join a game.</Alert>

    return (
        <Stack spacing={2} sx={{ flex: 1, minHeight: 0, py: 2 }}>
            <Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between" }}>
                <Typography variant="h5">Games</Typography>
                <Stack direction="row" spacing={1}>
                    {debugMode && <Button disabled={busy} onClick={openDebugCreate}>Create debug game</Button>}
                    <Button variant="contained" startIcon={<AddIcon />} disabled={busy} onClick={() => void games.createGame()}>
                        Create game
                    </Button>
                </Stack>
            </Stack>
            {games.error && <Alert severity="error" action={<Button color="inherit" onClick={() => void games.refresh()}>Retry</Button>}>{games.error}</Alert>}
            {games.loading
                ? (
                    <Box sx={{ display: "flex", justifyContent: "center", py: 5 }}>
                        <CircularProgress />
                    </Box>
                )
                : games.games.length === 0
                ? <Alert severity="info">No games yet. Create one to start playing.</Alert>
                : (
                    <Stack spacing={1.5}>
                        {games.games.filter((game) => game.mode !== "DEBUG" || debugMode).map((game) => (
                            <GameCard
                                key={game.id}
                                game={game}
                                viewerId={profile?.id}
                                profiles={profiles}
                                busy={busy}
                                onJoin={() => void games.joinGame(game)}
                                onLeave={() => void games.leaveGame(game)}
                                onStart={() => void games.startGame(game)}
                                onSettings={() => openSettings(game)}
                                onOpen={games.open(game)}
                                onClose={(action) => setClosing({ game, action })}
                            />
                        ))}
                    </Stack>
                )}
            <Dialog open={debugMode && debugCreateOpen} onClose={() => setDebugCreateOpen(false)} fullWidth maxWidth="sm">
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
                        <RuleSettings
                            loanLimit={loanLimit}
                            setLoanLimit={setLoanLimit}
                            minimumBid={minimumBid}
                            setMinimumBid={setMinimumBid}
                            meetingRule={meetingRule}
                            setMeetingRule={setMeetingRule}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDebugCreateOpen(false)}>Cancel</Button>
                    <Button disabled={busy} onClick={createDebug}>Create</Button>
                </DialogActions>
            </Dialog>
            <CloseGameDialog action={closing?.action ?? null} busy={busy} onCancel={() => setClosing(null)} onConfirm={closeGame} />
            <Dialog open={settingsGame !== null} onClose={() => setSettingsGame(null)} fullWidth maxWidth="sm">
                <DialogTitle>Game settings</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{ pt: 1 }}>
                        <RuleSettings
                            loanLimit={loanLimit}
                            setLoanLimit={setLoanLimit}
                            minimumBid={minimumBid}
                            setMinimumBid={setMinimumBid}
                            meetingRule={meetingRule}
                            setMeetingRule={setMeetingRule}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setSettingsGame(null)}>Cancel</Button>
                    <Button variant="contained" disabled={busy} onClick={saveSettings}>Save</Button>
                </DialogActions>
            </Dialog>
        </Stack>
    )
}

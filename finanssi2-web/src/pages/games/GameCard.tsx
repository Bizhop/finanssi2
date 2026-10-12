import { Avatar, Box, Button, Card, CardActions, CardContent, Stack, Typography } from "@mui/material"

import { avatarSrc, type Game } from "../../api/gameApi.ts"
import type { PublicProfile } from "../../auth/PublicProfiles.tsx"

/** One game in the list, with the controls its status and the viewer's role allow */
export const GameCard = ({ game, viewerId, profiles, busy, onJoin, onLeave, onStart, onSettings, onOpen, onClose }: {
    game: Game
    viewerId: string | undefined
    profiles: Record<string, PublicProfile>
    /** Some request is running; every control waits for it */
    busy: boolean
    onJoin: () => void
    onLeave: () => void
    onStart: () => void
    onSettings: () => void
    onOpen: () => void
    onClose: (action: "end" | "delete") => void
}) => {
    const member = game.state.players.some((player) => player.playerId === viewerId)
    const creator = game.creator === viewerId
    return (
        <Card variant="outlined">
            <CardContent>
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ justifyContent: "space-between" }}>
                    <Box>
                        <Typography variant="h6">{game.mode === "DEBUG" ? "Debug game" : "Game"} {game.id.slice(-6)}</Typography>
                        <Typography variant="body2" color="text.secondary">
                            {game.status === "LOBBY" ? "Waiting for players" : game.status === "RUNNING" ? "In progress" : "Finished"}
                            {creator ? " · You are the creator" : ` · Created by ${profiles[game.creator]?.displayName ?? "another player"}`}
                        </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                        {game.state.players.map((player) => (
                            <Avatar
                                key={player.playerId}
                                src={avatarSrc(profiles[player.playerId]?.avatar)}
                                alt={profiles[player.playerId]?.displayName ?? "Player"}
                                sx={{ width: 32, height: 32 }}
                            >
                                {(profiles[player.playerId]?.displayName ?? "?").slice(0, 1)}
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
                        disabled={busy}
                        onClick={() => onJoin()}
                    >
                        Join
                    </Button>
                )}
                {game.status === "LOBBY" && member && creator && (
                    <>
                        <Button disabled={busy} onClick={() => onSettings()}>Settings</Button>
                        <Button disabled={busy || game.state.players.length < 2} onClick={() => onStart()}>
                            Start
                        </Button>
                        {game.mode !== "DEBUG" && <Button disabled={busy} onClick={() => onLeave()}>Leave</Button>}
                    </>
                )}
                {game.mode !== "DEBUG" && game.status === "LOBBY" && member && !creator && (
                    <Button
                        disabled={busy}
                        onClick={() => onLeave()}
                    >
                        Leave
                    </Button>
                )}
                {member && (game.status !== "LOBBY" || game.mode === "DEBUG") && (
                    <Button onClick={() => onOpen()}>
                        {game.status === "FINISHED" ? "Results" : "Open game"}
                    </Button>
                )}
                {/* Creator controls that close the game sit apart on the right */}
                <Box sx={{ flex: 1 }} />
                {creator && game.mode !== "DEBUG" && game.status === "RUNNING" && (
                    <Button
                        color="error"
                        disabled={busy}
                        onClick={() => onClose("end")}
                    >
                        End game
                    </Button>
                )}
                {creator && game.mode === "DEBUG" && (
                    <Button
                        color="error"
                        disabled={busy}
                        onClick={() => onClose("delete")}
                    >
                        Delete
                    </Button>
                )}
            </CardActions>
        </Card>
    )
}

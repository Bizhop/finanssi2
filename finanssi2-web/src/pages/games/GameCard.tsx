import { useTranslation } from "react-i18next"
import { Avatar, Box, Button, Card, CardActions, CardContent, Stack, Typography } from "@mui/material"

import { avatarSrc, type Game, type PublicProfile } from "../../api/gameApi.ts"

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
    const { t } = useTranslation()
    const member = game.state.players.some((player) => player.playerId === viewerId)
    const creator = game.creator === viewerId
    return (
        <Card variant="outlined">
            <CardContent>
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ justifyContent: "space-between" }}>
                    <Box>
                        <Typography variant="h6">{game.mode === "DEBUG" ? t("gameCard.debugGame") : t("gameCard.game")} {game.id.slice(-6)}</Typography>
                        <Typography variant="body2" color="text.secondary">
                            {t(`gameCard.status.${game.status}`)}
                            {creator
                                ? ` · ${t("gameCard.youAreCreator")}`
                                : ` · ${t("gameCard.createdBy", { name: profiles[game.creator]?.displayName ?? t("gameCard.anotherPlayer") })}`}
                        </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                        {game.state.players.map((player) => (
                            <Avatar
                                key={player.playerId}
                                src={avatarSrc(profiles[player.playerId]?.avatar)}
                                alt={profiles[player.playerId]?.displayName ?? t("common.player")}
                                sx={{ width: 32, height: 32 }}
                            >
                                {(profiles[player.playerId]?.displayName ?? "?").slice(0, 1)}
                            </Avatar>
                        ))}
                        <Typography variant="caption">
                            {t("gameCard.players", { count: game.state.players.length })}
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
                        {t("gameCard.join")}
                    </Button>
                )}
                {game.status === "LOBBY" && member && creator && (
                    <>
                        <Button disabled={busy} onClick={() => onSettings()}>{t("gameCard.settings")}</Button>
                        <Button disabled={busy || game.state.players.length < 2} onClick={() => onStart()}>
                            {t("gameCard.start")}
                        </Button>
                        {game.mode !== "DEBUG" && <Button disabled={busy} onClick={() => onLeave()}>{t("gameCard.leave")}</Button>}
                    </>
                )}
                {game.mode !== "DEBUG" && game.status === "LOBBY" && member && !creator && (
                    <Button
                        disabled={busy}
                        onClick={() => onLeave()}
                    >
                        {t("gameCard.leave")}
                    </Button>
                )}
                {member && (game.status !== "LOBBY" || game.mode === "DEBUG") && (
                    <Button onClick={() => onOpen()}>
                        {game.status === "FINISHED" ? t("gameCard.results") : t("gameCard.open")}
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
                        {t("gameCard.end")}
                    </Button>
                )}
                {creator && game.mode === "DEBUG" && (
                    <Button
                        color="error"
                        disabled={busy}
                        onClick={() => onClose("delete")}
                    >
                        {t("gameCard.delete")}
                    </Button>
                )}
            </CardActions>
        </Card>
    )
}

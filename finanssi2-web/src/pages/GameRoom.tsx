import { useRef, useState } from "react"
import { useParams } from "react-router"
import { Alert, Box, Button, CircularProgress, Grid, Stack } from "@mui/material"

import FlagOutlined from "@mui/icons-material/FlagOutlined"

import { GameBoard } from "../game/components/GameBoard.tsx"
import { Chat } from "../chat/Chat.tsx"
import { GameWindow, type GameWindowTab } from "../ui/GameWindow.tsx"
import { TitleDeedCard } from "../game/components/Cards.tsx"
import { buildAction, type PlayerControls } from "../game/rules/assetActions.ts"
import { ConfirmDialog, type ConfirmRequest } from "../ui/ConfirmDialog.tsx"
import { bankSalesOpen, meetingGroups, takeoverSum } from "../game/rules/gameRules.ts"
import { PlayerPanel } from "../game/components/PlayerPanel.tsx"
import { useCurrentUser } from "../auth/CurrentUserContext.tsx"
import { usePublicProfiles } from "../auth/PublicProfiles.tsx"
import { avatarSrc } from "../api/gameApi.ts"
import { DecisionDialog } from "../game/components/DecisionDialog.tsx"
import { BOARD_ASPECT_RATIO } from "../game/rules/boardLayout.ts"
import { formatEuro } from "../ui/format.ts"
import { BuyShareDialog } from "./room/BuyShareDialog.tsx"
import { DebugPanel } from "./room/DebugPanel.tsx"
import { EventLog } from "./room/EventLog.tsx"
import { turnStockTip as findTurnStockTip } from "./room/eventLog.ts"
import { FinalStandings } from "./room/FinalStandings.tsx"
import { MeetingDialog } from "./room/MeetingDialog.tsx"
import { useGameRoom } from "./room/useGameRoom.ts"

const STACKED_GAME_LAYOUT = "@media (min-width: 1536px) and (max-width: 2199.95px)"
const SHORT_WIDE_GAME_LAYOUT = "@media (min-width: 2200px) and (max-height: 1099.95px)"

const GameRoomContent = () => {
    const { id } = useParams()
    const { profile, debugMode } = useCurrentUser()
    const { user, view, board, events, error, commandBusy, diceInput, setDiceInput, sendCommand, selectCard } = useGameRoom(id)
    const boardAnchor = useRef<HTMLDivElement>(null)
    const [confirmRequest, setConfirmRequest] = useState<ConfirmRequest | null>(null)
    const [buyShareOpen, setBuyShareOpen] = useState(false)
    // The group of the shareholders' meeting being called; null when the dialog is closed
    const [meetingGroup, setMeetingGroup] = useState<string | null>(null)
    const profiles = usePublicProfiles([
        ...(view?.game.state.players.map((player) => player.playerId) ?? []),
        ...events.flatMap((entry) => Object.values(entry.event).filter((value): value is string => typeof value === "string")),
    ])

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

    const { game: rawGame, allowedCommands } = view
    const game = {
        ...rawGame,
        state: {
            ...rawGame.state,
            players: rawGame.state.players.map((player) => ({
                ...player,
                name: profiles[player.playerId]?.displayName ?? (player.playerId.startsWith("debug:") ? `Debug player ${player.piece + 1}` : player.playerId),
                photoUrl: avatarSrc(profiles[player.playerId]?.avatar) ?? null,
            })),
        },
    }
    const identityId = profile?.id ?? ""
    const actingUid = game.mode === "DEBUG" ? view.actingPlayer : identityId
    const actingPlayer = game.state.players.find((player) => player.playerId === actingUid)
    const pending = game.state.pendingDecisions[0]
    const decisionActor = pending?.player === actingUid
    // Whoever the game is waiting for: the player of a pending decision, otherwise the player in turn
    const expectedUid = pending?.player ?? game.state.currentPlayer
    const playerName = (playerId: string) =>
        profiles[playerId]?.displayName ??
            game.state.players.find((player) => player.playerId === playerId)?.name ??
            (playerId.startsWith("debug:") ? "Debug player" : "a former player")
    // Only a Stock Tip drawn during the current turn stays face up on the board
    const turnStockTip = findTurnStockTip(events)
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
                        The bank sells it for {formatEuro(deed.price)}.
                        {deed.price > cash && ` You have ${formatEuro(cash)}: raise ${formatEuro(deed.price - cash)} more first.`}
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
                    canSend={game.mode === "DEBUG" ? game.creator === identityId : game.state.players.some((player) => player.playerId === identityId)}
                />
            ),
        },
        {
            label: "Event log",
            content: () => <EventLog events={events} board={board} playerName={playerName} />,
        },
        ...(game.mode === "DEBUG" && debugMode
            ? [{
                label: "Debug",
                content: () => (
                    <DebugPanel
                        game={game}
                        board={board}
                        controlling={actingPlayer?.name}
                        running={game.status === "RUNNING"}
                        hasPending={Boolean(pending)}
                        busy={commandBusy}
                        diceInput={diceInput}
                        onDiceChange={setDiceInput}
                        onSelectCard={selectCard}
                    />
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
                    <Box ref={boardAnchor} sx={{ position: "relative" }}>
                        <GameBoard
                            game={game}
                            board={board}
                            buildable={buildable}
                            onBuild={requestBuild}
                            purchasable={purchasable}
                            onBuy={requestBuy}
                            meetings={meetings}
                            onMeeting={setMeetingGroup}
                            turnStockTip={turnStockTip ? String(turnStockTip.event.card) : null}
                        />
                        {pending && !decisionActor && (
                            <Alert
                                severity="info"
                                role="status"
                                sx={{
                                    position: "absolute",
                                    top: "22%",
                                    left: "50%",
                                    transform: "translateX(-50%)",
                                    width: "max-content",
                                    maxWidth: "80%",
                                    boxShadow: 3,
                                }}
                            >
                                Waiting for {playerName(pending.player)} to decide.
                            </Alert>
                        )}
                    </Box>
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
                                key={player.playerId}
                                player={player}
                                game={game}
                                board={board}
                                you={player.playerId === identityId}
                                inTurn={game.status === "RUNNING" && player.playerId === game.state.currentPlayer}
                                expected={game.status !== "RUNNING" || expectedUid !== player.playerId
                                    ? null
                                    : pending
                                    ? player.playerId === actingUid ? "Your decision" : "Deciding"
                                    : player.playerId === actingUid
                                    ? "Your move"
                                    : "To move"}
                                controls={game.status === "RUNNING" && player.playerId === actingUid ? controls : undefined}
                            />
                        ))}
                        {game.state.finished && (
                            <Alert severity="success">
                                {game.state.winner
                                    ? `${game.state.players.find((p) => p.playerId === game.state.winner)?.name} wins.`
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
            {game.state.finished && game.state.finalStandings.length > 0 && <FinalStandings game={game} />}
            {decisionActor && pending && (
                <DecisionDialog
                    key={JSON.stringify(pending)}
                    pending={pending}
                    allowed={allowedCommands}
                    send={sendCommand}
                    game={game}
                    board={board}
                    busy={commandBusy}
                    sourceStockTip={turnStockTip ? String(turnStockTip.event.card) : null}
                    anchor={boardAnchor}
                />
            )}
            <ConfirmDialog
                request={confirmRequest}
                busy={commandBusy}
                onClose={() => setConfirmRequest(null)}
                onConfirm={(command) => void sendCommand(command)}
            />
            <BuyShareDialog
                open={buyShareOpen}
                game={game}
                board={board}
                cash={cash}
                busy={commandBusy}
                onClose={() => setBuyShareOpen(false)}
                onBuy={(share) => void sendCommand({ type: "BuyShare", share })}
            />
            <MeetingDialog
                // A fresh dialog (and fee choice) for every meeting
                key={meetingGroup ?? "closed"}
                groupName={meeting ? board?.groups.find((group) => group.id === meetingGroup)?.name ?? meetingGroup : null}
                takeover={meeting?.takeover ?? 0}
                cash={cash}
                busy={commandBusy}
                onClose={() => setMeetingGroup(null)}
                onCall={(brokerageFee) => void sendCommand({ type: "CallShareholdersMeeting", group: meetingGroup, brokerageFee })}
            />
        </Stack>
    )
}

export const GameRoom = () => {
    const { id } = useParams()
    const { user, profile } = useCurrentUser()
    return <GameRoomContent key={id + ":" + user?.uid + ":" + profile?.id} />
}

import { useRef, useState } from "react"
import { useParams } from "react-router"
import { Alert, Box, Button, CircularProgress, Grid, Stack } from "@mui/material"
import { useTranslation } from "react-i18next"

import FlagOutlined from "@mui/icons-material/FlagOutlined"

import { GameBoard } from "../game/components/GameBoard.tsx"
import { Chat } from "../chat/Chat.tsx"
import { GameWindow, type GameWindowTab } from "../ui/GameWindow.tsx"
import { TitleDeedCard } from "../game/components/Cards.tsx"
import { buildAction, type PlayerControls } from "../game/rules/assetActions.ts"
import { ConfirmDialog, type ConfirmRequest } from "../ui/ConfirmDialog.tsx"
import { takeoverSum } from "../game/rules/gameRules.ts"
import { PlayerPanel } from "../game/components/PlayerPanel.tsx"
import { useCurrentUser } from "../auth/CurrentUserContext.tsx"
import { usePublicProfiles } from "../auth/PublicProfiles.tsx"
import { avatarSrc } from "../api/gameApi.ts"
import { DecisionDialog } from "../game/components/DecisionDialog.tsx"
import { BOARD_ASPECT_RATIO, SHORT_WIDE_GAME_LAYOUT, STACKED_GAME_LAYOUT } from "../game/rules/boardLayout.ts"
import { formatEuro } from "../ui/format.ts"
import { BuyShareDialog } from "./room/BuyShareDialog.tsx"
import { DebugPanel } from "./room/DebugPanel.tsx"
import { EventLog } from "./room/EventLog.tsx"
import { turnStockTip as findTurnStockTip } from "./room/eventLog.ts"
import { FinalStandings } from "./room/FinalStandings.tsx"
import { MeetingDialog } from "./room/MeetingDialog.tsx"
import { useGameRoom } from "./room/useGameRoom.ts"

const GameRoomContent = () => {
    const { t } = useTranslation()
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

    if (!user) return <Alert severity="info" sx={{ mt: 2 }}>{t("room.signInRequired")}</Alert>
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

    const { game: rawGame, allowedCommands, options } = view
    const game = {
        ...rawGame,
        state: {
            ...rawGame.state,
            players: rawGame.state.players.map((player) => ({
                ...player,
                name: profiles[player.playerId]?.displayName ??
                    (player.playerId.startsWith("debug:") ? t("room.debugPlayer", { number: player.piece + 1 }) : player.playerId),
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
            (playerId.startsWith("debug:") ? t("room.debugPlayerShort") : t("room.formerPlayer"))
    // Only a Stock Tip drawn during the current turn stays face up on the board
    const turnStockTip = findTurnStockTip(events)
    const deedOf = (square: number) => board?.titleDeeds.find((deed) => deed.square === square)
    // What can be done right now, with current prices, comes from the backend's options
    const buildable = options.build.map((offer) => offer.square)
    const purchasable = options.buyProperty.map((offer) => offer.square)
    const cash = actingPlayer?.cash ?? 0
    const requestBuy = (square: number) => {
        const deed = deedOf(square)
        const offer = options.buyProperty.find((item) => item.square === square)
        if (!deed || !offer) return
        setConfirmRequest({
            title: t("room.buyTitle", { name: deed.name }),
            body: (
                <Stack spacing={1.5} sx={{ alignItems: "center", fontSize: 13 }}>
                    <TitleDeedCard deed={deed} groupName={board?.groups.find((group) => group.id === deed.group)?.name} />
                    <span>{t("room.bankSells", { price: formatEuro(offer.amount) })}</span>
                </Stack>
            ),
            confirmLabel: t("room.buy"),
            command: { type: "BuyProperty", square },
        })
    }
    // The takeover cost is shown for information; which meetings and fees are possible comes from the options
    const meetings = options.shareholdersMeeting.map((offer) => ({
        group: offer.group,
        fees: offer.brokerageFees,
        takeover: actingUid ? takeoverSum(game, board, offer.group, actingUid) : 0,
    }))
    const meeting = meetings.find((item) => item.group === meetingGroup)
    const requestBuild = (square: number) => {
        const deed = deedOf(square)
        const action = deed && buildAction(options, deed)
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
                            title: t("room.resignTitle"),
                            body: t("room.resignBody"),
                            confirmLabel: t("room.resign"),
                            command: { type: "Resign" },
                            danger: true,
                        })}
                >
                    {t("room.resign")}
                </Button>
            )}
        </>
    )
    const controls: PlayerControls = {
        allowed: allowedCommands,
        options,
        busy: commandBusy,
        send: (command) => void sendCommand(command),
        confirm: setConfirmRequest,
        contextActions: contextActions || undefined,
        onBuyShare: options.buyShare.length > 0 ? () => setBuyShareOpen(true) : undefined,
    }
    const gameActivityTabs: GameWindowTab[] = [
        {
            label: t("room.tab.chat"),
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
            label: t("room.tab.events"),
            content: () => <EventLog events={events} board={board} playerName={playerName} />,
        },
        ...(game.mode === "DEBUG" && debugMode
            ? [{
                label: t("room.tab.debug"),
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
                                {t("room.waitingFor", { name: playerName(pending.player) })}
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
                                    ? player.playerId === actingUid ? t("room.expected.yourDecision") : t("room.expected.deciding")
                                    : player.playerId === actingUid
                                    ? t("room.expected.yourMove")
                                    : t("room.expected.toMove")}
                                controls={game.status === "RUNNING" && player.playerId === actingUid ? controls : undefined}
                            />
                        ))}
                        {game.state.finished && (
                            <Alert severity="success">
                                {game.state.winner
                                    ? t("room.wins", { name: game.state.players.find((p) => p.playerId === game.state.winner)?.name })
                                    : t("room.closedWithoutWinner")}
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
                    options={options}
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
                offers={options.buyShare}
                board={board}
                busy={commandBusy}
                onClose={() => setBuyShareOpen(false)}
                onBuy={(share) => void sendCommand({ type: "BuyShare", share })}
            />
            <MeetingDialog
                // A fresh dialog (and fee choice) for every meeting
                key={meetingGroup ?? "closed"}
                groupName={meeting ? board?.groups.find((group) => group.id === meetingGroup)?.name ?? meetingGroup : null}
                takeover={meeting?.takeover ?? 0}
                fees={meeting?.fees ?? []}
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

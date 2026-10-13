import { type ReactNode } from "react"
import { Avatar, Box, Button, ButtonBase, Card, CardContent, Chip, IconButton, Stack, Tooltip, Typography } from "@mui/material"
import { keyframes } from "@emotion/react"
import { useTranslation } from "react-i18next"
import AccountBalanceOutlined from "@mui/icons-material/AccountBalanceOutlined"
import Casino from "@mui/icons-material/Casino"
import CheckCircleOutlineRounded from "@mui/icons-material/CheckCircleOutlineRounded"
import DirectionsCarOutlined from "@mui/icons-material/DirectionsCarOutlined"
import HomeWorkOutlined from "@mui/icons-material/HomeWorkOutlined"
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined"
import ReceiptLongOutlined from "@mui/icons-material/ReceiptLongOutlined"
import ShowChartOutlined from "@mui/icons-material/ShowChartOutlined"
import StyleOutlined from "@mui/icons-material/StyleOutlined"

import { CardPeek } from "./Cards.tsx"
import { playerColor, playerShade } from "./PlayerToken.tsx"
import { BondCard, CarCard, cardPeekSlotProps, CardRow, LoanCard, ShareCard, TitleDeedCard } from "./Cards.tsx"
import {
    type AssetAction,
    carAction,
    loanActions,
    NO_OPTIONS,
    type PlayerControls,
    propertyActions,
    shareActions,
    stockTipAction,
} from "../rules/assetActions.ts"
import { rentDoubled } from "../rules/gameRules.ts"
import type { Game, GameBoardData, GamePlayer } from "../../api/gameApi.ts"
import { formatEuro } from "../../ui/format.ts"

const pulse = keyframes`
    0%, 100% { box-shadow: 0 0 0 0 rgba(25, 118, 210, 0.55); }
    50% { box-shadow: 0 0 0 6px rgba(25, 118, 210, 0); }
`

/** Ring around an asset icon that opens a purchase, matching the glow of properties for sale on the board */
const iconHalo = keyframes`
    0%, 100% { box-shadow: 0 0 3px 2px rgba(255, 214, 64, 0.89); }
    50% { box-shadow: 0 0 5px 3px rgba(255, 214, 64, 0.96); }
`

const assetSx = { display: "inline-flex", alignItems: "center", gap: 0.4, px: 0.25, borderRadius: 1, "& svg": { fontSize: 18 } } as const

/**
 * An asset icon with a count. Hovering shows the asset cards (with their actions), or just the title when there are no cards.
 * `onClick` makes the icon itself an action; `faded` shows an asset the player doesn't have, e.g. no car; `halo` marks an icon that
 * opens a purchase.
 */
const Asset = ({ icon, count, title, cards, onClick, faded, halo }: {
    icon: ReactNode
    count?: ReactNode
    title: string
    cards?: ReactNode
    onClick?: (anchor: HTMLElement) => void
    faded?: boolean
    halo?: boolean
}) => {
    const shownIcon = halo
        ? (
            <Box
                component="span"
                sx={{
                    display: "inline-flex",
                    p: "2px",
                    borderRadius: "50%",
                    backgroundColor: "rgba(255, 214, 64, 0.25)",
                    animation: `${iconHalo} 1.8s ease-in-out infinite`,
                }}
            >
                {icon}
            </Box>
        )
        : icon
    return (
        <Tooltip
            arrow={!cards}
            leaveDelay={cards ? 150 : 0}
            slotProps={cards ? cardPeekSlotProps : undefined}
            title={cards ? <CardRow>{cards}</CardRow> : title}
        >
            {onClick
                ? (
                    <ButtonBase
                        onClick={(event) => onClick(event.currentTarget)}
                        aria-label={title}
                        sx={{
                            ...assetSx,
                            color: faded ? "text.disabled" : undefined,
                            "&:hover": { backgroundColor: "action.hover", color: "primary.main" },
                        }}
                    >
                        {shownIcon}
                        {count != null && <Typography variant="body2" component="span">{count}</Typography>}
                    </ButtonBase>
                )
                : (
                    <Box
                        component="span"
                        role="img"
                        tabIndex={0}
                        aria-label={title}
                        sx={{ ...assetSx, color: faded ? "text.disabled" : undefined, cursor: "default" }}
                    >
                        {shownIcon}
                        {count != null && <Typography variant="body2" component="span">{count}</Typography>}
                    </Box>
                )}
        </Tooltip>
    )
}

/** A card in a peek, with its actions underneath */
const ActionCard = ({ card, actions, controls }: { card: ReactNode; actions: AssetAction[]; controls?: PlayerControls }) => (
    <Stack spacing={0.5}>
        {card}
        {controls && actions.length > 0 && (
            <Stack
                direction="row"
                spacing={0.5}
                useFlexGap
                sx={{ flexWrap: "wrap", justifyContent: "center", p: 0.5, borderRadius: 1, backgroundColor: "background.paper", boxShadow: 2 }}
            >
                {actions.map((action) => (
                    <Button
                        key={action.label}
                        size="small"
                        variant="outlined"
                        color={action.request.danger ? "error" : "primary"}
                        disabled={controls.busy}
                        onClick={() => controls.confirm(action.request)}
                    >
                        {action.label}
                    </Button>
                ))}
            </Stack>
        )}
    </Stack>
)

/** A player's box; the player in turn gets a bold outline and the player the game waits for an action chip */
export const PlayerPanel = ({ player, game, board, you, inTurn, expected, controls }: {
    player: GamePlayer
    game: Game
    board: GameBoardData | null
    you: boolean
    inTurn: boolean
    expected: string | null
    /** Given for the player you control: assets become actions and the turn actions show */
    controls?: PlayerControls
}) => {
    const { t } = useTranslation()
    const color = playerColor(player.piece)
    const allowed = controls?.allowed ?? []
    const options = controls?.options ?? NO_OPTIONS
    const properties = game.state.properties.filter((property) => property.owner === player.playerId)
    const shares = game.state.shares.filter((share) => share.owner === player.playerId)
    const bonds = game.state.bonds.filter((bond) => bond.owner === player.playerId)
    const car = carAction(allowed, player.car)
    const loans = loanActions(allowed)
    const tipTitle = (id: string) => {
        const card = board?.stockTips.find((item) => item.id === id)
        return card?.chapters.find((chapter) => chapter.type === "header")?.text ?? t("board.stockTip")
    }
    const usableTips = player.heldStockTips.map((id) => stockTipAction(allowed, id, tipTitle(id))).filter((action) => action != null)
    const turnActions = controls && (allowed.includes("Roll") || allowed.includes("EndTurn") || inTurn)
    return (
        <Card
            variant="outlined"
            sx={{
                opacity: player.out ? 0.55 : 1,
                borderColor: inTurn ? color : undefined,
                borderWidth: inTurn ? 3 : 1,
                borderLeft: `8px solid ${color}`,
            }}
        >
            <CardContent sx={{ "&:last-child": { pb: 2 } }}>
                <Stack direction="row" spacing={1.25} sx={{ alignItems: "center" }}>
                    <Avatar src={player.photoUrl ?? undefined} sx={{ bgcolor: color, border: `2px solid ${playerShade(player.piece)}` }}>
                        {player.name.slice(0, 1)}
                    </Avatar>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography sx={{ fontWeight: inTurn ? 700 : 400 }}>
                            {you
                                ? player.out ? t("board.panel.youOut", { name: player.name }) : t("board.panel.you", { name: player.name })
                                : player.out
                                ? t("board.panel.out", { name: player.name })
                                : player.name}
                        </Typography>
                        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", mt: 0.25, color: "text.secondary" }}>
                            <Asset icon={<PaymentsOutlined />} count={formatEuro(player.cash)} title={t("board.panel.cash")} />
                            {(player.loans > 0 || controls) && (
                                <Asset
                                    icon={<AccountBalanceOutlined />}
                                    count={player.loans}
                                    faded={player.loans === 0}
                                    title={player.loans === 0 ? t("board.panel.noLoans") : t("board.panel.loans", { count: player.loans })}
                                    cards={player.loans > 0 ? Array.from({ length: player.loans }, (_, index) => <LoanCard key={index} />) : undefined}
                                    onClick={loans.length > 0 && controls ? () => controls.confirm({
                                        title: t("board.panel.loans", { count: player.loans }),
                                        body: undefined,
                                        confirmLabel: "",
                                        command: {},
                                        choices: loans.map((action) => ({ label: action.label, command: action.request.command })),
                                    }) : undefined}
                                />
                            )}
                            {(player.car || controls) && (
                                <Asset
                                    icon={<DirectionsCarOutlined />}
                                    faded={!player.car}
                                    title={player.car ? t("board.panel.car") : car ? t("board.panel.noCarBuy") : t("board.panel.noCar")}
                                    cards={player.car ? <ActionCard card={<CarCard />} actions={car ? [car] : []} controls={controls} /> : undefined}
                                    onClick={car && controls ? () => controls.confirm(car.request) : undefined}
                                />
                            )}
                            {properties.length > 0 && (
                                <Asset
                                    icon={<HomeWorkOutlined />}
                                    count={properties.length}
                                    title={t("board.panel.properties")}
                                    cards={properties.map((property) => {
                                        const deed = board?.titleDeeds.find((item) => item.square === property.square)
                                        return deed && (
                                            <ActionCard
                                                key={property.square}
                                                controls={controls}
                                                actions={propertyActions(options, property, deed)}
                                                card={
                                                    <TitleDeedCard
                                                        deed={deed}
                                                        groupName={board?.groups.find((group) => group.id === deed.group)?.name}
                                                        built={property.built}
                                                        mortgaged={property.mortgaged}
                                                        rentDoubled={rentDoubled(game, board, property.square)}
                                                    />
                                                }
                                            />
                                        )
                                    })}
                                />
                            )}
                            {(shares.length > 0 || controls?.onBuyShare) && (
                                <Asset
                                    icon={<ShowChartOutlined />}
                                    count={shares.length}
                                    faded={shares.length === 0}
                                    title={controls?.onBuyShare ? t("board.panel.sharesBuy") : t("board.panel.shares")}
                                    // While the bank sells shares the icon glows and opens the purchase; hovering still shows owned shares
                                    halo={controls?.onBuyShare != null}
                                    onClick={controls?.onBuyShare}
                                    cards={shares.length === 0 ? undefined : shares.map((owned) => {
                                        const share = board?.shares.find((item) => item.id === owned.id)
                                        const name = share?.group
                                            ? board?.groups.find((group) => group.id === share.group)?.name ?? share.group
                                            : t("board.panel.fund")
                                        return share && (
                                            <ActionCard
                                                key={share.id}
                                                controls={controls}
                                                actions={shareActions(options, share, name)}
                                                card={<ShareCard share={share} board={board} />}
                                            />
                                        )
                                    })}
                                />
                            )}
                            {bonds.length > 0 && (
                                <Asset
                                    icon={<ReceiptLongOutlined />}
                                    count={bonds.length}
                                    title={t("board.panel.bonds")}
                                    cards={bonds.map((bond) => <BondCard key={bond.number} number={bond.number} />)}
                                />
                            )}
                            {player.heldStockTips.length > 0 && (
                                <Asset
                                    icon={<StyleOutlined />}
                                    count={player.heldStockTips.length}
                                    title={t("board.panel.heldStockTips")}
                                    // With a single playable card the icon plays it; with several, each card's own button does
                                    onClick={controls && usableTips.length === 1 ? () => controls.confirm(usableTips[0].request) : undefined}
                                    cards={player.heldStockTips.map((id) => {
                                        const action = stockTipAction(allowed, id, tipTitle(id))
                                        return (
                                            <ActionCard
                                                key={id}
                                                controls={controls}
                                                actions={action ? [action] : []}
                                                card={<CardPeek card={board?.stockTips.find((item) => item.id === id)} fallback={t("board.stockTip")} />}
                                            />
                                        )
                                    })}
                                />
                            )}
                        </Stack>
                    </Box>
                    {expected && (
                        <Chip
                            size="small"
                            label={expected}
                            color="primary"
                            variant={controls ? "filled" : "outlined"}
                            sx={controls ? { fontWeight: 700, animation: `${pulse} 1.6s ease-in-out infinite` } : undefined}
                        />
                    )}
                </Stack>
                {controls && turnActions && (
                    <Stack direction="row" spacing={1} sx={{ mt: 1.25, alignItems: "center" }}>
                        <Tooltip title={t("board.panel.rollTooltip")}>
                            <span>
                                <IconButton
                                    aria-label={t("board.panel.roll")}
                                    disabled={!allowed.includes("Roll") || controls.busy}
                                    onClick={() => controls.send({ type: "Roll" })}
                                    sx={{
                                        bgcolor: "primary.main",
                                        color: "primary.contrastText",
                                        "&:hover": { bgcolor: "primary.dark" },
                                        "&.Mui-disabled": { bgcolor: "action.disabledBackground" },
                                    }}
                                >
                                    <Casino />
                                </IconButton>
                            </span>
                        </Tooltip>
                        <Tooltip title={t("board.panel.endTurn")}>
                            <span>
                                <IconButton
                                    aria-label={t("board.panel.endTurn")}
                                    color="success"
                                    disabled={!allowed.includes("EndTurn") || controls.busy}
                                    onClick={() => controls.send({ type: "EndTurn" })}
                                    sx={{ border: 1, borderColor: "currentcolor" }}
                                >
                                    <CheckCircleOutlineRounded />
                                </IconButton>
                            </span>
                        </Tooltip>
                    </Stack>
                )}
                {controls?.contextActions && (
                    <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 1, pt: 1, borderTop: 1, borderColor: "divider", flexWrap: "wrap" }}>
                        {controls.contextActions}
                    </Stack>
                )}
            </CardContent>
        </Card>
    )
}

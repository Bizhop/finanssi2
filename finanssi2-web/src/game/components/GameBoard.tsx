import { Box, IconButton, Tooltip } from "@mui/material"
import { useTranslation } from "react-i18next"
import AddHome from "@mui/icons-material/AddHome"
import GroupsOutlined from "@mui/icons-material/GroupsOutlined"
import { keyframes } from "@emotion/react"
import FactoryRounded from "@mui/icons-material/FactoryRounded"
import HomeRounded from "@mui/icons-material/HomeRounded"

import boardImage from "../../assets/board.webp"
import financeNewsBack from "../../assets/cards/finance-news-back.webp"
import stockTipBack from "../../assets/cards/stock-tip-back.webp"
import { besideSquares, BOARD_ASPECT_RATIO, CARD_PLACES, type SquareRect, squareRect, squareSide } from "../rules/boardLayout.ts"
import type { Card, Game, GameBoardData } from "../../api/gameApi.ts"
import { playerColor, PlayerToken } from "./PlayerToken.tsx"
import { SquareDetails } from "./SquareDetails.tsx"
import { CardFace, cardPeekSlotProps } from "./Cards.tsx"
import { formatEuro, formatNumber } from "../../ui/format.ts"

const placement = (rect: SquareRect) =>
    ({
        position: "absolute",
        left: `${rect.left}%`,
        top: `${rect.top}%`,
        width: `${rect.width}%`,
        height: `${rect.height}%`,
    }) as const

/** Text size of the face-up cards, small enough for the longest card (FL-19) to fit */
const DRAWN_CARD_FONT = "0.74cqw"

/** A face-up card on the board; who drew it needs no caption, as it is the player in turn */
const DrawnCard = (
    { rect, card, fallback, label }: { rect: SquareRect; card: Card | undefined; fallback: string; label?: string },
) => (
    <Box sx={{ ...placement(rect), fontSize: DRAWN_CARD_FONT, boxShadow: "0 0.3cqw 0.8cqw rgba(0,0,0,0.6)", borderRadius: "0.4em" }}>
        <Box data-card-face sx={{ height: "100%", overflow: "hidden", borderRadius: "0.4em" }}>
            <CardFace card={card} fallback={fallback} label={label} />
        </Box>
    </Box>
)

/** Glow inside a property you can buy; kept inside the square so it doesn't light up its neighbours */
const halo = keyframes`
    0%, 100% { box-shadow: inset 0 0 0.35cqw 0.15cqw rgba(255, 214, 64, 0.89); }
    50% { box-shadow: inset 0 0 0.5cqw 0.25cqw rgba(255, 214, 64, 0.96); }
`

/** Gold ring around a round action button on the board, in the colour and rhythm of the property halo */
const iconHalo = keyframes`
    0%, 100% { box-shadow: 0 0 0.4cqw 0.25cqw rgba(255, 214, 64, 0.89); }
    50% { box-shadow: 0 0 0.6cqw 0.35cqw rgba(255, 214, 64, 0.96); }
`

/** What calling a shareholders' meeting means, from the rules */
const MeetingRules = ({ groupName, takeover }: { groupName: string; takeover: number }) => {
    const { t } = useTranslation()
    return (
        <Box sx={{ maxWidth: 300, "& p": { m: 0, mt: 0.75 } }}>
            <Box sx={{ fontWeight: 700 }}>{t("board.meeting.title", { group: groupName })}</Box>
            <p>
                {t("board.meeting.offer", { takeover: formatEuro(takeover), minFee: formatEuro(20_000), maxFee: formatEuro(120_000) })}
            </p>
            <p>
                {t("board.meeting.roll", { unit: formatNumber(10_000), bankShare: formatEuro(30_000) })}
            </p>
        </Box>
    )
}

/** Where a building stands on its square: at the outer edge over the printed price, which no longer matters once built */
const BUILDING_POSITION = {
    left: { left: "4%", top: "50%", transform: "translateY(-50%)" },
    right: { right: "4%", top: "50%", transform: "translateY(-50%)" },
    top: { top: "4%", left: "50%", transform: "translateX(-50%)" },
    bottom: { bottom: "4%", left: "50%", transform: "translateX(-50%)" },
    corner: { right: "4%", top: "4%" },
}

/** A building on a square: red for ordinary buildings, black for industrial plants, as in the physical game */
const Building = ({ square, industrial }: { square: number; industrial: boolean }) => (
    <Box
        sx={{
            position: "absolute",
            ...BUILDING_POSITION[squareSide(square)],
            display: "flex",
            color: industrial ? "#151515" : "#c62828",
            filter: "drop-shadow(0 0 0.15cqw #fff) drop-shadow(0 0 0.15cqw #fff) drop-shadow(0 0.15cqw 0.3cqw rgba(0,0,0,0.6))",
            "& svg": { fontSize: "max(13px, 2.1cqw)" },
            pointerEvents: "none",
        }}
    >
        {industrial ? <FactoryRounded /> : <HomeRounded />}
    </Box>
)

const Deck = ({ rect, image, name }: { rect: SquareRect; image: string; name: string }) => (
    <Box
        component="img"
        src={image}
        alt={name}
        title={name}
        sx={{
            ...placement(rect),
            borderRadius: "0.4cqw",
            // Edges of the cards underneath the top one
            boxShadow: "0.15cqw 0.15cqw 0 #d9dde2, 0.3cqw 0.3cqw 0 #b9bec5, 0.45cqw 0.45cqw 0 #d9dde2, 0.6cqw 0.6cqw 1cqw rgba(0,0,0,0.6)",
        }}
    />
)

export const GameBoard = (
    { game, board, turnStockTip, turnFinanceNews, buildable = [], onBuild, purchasable = [], onBuy, meetings = [], onMeeting, selectable = [], onSelectSquare, selectedSquare }:
        {
            game: Game
            board: GameBoardData | null
            /** The Stock Tip drawn this turn, if any */
            turnStockTip: string | null
            /** Finance News drawn this turn, including non-permanent cards */
            turnFinanceNews?: string | null
            /** Squares you can build on right now; each gets a build button */
            buildable?: number[]
            onBuild?: (square: number) => void
            /** Bank properties you can buy right now; they glow and buy on click */
            purchasable?: number[]
            onBuy?: (square: number) => void
            /** Groups where you can call a shareholders' meeting, with what the takeover costs; each gets a meeting button by its properties */
            meetings?: { group: string; takeover: number }[]
            onMeeting?: (group: string) => void
            /** A decision's legal destinations; selecting a square does not execute a purchase. */
            selectable?: number[]
            onSelectSquare?: (square: number) => void
            selectedSquare?: number | null
        },
) => {
    const { t } = useTranslation()
    const propertyBySquare = new Map(game.state.properties.map((property) => [property.square, property]))
    const activeNews = turnFinanceNews ?? game.state.activeFinanceNews
    return (
        <Box
            sx={{
                position: "relative",
                containerType: "inline-size",
                aspectRatio: `${BOARD_ASPECT_RATIO}`,
                width: "100%",
                overflow: "hidden",
                borderRadius: 1,
                backgroundImage: `url(${boardImage})`,
                backgroundSize: "100% 100%",
            }}
        >
            {(board?.squares ??
                Array.from(
                    { length: 46 },
                    (_, index) => ({ square: index + 1, name: t("board.loadingSquare"), type: "", group: null, price: null, text: null }),
                ))
                .map((square) => {
                    const property = propertyBySquare.get(square.square)
                    const tokens = game.state.players.filter((player) => !player.out && player.position === square.square)
                    const owner = game.state.players.find((player) => player.playerId === property?.owner)
                    const canSelect = onSelectSquare != null && selectable.includes(square.square)
                    const forSale = onBuy != null && purchasable.includes(square.square)
                    return (
                        <Tooltip
                            key={square.square}
                            arrow
                            disableInteractive
                            slotProps={cardPeekSlotProps}
                            // Only property squares have a peek, their title deed; other squares explain themselves on the board
                            title={board?.titleDeeds.some((deed) => deed.square === square.square)
                                ? <SquareDetails square={square} board={board} game={game} />
                                : ""}
                        >
                            <Box
                                role={canSelect || forSale ? "button" : undefined}
                                aria-label={canSelect
                                    ? t("board.selectSquare", { name: square.name })
                                    : forSale
                                    ? t("board.buySquare", { name: square.name })
                                    : undefined}
                                tabIndex={canSelect || forSale ? 0 : undefined}
                                onClick={canSelect ? () => onSelectSquare(square.square) : forSale ? () => onBuy(square.square) : undefined}
                                onKeyDown={canSelect || forSale
                                    ? (event) => {
                                        if (event.key !== "Enter" && event.key !== " ") return
                                        event.preventDefault()
                                        if (canSelect) onSelectSquare(square.square)
                                        else onBuy?.(square.square)
                                    }
                                    : undefined}
                                sx={{
                                    ...placement(squareRect(square.square)),
                                    ...(forSale || canSelect) && {
                                        cursor: "pointer",
                                        animation: `${halo} 1.8s ease-in-out infinite`,
                                        "&:hover": { backgroundColor: "rgba(255, 214, 64, 0.25)" },
                                    },
                                    ...(selectedSquare === square.square &&
                                        { backgroundColor: "rgba(255, 214, 64, 0.6)", outline: "0.5cqw solid #ffd640", outlineOffset: "-0.5cqw" }),
                                    boxSizing: "border-box",
                                    display: "flex",
                                    flexWrap: "wrap",
                                    alignItems: "center",
                                    alignContent: "center",
                                    justifyContent: "center",
                                    gap: 0.25,
                                    p: 0.25,
                                    border: owner ? `0.35cqw ${property?.mortgaged ? "dashed" : "solid"} ${playerColor(owner.piece)}` : undefined,
                                }}
                            >
                                {property?.built && (
                                    <Building
                                        square={square.square}
                                        industrial={board?.titleDeeds.find((deed) => deed.square === square.square)?.building?.label === "Teollisuus"}
                                    />
                                )}
                                {onBuild && buildable.includes(square.square) && (
                                    <IconButton
                                        aria-label={t("board.buildOn", { name: square.name })}
                                        onClick={() => onBuild(square.square)}
                                        sx={{
                                            position: "absolute",
                                            ...BUILDING_POSITION[squareSide(square.square)],
                                            p: "0.2cqw",
                                            color: "#c62828",
                                            backgroundColor: "rgba(255,255,255,0.85)",
                                            border: "0.12cqw dashed #c62828",
                                            "&:hover": { backgroundColor: "#fff" },
                                            "& svg": { fontSize: "max(13px, 1.7cqw)" },
                                        }}
                                    >
                                        <AddHome />
                                    </IconButton>
                                )}
                                {tokens.map((player) => <PlayerToken key={player.playerId} player={player} size="max(14px, 1.6cqw)" />)}
                            </Box>
                        </Tooltip>
                    )
                })}
            {onMeeting && meetings.map(({ group, takeover }) => {
                const groupData = board?.groups.find((item) => item.id === group)
                if (!groupData) return null
                const point = besideSquares(groupData.properties)
                return (
                    <Tooltip key={group} arrow title={<MeetingRules groupName={groupData.name} takeover={takeover} />}>
                        <IconButton
                            aria-label={t("board.callMeeting", { group: groupData.name })}
                            onClick={() => onMeeting(group)}
                            sx={{
                                position: "absolute",
                                left: `${point.left}%`,
                                top: `${point.top}%`,
                                transform: "translate(-50%, -50%)",
                                p: "0.45cqw",
                                color: "#1d4f9c",
                                backgroundColor: "#fff",
                                animation: `${iconHalo} 1.8s ease-in-out infinite`,
                                "&:hover": { backgroundColor: "#fff8dc" },
                                "& svg": { fontSize: "max(16px, 2.2cqw)" },
                            }}
                        >
                            <GroupsOutlined />
                        </IconButton>
                    </Tooltip>
                )
            })}
            <Deck rect={CARD_PLACES.financeNews.deck} image={financeNewsBack} name={t("board.financeNewsDeck")} />
            <Deck rect={CARD_PLACES.stockTip.deck} image={stockTipBack} name={t("board.stockTipDeck")} />
            {activeNews && (
                <DrawnCard
                    rect={CARD_PLACES.financeNews.drawn}
                    card={board?.financeNews.find((card) => card.id === activeNews)}
                    fallback={t("board.financeNews")}
                    label="FINANSSILEHTI"
                />
            )}
            {turnStockTip && (
                <DrawnCard
                    rect={CARD_PLACES.stockTip.drawn}
                    card={board?.stockTips.find((card) => card.id === turnStockTip)}
                    fallback={t("board.stockTip")}
                />
            )}
        </Box>
    )
}

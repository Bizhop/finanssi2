import { Box, IconButton, Tooltip } from "@mui/material"
import AddHome from "@mui/icons-material/AddHome"
import GroupsOutlined from "@mui/icons-material/GroupsOutlined"
import { keyframes } from "@emotion/react"
import FactoryRounded from "@mui/icons-material/FactoryRounded"
import HomeRounded from "@mui/icons-material/HomeRounded"

import boardImage from "../assets/board.webp"
import financeNewsBack from "../assets/cards/finance-news-back.webp"
import stockTipBack from "../assets/cards/stock-tip-back.webp"
import { besideSquares, BOARD_ASPECT_RATIO, CARD_PLACES, type SquareRect, squareRect, squareSide } from "./boardLayout.ts"
import type { Card, Game, GameBoardData } from "./gameApi.ts"
import { playerColor, PlayerToken } from "./PlayerToken.tsx"
import { SquareDetails } from "./SquareDetails.tsx"
import { cardPeekSlotProps } from "./cards.tsx"

const CARD_INK = "#1d4f9c"

const placement = (rect: SquareRect) =>
    ({
        position: "absolute",
        left: `${rect.left}%`,
        top: `${rect.top}%`,
        width: `${rect.width}%`,
        height: `${rect.height}%`,
    }) as const

/** A card face styled after the printed cards; sizes are in em so the parent's font size scales the whole card */
export const CardFace = ({ card, fallback, label }: { card: Card | undefined; fallback: string; label?: string }) => (
    <Box
        sx={{
            width: "100%",
            minHeight: "100%",
            boxSizing: "border-box",
            p: "0.7em 0.6em",
            overflow: "hidden",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: "0.45em",
            borderRadius: "0.4em",
            backgroundColor: "#f3f5f7",
            // Printed edge of the card
            boxShadow: "inset 0 0 0 0.06em #c3c9d1",
            color: CARD_INK,
            textAlign: "center",
            fontFamily: "Helvetica, Arial, sans-serif",
        }}
    >
        {label && (
            <Box sx={{ alignSelf: "stretch", pb: "0.15em", borderBottom: `0.08em solid ${CARD_INK}`, fontSize: "0.7em", fontWeight: 700 }}>
                {label}
            </Box>
        )}
        {card?.chapters.map((chapter, index) => (
            <Box
                key={index}
                sx={chapter.type === "header"
                    ? { mt: index > 0 ? "0.4em" : 0, fontSize: "1.1em", fontWeight: 800, lineHeight: 1.1, textTransform: "uppercase" }
                    : { fontSize: "0.8em", lineHeight: 1.25, fontStyle: chapter["font-style"] === "italic" ? "italic" : undefined }}
            >
                {chapter.text}
            </Box>
        )) ?? <Box sx={{ fontWeight: 700 }}>{fallback}</Box>}
    </Box>
)

/** A Finance News or Stock Tip card at a readable size, for peeks */
export const CardPeek = ({ card, fallback, label }: { card: Card | undefined; fallback: string; label?: string }) => (
    <Box sx={{ width: 240, minHeight: 327, display: "flex", fontSize: 15, borderRadius: "0.4em", boxShadow: "0 0.2em 0.7em rgba(0,0,0,0.45)" }}>
        <CardFace card={card} fallback={fallback} label={label} />
    </Box>
)

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
const MeetingRules = ({ groupName, takeover }: { groupName: string; takeover: number }) => (
    <Box sx={{ maxWidth: 300, "& p": { m: 0, mt: 0.75 } }}>
        <Box sx={{ fontWeight: 700 }}>Shareholders' meeting: {groupName}</Box>
        <p>
            Show the purchase prices of the other players' properties (with buildings) and shares in the group, now €{takeover.toLocaleString()}, and put down a
            brokerage fee of €20,000–€120,000.
        </p>
        <p>
            Roll both dice. If the total is at most the fee divided by 10,000, you take over their properties and shares and pay them the purchase prices;
            €30,000 of the fee goes to the bank and the rest is shared among them. Otherwise the takeover fails and the whole fee goes to the bank.
        </p>
    </Box>
)

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

export const GameBoard = ({ game, board, turnStockTip, buildable = [], onBuild, purchasable = [], onBuy, meetings = [], onMeeting }: {
    game: Game
    board: GameBoardData | null
    /** The Stock Tip drawn this turn, if any */
    turnStockTip: string | null
    /** Squares you can build on right now; each gets a build button */
    buildable?: number[]
    onBuild?: (square: number) => void
    /** Bank properties you can buy right now; they glow and buy on click */
    purchasable?: number[]
    onBuy?: (square: number) => void
    /** Groups where you can call a shareholders' meeting, with what the takeover costs; each gets a meeting button by its properties */
    meetings?: { group: string; takeover: number }[]
    onMeeting?: (group: string) => void
}) => {
    const propertyBySquare = new Map(game.state.properties.map((property) => [property.square, property]))
    const activeNews = game.state.activeFinanceNews
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
                Array.from({ length: 46 }, (_, index) => ({ square: index + 1, name: "Loading…", type: "", group: null, price: null, text: null })))
                .map((square) => {
                    const property = propertyBySquare.get(square.square)
                    const tokens = game.state.players.filter((player) => !player.out && player.position === square.square)
                    const owner = game.state.players.find((player) => player.uid === property?.owner)
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
                                role={forSale ? "button" : undefined}
                                aria-label={forSale ? `Buy ${square.name}` : undefined}
                                tabIndex={forSale ? 0 : undefined}
                                onClick={forSale ? () => onBuy(square.square) : undefined}
                                onKeyDown={forSale ? (event) => (event.key === "Enter" || event.key === " ") && onBuy(square.square) : undefined}
                                sx={{
                                    ...placement(squareRect(square.square)),
                                    ...forSale && {
                                        cursor: "pointer",
                                        animation: `${halo} 1.8s ease-in-out infinite`,
                                        "&:hover": { backgroundColor: "rgba(255, 214, 64, 0.25)" },
                                    },
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
                                        aria-label={`Build on ${square.name}`}
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
                                {tokens.map((player) => <PlayerToken key={player.uid} player={player} size="max(14px, 1.6cqw)" />)}
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
                            aria-label={`Call a shareholders' meeting of ${groupData.name}`}
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
            <Deck rect={CARD_PLACES.financeNews.deck} image={financeNewsBack} name="Finance News deck" />
            <Deck rect={CARD_PLACES.stockTip.deck} image={stockTipBack} name="Stock Tip deck" />
            {activeNews && (
                <DrawnCard
                    rect={CARD_PLACES.financeNews.drawn}
                    card={board?.financeNews.find((card) => card.id === activeNews)}
                    fallback="Finance News"
                    label="FINANSSILEHTI"
                />
            )}
            {turnStockTip && (
                <DrawnCard
                    rect={CARD_PLACES.stockTip.drawn}
                    card={board?.stockTips.find((card) => card.id === turnStockTip)}
                    fallback="Stock Tip"
                />
            )}
        </Box>
    )
}

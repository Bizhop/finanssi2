import { Avatar, Box, IconButton, Tooltip, Typography } from "@mui/material"
import AddHome from "@mui/icons-material/AddHome"
import FactoryRounded from "@mui/icons-material/FactoryRounded"
import HomeRounded from "@mui/icons-material/HomeRounded"

import boardImage from "../assets/board.webp"
import financeNewsBack from "../assets/cards/finance-news-back.webp"
import stockTipBack from "../assets/cards/stock-tip-back.webp"
import { BOARD_ASPECT_RATIO, CARD_PLACES, type SquareRect, squareRect, squareSide } from "./boardLayout.ts"
import type { Card, Game, GameBoardData } from "./gameApi.ts"
import { SquareDetails } from "./SquareDetails.tsx"
import { cardPeekSlotProps } from "./cards.tsx"

const playerHue = (piece: number) => piece * 61 % 360
export const playerColor = (piece: number) => `hsl(${playerHue(piece)} 58% 44%)`

/** WCAG relative luminance of the player colour */
const playerLuminance = (piece: number) => {
    const s = 0.58, l = 0.44, a = s * Math.min(l, 1 - l)
    const channel = (n: number) => {
        const k = (n + playerHue(piece) / 30) % 12
        const c = l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))
        return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * channel(0) + 0.7152 * channel(8) + 0.0722 * channel(4)
}

/** A shade of the player colour that contrasts with it: lighter for dark colours, darker for light ones */
export const playerShade = (piece: number) => `hsl(${playerHue(piece)} 60% ${playerLuminance(piece) < 0.179 ? 78 : 22}%)`

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

/** A face-up card on the board; hovering or tapping shows it at a readable size */
const DrawnCard = (
    { rect, card, fallback, label, caption }: { rect: SquareRect; card: Card | undefined; fallback: string; label?: string; caption?: string },
) => (
    <Tooltip
        placement="top"
        slotProps={cardPeekSlotProps}
        title={
            <Box sx={{ p: 1 }}>
                <CardPeek card={card} fallback={fallback} label={label} />
            </Box>
        }
    >
        <Box sx={{ ...placement(rect), fontSize: "0.85cqw", cursor: "zoom-in", boxShadow: "0 0.3cqw 0.8cqw rgba(0,0,0,0.6)", borderRadius: "0.4em" }}>
            {/* The small card clips long text; the enlarged one grows to fit */}
            <Box sx={{ height: "100%", overflow: "hidden", borderRadius: "0.4em" }}>
                <CardFace card={card} fallback={fallback} label={label} />
            </Box>
            {caption && (
                <Typography
                    sx={{
                        position: "absolute",
                        top: "100%",
                        left: "-40%",
                        right: "-40%",
                        mt: "0.5cqw",
                        fontSize: "max(8px, 1cqw)",
                        lineHeight: 1.2,
                        textAlign: "center",
                        color: "#fff",
                    }}
                >
                    {caption}
                </Typography>
            )}
        </Box>
    </Tooltip>
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

export const GameBoard = ({ game, board, lastStockTip, buildable = [], onBuild }: {
    game: Game
    board: GameBoardData | null
    lastStockTip: { card: string; drawnBy: string; held: boolean } | null
    /** Squares you can build on right now; each gets a build button */
    buildable?: number[]
    onBuild?: (square: number) => void
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
                    return (
                        <Tooltip
                            key={square.square}
                            arrow
                            disableInteractive
                            slotProps={cardPeekSlotProps}
                            title={<SquareDetails square={square} board={board} game={game} here={tokens.map((player) => player.name)} />}
                        >
                            <Box
                                sx={{
                                    ...placement(squareRect(square.square)),
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
                                {tokens.map((player) => (
                                    <Avatar
                                        key={player.uid}
                                        src={player.photoUrl ?? undefined}
                                        alt={player.name}
                                        sx={{
                                            width: "max(14px, 1.6cqw)",
                                            height: "max(14px, 1.6cqw)",
                                            fontSize: "max(8px, 0.9cqw)",
                                            fontWeight: 700,
                                            color: "#fff",
                                            // Outline around the initial so it stays readable on every player colour
                                            textShadow: "-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000",
                                            bgcolor: playerColor(player.piece),
                                            border: `max(1.5px, 0.18cqw) solid ${playerShade(player.piece)}`,
                                            boxShadow: "0 0.15cqw 0.35cqw rgba(0,0,0,0.55)",
                                        }}
                                    >
                                        {player.name.slice(0, 1)}
                                    </Avatar>
                                ))}
                            </Box>
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
                    caption="Active Finance News"
                />
            )}
            {lastStockTip && (
                <DrawnCard
                    rect={CARD_PLACES.stockTip.drawn}
                    card={board?.stockTips.find((card) => card.id === lastStockTip.card)}
                    fallback="Stock Tip"
                    caption={`Drawn by ${lastStockTip.drawnBy}${lastStockTip.held ? " · held" : ""}`}
                />
            )}
        </Box>
    )
}

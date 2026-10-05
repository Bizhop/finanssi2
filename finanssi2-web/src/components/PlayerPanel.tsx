import type { ReactNode } from "react"
import { Avatar, Box, Card, CardContent, Chip, Stack, Tooltip, Typography } from "@mui/material"
import { keyframes } from "@emotion/react"
import AccountBalanceOutlined from "@mui/icons-material/AccountBalanceOutlined"
import DirectionsCarOutlined from "@mui/icons-material/DirectionsCarOutlined"
import HomeWorkOutlined from "@mui/icons-material/HomeWorkOutlined"
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined"
import ReceiptLongOutlined from "@mui/icons-material/ReceiptLongOutlined"
import ShowChartOutlined from "@mui/icons-material/ShowChartOutlined"
import StyleOutlined from "@mui/icons-material/StyleOutlined"

import { CardFace, playerColor, playerShade } from "./GameBoard.tsx"
import { SquareDetails } from "./SquareDetails.tsx"
import type { Game, GameBoardData, GamePlayer } from "./gameApi.ts"

const pulse = keyframes`
    0%, 100% { box-shadow: 0 0 0 0 rgba(25, 118, 210, 0.55); }
    50% { box-shadow: 0 0 0 6px rgba(25, 118, 210, 0); }
`

/** An asset icon with a count; hovering lists the details, or shows `content` instead when given */
const Asset = ({ icon, count, title, details, content }: { icon: ReactNode; count?: ReactNode; title: string; details?: string[]; content?: ReactNode }) => (
    <Tooltip
        arrow
        slotProps={{ tooltip: { sx: { maxWidth: "none" } } }}
        title={
            <Stack spacing={0.5}>
                <Box sx={{ fontWeight: 700 }}>{title}</Box>
                {details?.map((line) => <Box key={line}>{line}</Box>)}
                {content}
            </Stack>
        }
    >
        <Stack direction="row" spacing={0.4} tabIndex={0} aria-label={title} sx={{ alignItems: "center", cursor: "default", "& svg": { fontSize: 18 } }}>
            {icon}
            {count != null && <Typography variant="body2">{count}</Typography>}
        </Stack>
    </Tooltip>
)

/** A player's box; the player in turn gets a bold outline and the player the game waits for an action chip */
export const PlayerPanel = ({ player, game, board, you, inTurn, expected }: {
    player: GamePlayer
    game: Game
    board: GameBoardData | null
    you: boolean
    inTurn: boolean
    expected: string | null
}) => {
    const color = playerColor(player.piece)
    const properties = game.state.properties.filter((property) => property.owner === player.uid)
    const shares = game.state.shares.filter((share) => share.owner === player.uid)
    const bonds = game.state.bonds.filter((bond) => bond.owner === player.uid)
    const shareName = (id: string) => {
        const share = board?.shares.find((item) => item.id === id)
        if (!share) return "Share"
        const group = share.group ? board?.groups.find((item) => item.id === share.group)?.name ?? share.group : "Rahasto-osake"
        return `${group} ${share.dividendPercent} % · value €${share.value.toLocaleString()} · dividend €${share.dividend.toLocaleString()} · bank buys back €${share.buyBack.toLocaleString()}`
    }
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
                            {player.name}
                            {you ? " (you)" : ""}
                            {player.out ? " · out" : ""}
                        </Typography>
                        <Stack direction="row" spacing={1.5} useFlexGap sx={{ flexWrap: "wrap", mt: 0.25, color: "text.secondary" }}>
                            <Asset icon={<PaymentsOutlined />} count={`€${player.cash.toLocaleString()}`} title="Cash" />
                            {player.loans > 0 && (
                                <Asset
                                    icon={<AccountBalanceOutlined />}
                                    count={player.loans}
                                    title={`${player.loans} bank loan${player.loans === 1 ? "" : "s"}`}
                                />
                            )}
                            {player.car && <Asset icon={<DirectionsCarOutlined />} title="Owns a car" />}
                            {properties.length > 0 && (
                                <Asset
                                    icon={<HomeWorkOutlined />}
                                    count={properties.length}
                                    title="Properties"
                                    content={
                                        <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: "wrap", maxWidth: 720 }}>
                                            {properties.map((property) => {
                                                const square = board?.squares.find((item) => item.square === property.square)
                                                return square && <SquareDetails key={property.square} square={square} board={board} game={game} />
                                            })}
                                        </Stack>
                                    }
                                />
                            )}
                            {shares.length > 0 && (
                                <Asset
                                    icon={<ShowChartOutlined />}
                                    count={shares.length}
                                    title="Shares"
                                    details={shares.map((share) => shareName(share.id))}
                                />
                            )}
                            {bonds.length > 0 && (
                                <Asset
                                    icon={<ReceiptLongOutlined />}
                                    count={bonds.length}
                                    title="Bonds"
                                    details={[`No. ${bonds.map((bond) => bond.number).join(", ")}`]}
                                />
                            )}
                            {player.heldStockTips.length > 0 && (
                                <Asset
                                    icon={<StyleOutlined />}
                                    count={player.heldStockTips.length}
                                    title="Held Stock Tips"
                                    content={
                                        <Stack direction="row" spacing={1}>
                                            {player.heldStockTips.map((id) => (
                                                <Box
                                                    key={id}
                                                    sx={{
                                                        width: 200,
                                                        minHeight: 272,
                                                        display: "flex",
                                                        fontSize: 13,
                                                        "& > *": { height: "auto" },
                                                        color: "initial",
                                                    }}
                                                >
                                                    <CardFace card={board?.stockTips.find((item) => item.id === id)} fallback="Stock Tip" />
                                                </Box>
                                            ))}
                                        </Stack>
                                    }
                                />
                            )}
                        </Stack>
                    </Box>
                    {expected && (
                        <Chip
                            size="small"
                            label={expected}
                            color="primary"
                            variant={expected.startsWith("Your") ? "filled" : "outlined"}
                            sx={expected.startsWith("Your") ? { fontWeight: 700, animation: `${pulse} 1.6s ease-in-out infinite` } : undefined}
                        />
                    )}
                </Stack>
            </CardContent>
        </Card>
    )
}

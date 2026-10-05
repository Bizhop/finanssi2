import { Avatar } from "@mui/material"

import type { GamePlayer } from "./gameApi.ts"

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

/** A player's piece: their colour with a contrasting rim and an outlined initial */
export const PlayerToken = ({ player, size }: { player: GamePlayer; size: string }) => (
    <Avatar
        src={player.photoUrl ?? undefined}
        alt={player.name}
        sx={{
            width: size,
            height: size,
            fontSize: `calc(${size} * 0.56)`,
            fontWeight: 700,
            color: "#fff",
            // Outline around the initial so it stays readable on every player colour
            textShadow: "-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000",
            bgcolor: playerColor(player.piece),
            border: `max(1.5px, calc(${size} * 0.11)) solid ${playerShade(player.piece)}`,
            boxShadow: `0 calc(${size} * 0.1) calc(${size} * 0.22) rgba(0,0,0,0.55)`,
        }}
    >
        {player.name.slice(0, 1)}
    </Avatar>
)

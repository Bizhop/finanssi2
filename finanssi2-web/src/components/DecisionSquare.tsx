import { Box, Stack, Typography } from "@mui/material"
import boardImage from "../assets/board.webp"
import { BOARD_ASPECT_RATIO, squareRect } from "./boardLayout.ts"
import type { GameBoardData } from "./gameApi.ts"

/** A crop of the actual board square with its number and readable name. */
export const DecisionSquare = ({ square, board }: { square: number; board: GameBoardData | null }) => {
    const rect = squareRect(square)
    return (
        <Stack spacing={0.5} sx={{ alignItems: "center", width: 160, maxWidth: "100%" }}>
            <Box
                sx={{
                    width: 64,
                    aspectRatio: rect.width * BOARD_ASPECT_RATIO / rect.height,
                    maxHeight: 88,
                    borderRadius: 0.5,
                    backgroundImage: `url(${boardImage})`,
                    backgroundSize: `${10000 / rect.width}% ${10000 / rect.height}%`,
                    backgroundPosition: `${rect.left / (100 - rect.width) * 100}% ${rect.top / (100 - rect.height) * 100}%`,
                    boxShadow: 1,
                }}
            />
            <Typography variant="caption" sx={{ textAlign: "center", lineHeight: 1.2 }}>
                {square} · {board?.squares.find((item) => item.square === square)?.name}
            </Typography>
        </Stack>
    )
}

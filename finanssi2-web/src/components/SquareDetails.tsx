import { Box, Stack } from "@mui/material"

import { TitleDeedCard } from "./cards.tsx"
import { PlayerToken } from "./PlayerToken.tsx"
import type { Game, GameBoardData, GameSquare } from "./gameApi.ts"

/** Whether the owner of a property also owns the rest of its group, which doubles its rent */
export const rentDoubled = (game: Game, board: GameBoardData | null, square: number) => {
    const owner = game.state.properties.find((item) => item.square === square)?.owner
    const group = board?.groups.find((item) => item.properties.includes(square))
    return owner != null && group != null && group.properties.every((number) => game.state.properties.find((item) => item.square === number)?.owner === owner)
}

/** The title deed card of a property square with its owner's token; nothing for other squares */
export const SquareDetails = ({ square, board, game }: { square: GameSquare; board: GameBoardData | null; game: Game }) => {
    const deed = board?.titleDeeds.find((item) => item.square === square.square)
    if (!deed) return null
    const property = game.state.properties.find((item) => item.square === deed.square)
    const owner = game.state.players.find((player) => player.playerId === property?.owner)
    const group = board?.groups.find((item) => item.id === deed.group)
    return (
        <Stack spacing={0.75} sx={{ p: 1.5, alignItems: "flex-start" }}>
            <Box sx={{ position: "relative" }}>
                <TitleDeedCard
                    deed={deed}
                    groupName={group?.name}
                    built={property?.built}
                    mortgaged={property?.mortgaged}
                    rentDoubled={rentDoubled(game, board, deed.square)}
                />
                {/* The owner's token sits on the card's corner; bank-owned squares show none */}
                {owner && (
                    <Box sx={{ position: "absolute", top: -10, left: -10 }}>
                        <PlayerToken player={owner} size="30px" />
                    </Box>
                )}
            </Box>
        </Stack>
    )
}

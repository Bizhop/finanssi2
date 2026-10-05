import type { ReactNode } from "react"
import { Box, Stack } from "@mui/material"

import { TitleDeedCard } from "./cards.tsx"
import type { Game, GameBoardData, GameSquare } from "./gameApi.ts"

/** A short dark label under a card in a peek */
export const PeekCaption = ({ children }: { children: ReactNode }) => (
    <Box sx={{ px: 1, py: 0.5, borderRadius: 1, backgroundColor: "rgba(33, 33, 33, 0.92)", color: "#fff", fontSize: 12, lineHeight: 1.35 }}>{children}</Box>
)

/** The title deed card of a property square with its owner, or the name and instruction of any other square */
export const SquareDetails = ({ square, board, game, here }: { square: GameSquare; board: GameBoardData | null; game: Game; here?: string[] }) => {
    const deed = board?.titleDeeds.find((item) => item.square === square.square)
    const herePart = here && here.length > 0 && <div>Here: {here.join(", ")}</div>
    if (!deed) {
        return (
            <Box sx={{ p: 1 }}>
                <PeekCaption>
                    <Box sx={{ fontWeight: 700 }}>{square.name}</Box>
                    {square.text && square.text !== square.name && <Box sx={{ maxWidth: 240 }}>{square.text}</Box>}
                    {herePart}
                </PeekCaption>
            </Box>
        )
    }
    const property = game.state.properties.find((item) => item.square === deed.square)
    const owner = game.state.players.find((player) => player.uid === property?.owner)
    const group = board?.groups.find((item) => item.id === deed.group)
    const completeGroup = owner != null && group != null &&
        group.properties.every((number) => game.state.properties.find((item) => item.square === number)?.owner === owner.uid)
    return (
        <Stack spacing={0.75} sx={{ p: 1, alignItems: "flex-start" }}>
            <TitleDeedCard deed={deed} groupName={group?.name} built={property?.built} mortgaged={property?.mortgaged} />
            <PeekCaption>
                <div>{owner ? `Owner: ${owner.name}` : property?.owner ? "Owner: a former player" : "Owned by the bank"}</div>
                {completeGroup && <div>Complete group: rent doubled</div>}
                {herePart}
            </PeekCaption>
        </Stack>
    )
}

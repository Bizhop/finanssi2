import { Box, Stack } from "@mui/material"

import type { Game, GameBoardData, GameSquare } from "./gameApi.ts"

const money = (value: number | null | undefined) => value == null ? "—" : `€${value.toLocaleString()}`

const cell = { px: 0.75, py: 0.1, textAlign: "right", whiteSpace: "nowrap" } as const

/**
 * Everything printed on a square's title deed plus its current state; squares without a deed show their name and instruction.
 * Values that don't apply in a state are a dash, as on the printed card.
 */
export const SquareDetails = ({ square, board, game }: { square: GameSquare; board: GameBoardData | null; game: Game }) => {
    const deed = board?.titleDeeds.find((item) => item.square === square.square)
    if (!deed) {
        return (
            <Stack spacing={0.25} sx={{ maxWidth: 240 }}>
                <Box sx={{ fontWeight: 700 }}>{square.name}</Box>
                {square.text && square.text !== square.name && <Box>{square.text}</Box>}
            </Stack>
        )
    }
    const property = game.state.properties.find((item) => item.square === deed.square)
    const owner = game.state.players.find((player) => player.uid === property?.owner)
    const group = board?.groups.find((item) => item.id === deed.group)
    const completeGroup = owner != null && group != null &&
        group.properties.every((number) => game.state.properties.find((item) => item.square === number)?.owner === owner.uid)
    const built = property?.built ?? false
    const rows = [
        { label: "Rent", values: deed.rent },
        { label: "Mortgage", values: deed.mortgage, none: "Cannot be mortgaged" },
        { label: "Redemption", values: deed.redemption, none: "—" },
        { label: "Bank buys back", values: deed.buyBack, none: "Not bought back" },
    ].filter((row) => row.values || row.none)
    return (
        <Stack spacing={0.5} sx={{ minWidth: 200 }}>
            <Box>
                <Box sx={{ fontWeight: 700 }}>{deed.name}</Box>
                <Box sx={{ opacity: 0.8 }}>{group?.name ?? "No group"}</Box>
            </Box>
            <Box>
                {owner ? `Owner: ${owner.name}` : property?.owner ? "Owner: a former player" : "Owned by the bank"}
                {built ? " · built" : ""}
                {property?.mortgaged ? " · mortgaged" : ""}
            </Box>
            <Box>Price {money(deed.price)}</Box>
            <Box>{deed.building ? `${deed.building.label} ${money(deed.building.price)}` : "Cannot be built on"}</Box>
            {deed.parkingFee != null && <Box>Parking fee {money(deed.parkingFee)} (car owners only)</Box>}
            <Box component="table" sx={{ borderCollapse: "collapse", mx: -0.75 }}>
                <thead>
                    <tr>
                        <Box component="th" />
                        <Box component="th" sx={{ ...cell, fontWeight: built ? 400 : 700 }}>Unbuilt</Box>
                        <Box component="th" sx={{ ...cell, fontWeight: built ? 700 : 400 }}>Built</Box>
                    </tr>
                </thead>
                <tbody>
                    {rows.map((row) => (
                        <tr key={row.label}>
                            <Box component="td" sx={{ ...cell, textAlign: "left" }}>{row.label}</Box>
                            {row.values
                                ? (
                                    <>
                                        <Box component="td" sx={cell}>{money(row.values.unbuilt)}</Box>
                                        <Box component="td" sx={cell}>{money(row.values.built)}</Box>
                                    </>
                                )
                                : <Box component="td" colSpan={2} sx={cell}>{row.none}</Box>}
                        </tr>
                    ))}
                </tbody>
            </Box>
            {completeGroup && <Box sx={{ fontWeight: 700 }}>Complete group: rent doubled</Box>}
        </Stack>
    )
}

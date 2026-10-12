import { Typography } from "@mui/material"

import type { Game } from "../../api/gameApi.ts"
import { formatEuro } from "../../ui/format.ts"

export const FinalStandings = ({ game }: { game: Game }) => (
    <>
        <Typography variant="h6">Final standings</Typography>
        {[...game.state.finalStandings].sort((a, b) => b.netWorth - a.netWorth).map((standing, index) => (
            <Typography key={standing.player}>
                {index + 1}. {game.state.players.find((p) => p.playerId === standing.player)?.name ?? standing.player} — {formatEuro(standing.netWorth)}{" "}
                net worth ({formatEuro(standing.cash)} cash)
            </Typography>
        ))}
    </>
)

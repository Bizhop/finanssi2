import { useTranslation } from "react-i18next"
import { Typography } from "@mui/material"

import type { Game } from "../../api/gameApi.ts"
import { formatEuro } from "../../ui/format.ts"

export const FinalStandings = ({ game }: { game: Game }) => {
    const { t } = useTranslation()
    return (
        <>
            <Typography variant="h6">{t("room.standings.title")}</Typography>
            {[...game.state.finalStandings].sort((a, b) => b.netWorth - a.netWorth).map((standing, index) => (
                <Typography key={standing.player}>
                    {t("room.standings.line", {
                        rank: index + 1,
                        name: game.state.players.find((p) => p.playerId === standing.player)?.name ?? standing.player,
                        netWorth: formatEuro(standing.netWorth),
                        cash: formatEuro(standing.cash),
                    })}
                </Typography>
            ))}
        </>
    )
}

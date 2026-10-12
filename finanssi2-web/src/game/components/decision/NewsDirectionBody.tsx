import { Button, Stack } from "@mui/material"
import { useTranslation } from "react-i18next"
import ArrowBack from "@mui/icons-material/ArrowBack"
import ArrowForward from "@mui/icons-material/ArrowForward"

import { newsDestination } from "../../labels/gameDecisions.ts"
import { DecisionSquare } from "../DecisionSquare.tsx"
import type { DecisionEnv } from "./shared.tsx"

/** Finance News movement: forward or backward, each showing the square it ends on */
export const NewsDirectionBody = ({ env }: { env: DecisionEnv }) => {
    const { t } = useTranslation()
    const { allowed, board, busy, position, send } = env
    return (
        <Stack direction="row" spacing={1}>
            {[false, true].map((forward) => {
                const square = newsDestination(position, forward, board)
                const name = board?.squares.find((item) => item.square === square)?.name ?? square
                const label = t(forward ? "decision.news.forward" : "decision.news.backward", { name, square })
                return (
                    <Button
                        key={String(forward)}
                        variant="outlined"
                        aria-label={label}
                        disabled={busy || !allowed.includes("ChooseNewsDirection")}
                        onClick={() => void send({ type: "ChooseNewsDirection", forward })}
                        sx={{ flex: 1, p: 1, minWidth: 0, textTransform: "none" }}
                    >
                        <Stack spacing={1} sx={{ alignItems: "center" }}>
                            {forward ? <ArrowForward /> : <ArrowBack />}
                            <DecisionSquare square={square} board={board} />
                        </Stack>
                    </Button>
                )
            })}
        </Stack>
    )
}

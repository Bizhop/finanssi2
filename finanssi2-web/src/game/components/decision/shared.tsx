import type { ReactNode } from "react"
import { Button, Stack, Tooltip, Typography } from "@mui/material"

import type { CommandOptions, Game, GameBoardData, PendingDecision } from "../../../api/gameApi.ts"
import { formatEuro } from "../../../ui/format.ts"
import type { decisionLabels } from "../../labels/gameDecisions.ts"
import { DecisionAsset } from "../DecisionAsset.tsx"
import { PlayerToken } from "../PlayerToken.tsx"

export type Pending = PendingDecision
/** One kind of pending decision */
export type PendingOf<T extends Pending["type"]> = Extract<Pending, { type: T }>

/** What every part of a decision dialog needs: the game, what the player may send, and whether a request is running */
export type DecisionEnv = {
    game: Game
    board: GameBoardData | null
    /** The command types the backend currently allows */
    allowed: string[]
    /** The concrete choices behind them, with current prices */
    options: CommandOptions
    send: (command: Record<string, unknown>) => Promise<void>
    busy: boolean
    labels: ReturnType<typeof decisionLabels>
    /** Cash of the player who decides */
    cash: number
    /** Where the deciding player's piece stands */
    position: number
}

/** An outlined command button; the label is also the tooltip and accessible name */
export const Choice = ({ label, children, icon, disabled, onClick }: {
    label: string
    children: ReactNode
    icon: ReactNode
    disabled?: boolean
    onClick: () => void
}) => (
    <Tooltip title={label}>
        <span>
            <Button
                size="small"
                variant="outlined"
                aria-label={label}
                disabled={disabled}
                onClick={onClick}
                startIcon={icon}
                sx={{ minHeight: 40, textTransform: "none", lineHeight: 1.25, textAlign: "left" }}
            >
                {children}
            </Button>
        </span>
    </Tooltip>
)

/** A choice that sends one command */
export const CommandChoice = ({ env, label, icon, command, disabled = false, children }: {
    env: DecisionEnv
    label: string
    icon: ReactNode
    command: Record<string, unknown>
    disabled?: boolean
    children: ReactNode
}) => (
    <Choice label={label} icon={icon} disabled={env.busy || disabled} onClick={() => void env.send(command)}>
        {children}
    </Choice>
)

/** A property, share, bond, loan or car card, optionally selectable or with actions underneath */
export const AssetChoice = ({ env, id, label, onSelect, disabled = false, selected = false, children }: {
    env: DecisionEnv
    id: string
    label?: string
    onSelect?: () => void
    disabled?: boolean
    selected?: boolean
    children?: ReactNode
}) => (
    <DecisionAsset asset={id} game={env.game} board={env.board} label={label} onSelect={onSelect} disabled={env.busy || disabled} selected={selected}>
        {children}
    </DecisionAsset>
)

/** A player's token and name; nothing for an unknown id */
export const PersonTag = ({ env, playerId }: { env: DecisionEnv; playerId: string }) => {
    const player = env.game.state.players.find((item) => item.playerId === playerId)
    return player && (
        <Tooltip title={player.name}>
            <Stack direction="row" spacing={0.75} sx={{ alignItems: "center", color: "text.primary" }}>
                <PlayerToken player={player} size="24px" />
                <Typography variant="caption">{player.name}</Typography>
            </Stack>
        </Tooltip>
    )
}

export const sellLabel = (amount: number) => `Sell to bank for ${formatEuro(amount)}`

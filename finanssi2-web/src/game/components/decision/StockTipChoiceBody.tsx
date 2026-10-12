import { useState } from "react"
import { Box, Stack, Typography } from "@mui/material"
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined"
import LocalFireDepartmentOutlined from "@mui/icons-material/LocalFireDepartmentOutlined"
import ArrowForward from "@mui/icons-material/ArrowForward"
import BlockOutlined from "@mui/icons-material/BlockOutlined"
import Check from "@mui/icons-material/Check"
import SwapHoriz from "@mui/icons-material/SwapHoriz"

import { formatEuro } from "../../../ui/format.ts"
import { ASSET_PICK_TIPS, STOCK_TIP, TRAVEL_FEE } from "../../rules/stockTipCards.ts"
import { GameBoard } from "../GameBoard.tsx"
import { AssetChoice, Choice, CommandChoice, type DecisionEnv, type Pending, PersonTag } from "./shared.tsx"

/** The options of a Stock Tip that asks the player to choose; each tip presents its options in its own way */
export const StockTipChoiceBody = ({ pending, env }: { pending: Pending; env: DecisionEnv }) => {
    const card = String(pending.card)
    const options = Array.isArray(pending.options) ? pending.options.map(String) : []
    if (card === STOCK_TIP.MOVE_TO_PROPERTY) return <MoveToProperty options={options} env={env} />
    if (card === STOCK_TIP.FIRE) return <Fire options={options} env={env} />
    return (
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", alignItems: "flex-start" }}>
            {options.map((option) => <PlainOption key={option} card={card} option={option} env={env} />)}
        </Stack>
    )
}

/** PV-17: pick a property on the board to move to */
const MoveToProperty = ({ options, env }: { options: string[]; env: DecisionEnv }) => {
    const { game, board, busy, allowed, labels } = env
    const [destination, setDestination] = useState<number | null>(null)
    return (
        <>
            <GameBoard
                game={game}
                board={board}
                turnStockTip={null}
                selectable={busy ? [] : options.map(Number)}
                onSelectSquare={setDestination}
                selectedSquare={destination}
            />
            {destination !== null && (
                <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                    <AssetChoice env={env} id={`P:${destination}`} />
                    <CommandChoice
                        env={env}
                        label={`Move to ${labels.property(String(destination))}`}
                        icon={<ArrowForward />}
                        command={{ type: "ChooseStockTipOption", option: String(destination) }}
                        disabled={!allowed.includes("ChooseStockTipOption")}
                    >
                        Move to {labels.property(String(destination))}
                    </CommandChoice>
                </Stack>
            )}
        </>
    )
}

/** PV-10: choose two buildings to burn, or confirm the only pair there is */
const Fire = ({ options, env }: { options: string[]; env: DecisionEnv }) => {
    const { labels, allowed } = env
    const [burned, setBurned] = useState<number[]>([])
    const singleFire = options.length === 1
    const fireSquares = [...new Set(options.flatMap((option) => option.split(",").map(Number)))]
    const fireOption = singleFire ? options[0] : options.find((option) => {
        const squares = option.split(",").map(Number)
        return squares.length === burned.length && squares.every((square) => burned.includes(square))
    })
    const toggle = (square: number) => setBurned((current) => current.includes(square) ? current.filter((value) => value !== square) : [...current, square])
    return (
        <>
            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
                {fireSquares.map((square) => (
                    <Box key={square}>
                        <AssetChoice
                            env={env}
                            id={`P:${square}`}
                            label={`Select ${labels.property(String(square))} to burn`}
                            onSelect={singleFire ? undefined : () => toggle(square)}
                            disabled={!singleFire && !burned.includes(square) && burned.length >= 2}
                            selected={singleFire || burned.includes(square)}
                        />
                    </Box>
                ))}
            </Stack>
            <CommandChoice
                env={env}
                label={singleFire ? "Confirm fire and roll for insurance" : "Burn selected buildings and roll for insurance"}
                icon={singleFire ? <Check /> : <LocalFireDepartmentOutlined />}
                command={{ type: "ChooseStockTipOption", option: fireOption }}
                disabled={!fireOption || !allowed.includes("ChooseStockTipOption")}
            >
                {singleFire ? "Continue" : `Burn buildings (${burned.length}/2)`}
            </CommandChoice>
        </>
    )
}

/** Every other tip: free bond, asset picks, share swaps and the plain options (pay, bail, jail, stay) */
const PlainOption = ({ card, option, env }: { card: string; option: string; env: DecisionEnv }) => {
    const { allowed, busy, cash, labels, send } = env
    const label = labels.option(card, option)
    const disabled = !allowed.includes("ChooseStockTipOption")
    const choose = () => void send({ type: "ChooseStockTipOption", option })
    if (card === STOCK_TIP.FREE_BOND) {
        const parts = option.split(":")
        return (
            <Stack spacing={0.75} sx={{ alignItems: "center" }}>
                {parts[0] === "P" && (
                    <Stack direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
                        <PersonTag env={env} playerId={parts[1]} />
                        <ArrowForward fontSize="small" />
                    </Stack>
                )}
                <AssetChoice env={env} id={`B:${parts.at(-1)}`} label={label} onSelect={choose} disabled={disabled} />
            </Stack>
        )
    }
    if (ASSET_PICK_TIPS.includes(card)) {
        const id = card === STOCK_TIP.SELL_AT_NOMINAL ? `P:${option}` : option
        return (
            <Box>
                <AssetChoice env={env} id={id} label={label} onSelect={choose} disabled={disabled}>
                    {card === STOCK_TIP.SELL_AT_NOMINAL && <Typography variant="caption">{label.match(/€[\d,]+/)?.[0]}</Typography>}
                </AssetChoice>
            </Box>
        )
    }
    if (card === STOCK_TIP.SHARE_SWAP && option !== "PASS") {
        const [mine, other, theirs] = option.split("|")
        return (
            <Stack spacing={0.75} sx={{ alignItems: "center" }}>
                <PersonTag env={env} playerId={other} />
                <Stack direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
                    <AssetChoice env={env} id={`S:${mine}`} />
                    <SwapHoriz fontSize="small" />
                    <AssetChoice env={env} id={`S:${theirs}`} />
                </Stack>
                <Choice label={label} icon={<SwapHoriz />} disabled={busy || disabled} onClick={choose}>Swap</Choice>
            </Stack>
        )
    }
    const icon = option === "PAY" ? <ArrowForward /> : option === "BAIL" ? <PaymentsOutlined /> : option === "JAIL" ? <BlockOutlined /> : <Check />
    const text = option === "PAY"
        ? formatEuro(TRAVEL_FEE)
        : option === "BAIL"
        ? "€30,000 bail"
        : option === "JAIL"
        ? "Jail"
        : card === STOCK_TIP.SHARE_SWAP
        ? "Keep shares"
        : "Stay"
    return (
        <Choice label={label} icon={icon} disabled={busy || disabled || (card === STOCK_TIP.TRAVEL && option === "PAY" && cash < TRAVEL_FEE)} onClick={choose}>
            {text}
        </Choice>
    )
}

import { Box, IconButton, Stack, Tooltip } from "@mui/material"
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined"
import AccountBalanceOutlined from "@mui/icons-material/AccountBalanceOutlined"
import BlockOutlined from "@mui/icons-material/BlockOutlined"
import Check from "@mui/icons-material/Check"

import { formatEuro } from "../../../ui/format.ts"
import { propertySaleProceeds, shareSaleProceeds } from "../../labels/gameDecisions.ts"
import { AssetChoice, CommandChoice, type DecisionEnv, type Pending, sellLabel } from "./shared.tsx"

/** A payment the player cannot yet cover: the loans, car, mortgages and sales that raise cash, then pay or go bankrupt */
export const RaiseFundsBody = ({ pending, env }: { pending: Pending; env: DecisionEnv }) => {
    const { allowed, board, busy, game, labels, send } = env
    const activeNews = game.state.activeFinanceNews
    const properties = game.state.properties.filter((property) => {
        const deed = board?.titleDeeds.find((item) => item.square === property.square)
        const state = property.built ? "built" : "unbuilt"
        return property.owner === pending.player && !property.mortgaged &&
            ((allowed.includes("Mortgage") && deed?.mortgage?.[state] != null) || (allowed.includes("SellBackProperty") && deed?.buyBack?.[state] != null))
    })
    return (
        <>
            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", alignItems: "flex-start" }}>
                {allowed.includes("TakeLoan") && (
                    <AssetChoice env={env} id="LOAN">
                        <Tooltip title="Take a bank loan for 50,000€">
                            <IconButton size="small" aria-label="Take a €50,000 bank loan" disabled={busy} onClick={() => void send({ type: "TakeLoan" })}>
                                <AccountBalanceOutlined fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    </AssetChoice>
                )}
                {allowed.includes("SellCar") && (
                    <AssetChoice env={env} id="CAR">
                        <Tooltip title={sellLabel(25000)}>
                            <IconButton size="small" aria-label="Sell car" disabled={busy} onClick={() => void send({ type: "SellCar" })}>
                                <PaymentsOutlined fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    </AssetChoice>
                )}
                {properties.map((property) => {
                    const deed = board!.titleDeeds.find((item) => item.square === property.square)!
                    const state = property.built ? "built" : "unbuilt"
                    return (
                        <Box key={property.square}>
                            <AssetChoice env={env} id={`P:${property.square}`}>
                                <Stack direction="row">
                                    {allowed.includes("Mortgage") && deed.mortgage?.[state] != null && (
                                        <Tooltip title={`Mortgage for ${formatEuro(deed.mortgage[state]!)}`}>
                                            <IconButton
                                                size="small"
                                                aria-label={`Mortgage ${deed.name}`}
                                                disabled={busy}
                                                onClick={() =>
                                                    void send({ type: "Mortgage", square: property.square })}
                                            >
                                                <AccountBalanceOutlined fontSize="small" />
                                            </IconButton>
                                        </Tooltip>
                                    )}
                                    {allowed.includes("SellBackProperty") && deed.buyBack?.[state] != null && (
                                        <Tooltip title={sellLabel(propertySaleProceeds(deed.buyBack[state]!, property.built, activeNews))}>
                                            <IconButton
                                                size="small"
                                                aria-label={`Sell ${deed.name}`}
                                                disabled={busy}
                                                onClick={() =>
                                                    void send({ type: "SellBackProperty", square: property.square })}
                                            >
                                                <PaymentsOutlined fontSize="small" />
                                            </IconButton>
                                        </Tooltip>
                                    )}
                                </Stack>
                            </AssetChoice>
                        </Box>
                    )
                })}
                {allowed.includes("SellBackShare") && game.state.shares.filter((share) => share.owner === pending.player).map((share) => {
                    const printed = board?.shares.find((item) => item.id === share.id)
                    return (
                        <Box key={share.id}>
                            <AssetChoice env={env} id={`S:${share.id}`}>
                                <Tooltip title={printed ? sellLabel(shareSaleProceeds(printed.buyBack, activeNews)) : "Sell to bank"}>
                                    <IconButton
                                        size="small"
                                        aria-label={`Sell ${labels.share(share.id)}`}
                                        disabled={busy}
                                        onClick={() => void send({ type: "SellBackShare", share: share.id })}
                                    >
                                        <PaymentsOutlined fontSize="small" />
                                    </IconButton>
                                </Tooltip>
                            </AssetChoice>
                        </Box>
                    )
                })}
            </Stack>
            <Stack direction="row" spacing={1}>
                {allowed.includes("Pay") && <CommandChoice env={env} label="Settle payment" icon={<Check />} command={{ type: "Pay" }}>Pay</CommandChoice>}
                {allowed.includes("DeclareBankruptcy") && (
                    <CommandChoice env={env} label="Declare bankruptcy" icon={<BlockOutlined />} command={{ type: "DeclareBankruptcy" }}>
                        Bankruptcy
                    </CommandChoice>
                )}
            </Stack>
        </>
    )
}

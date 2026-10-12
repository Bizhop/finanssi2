import { Box, IconButton, Stack, Tooltip } from "@mui/material"
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined"
import AccountBalanceOutlined from "@mui/icons-material/AccountBalanceOutlined"
import BlockOutlined from "@mui/icons-material/BlockOutlined"
import Check from "@mui/icons-material/Check"

import { formatEuro } from "../../../ui/format.ts"
import { AssetChoice, CommandChoice, type DecisionEnv, sellLabel } from "./shared.tsx"

/** A payment the player cannot yet cover: the loans, car, mortgages and sales that raise cash, then pay or go bankrupt */
export const RaiseFundsBody = ({ env }: { env: DecisionEnv }) => {
    const { allowed, board, busy, game, labels, options, send } = env
    // What can be raised, and for how much, comes from the backend's options
    const properties = game.state.properties.filter((property) =>
        options.mortgage.some((offer) => offer.square === property.square) || options.sellBackProperty.some((offer) => offer.square === property.square)
    )
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
                    const deed = board?.titleDeeds.find((item) => item.square === property.square)
                    const mortgage = options.mortgage.find((offer) => offer.square === property.square)
                    const sell = options.sellBackProperty.find((offer) => offer.square === property.square)
                    return (
                        <Box key={property.square}>
                            <AssetChoice env={env} id={`P:${property.square}`}>
                                <Stack direction="row">
                                    {mortgage && (
                                        <Tooltip title={`Mortgage for ${formatEuro(mortgage.amount)}`}>
                                            <IconButton
                                                size="small"
                                                aria-label={`Mortgage ${deed?.name ?? property.square}`}
                                                disabled={busy}
                                                onClick={() => void send({ type: "Mortgage", square: property.square })}
                                            >
                                                <AccountBalanceOutlined fontSize="small" />
                                            </IconButton>
                                        </Tooltip>
                                    )}
                                    {sell && (
                                        <Tooltip title={sellLabel(sell.amount)}>
                                            <IconButton
                                                size="small"
                                                aria-label={`Sell ${deed?.name ?? property.square}`}
                                                disabled={busy}
                                                onClick={() => void send({ type: "SellBackProperty", square: property.square })}
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
                {options.sellBackShare.map((offer) => (
                    <Box key={offer.share}>
                        <AssetChoice env={env} id={`S:${offer.share}`}>
                            <Tooltip title={sellLabel(offer.amount)}>
                                <IconButton
                                    size="small"
                                    aria-label={`Sell ${labels.share(offer.share)}`}
                                    disabled={busy}
                                    onClick={() => void send({ type: "SellBackShare", share: offer.share })}
                                >
                                    <PaymentsOutlined fontSize="small" />
                                </IconButton>
                            </Tooltip>
                        </AssetChoice>
                    </Box>
                ))}
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

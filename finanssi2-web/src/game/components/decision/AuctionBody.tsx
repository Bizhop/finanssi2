import { useState } from "react"
import { IconButton, InputAdornment, Stack, TextField } from "@mui/material"
import GavelOutlined from "@mui/icons-material/GavelOutlined"
import Add from "@mui/icons-material/Add"
import Remove from "@mui/icons-material/Remove"

import { nextAuctionBid, validAuctionBid } from "../../labels/gameDecisions.ts"
import { AssetChoice, CommandChoice, type DecisionEnv, type PendingOf, PersonTag } from "./shared.tsx"

/** A sealed bond or asset auction: the lot, and the player's own sealed bid (0 passes) */
export const AuctionBody = ({ pending, env, minimum }: { pending: PendingOf<"BondAuction" | "AssetAuction">; env: DecisionEnv; minimum: number }) => {
    const { allowed, busy, cash } = env
    const [bid, setBid] = useState("0")
    const bondAuction = pending.type === "BondAuction"
    const command = bondAuction ? "BidBond" : "BidAsset"
    const stepBid = (direction: 1 | -1) => {
        const next = nextAuctionBid(bid, direction, minimum)
        if (next <= cash) setBid(String(next))
    }
    return (
        <>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
                <AssetChoice env={env} id={pending.type === "AssetAuction" ? pending.asset : "B:?"} />
                {pending.type === "AssetAuction" && <PersonTag env={env} playerId={pending.seller} />}
            </Stack>
            <Stack direction="row" spacing={1} sx={{ alignItems: "flex-start" }}>
                <TextField
                    size="small"
                    label="Your bid (€)"
                    type="number"
                    value={bid}
                    onChange={(event) => setBid(event.target.value)}
                    onKeyDown={(event) => {
                        if (event.key === "ArrowUp" || event.key === "ArrowDown") {
                            event.preventDefault()
                            stepBid(event.key === "ArrowUp" ? 1 : -1)
                        }
                    }}
                    error={bid !== "" && !validAuctionBid(bid, cash, minimum)}
                    helperText="€500 increments · 0 to pass"
                    slotProps={{
                        htmlInput: { min: 0, max: cash, step: 500 },
                        input: {
                            endAdornment: (
                                <InputAdornment position="end">
                                    <IconButton size="small" aria-label="Lower bid" disabled={busy || Number(bid) <= 0} onClick={() => stepBid(-1)}>
                                        <Remove fontSize="small" />
                                    </IconButton>
                                    <IconButton
                                        size="small"
                                        aria-label="Raise bid"
                                        disabled={busy || nextAuctionBid(bid, 1, minimum) > cash}
                                        onClick={() => stepBid(1)}
                                    >
                                        <Add fontSize="small" />
                                    </IconButton>
                                </InputAdornment>
                            ),
                        },
                    }}
                    sx={{
                        flex: 1,
                        minWidth: 0,
                        "& input::-webkit-inner-spin-button, & input::-webkit-outer-spin-button": { WebkitAppearance: "none", m: 0 },
                        "& input": { MozAppearance: "textfield" },
                    }}
                />
                <CommandChoice
                    env={env}
                    label="Submit sealed bid"
                    icon={<GavelOutlined />}
                    command={{ type: command, amount: Number(bid) }}
                    disabled={!allowed.includes(command) || !validAuctionBid(bid, cash, minimum)}
                >
                    {Number(bid) === 0 && bid !== "" ? "Pass" : "Bid"}
                </CommandChoice>
            </Stack>
        </>
    )
}

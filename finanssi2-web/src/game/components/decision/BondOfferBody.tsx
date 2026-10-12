import { Box, Stack } from "@mui/material"
import BlockOutlined from "@mui/icons-material/BlockOutlined"

import { formatEuro } from "../../../ui/format.ts"
import { AssetChoice, CommandChoice, type DecisionEnv } from "./shared.tsx"

/** Buy one of the bonds still with the bank, or pass */
export const BondOfferBody = ({ env }: { env: DecisionEnv }) => {
    const { allowed, options, send } = env
    return (
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", alignItems: "center" }}>
            {allowed.includes("BuyBond") && options.buyBond.map((number) => (
                <Box key={number}>
                    <AssetChoice
                        env={env}
                        id={`B:${number}`}
                        label={`Buy bond ${number} · ${formatEuro(options.bondPrice)}`}
                        onSelect={() => void send({ type: "BuyBond", number })}
                    />
                </Box>
            ))}
            <CommandChoice env={env} label="Pass on buying a bond" icon={<BlockOutlined />} command={{ type: "Pass" }} disabled={!allowed.includes("Pass")}>
                Pass
            </CommandChoice>
        </Stack>
    )
}

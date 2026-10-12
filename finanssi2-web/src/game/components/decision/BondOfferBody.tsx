import { Box, Stack } from "@mui/material"
import BlockOutlined from "@mui/icons-material/BlockOutlined"

import { AssetChoice, CommandChoice, type DecisionEnv } from "./shared.tsx"

/** Buy one of the bonds still with the bank, or pass */
export const BondOfferBody = ({ env }: { env: DecisionEnv }) => {
    const { allowed, game, send } = env
    return (
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", alignItems: "center" }}>
            {allowed.includes("BuyBond") && game.state.bonds.filter((bond) => !bond.owner).map((bond) => (
                <Box key={bond.number}>
                    <AssetChoice
                        env={env}
                        id={`B:${bond.number}`}
                        label={`Buy bond ${bond.number} · €500`}
                        onSelect={() => void send({ type: "BuyBond", number: bond.number })}
                    />
                </Box>
            ))}
            <CommandChoice env={env} label="Pass on buying a bond" icon={<BlockOutlined />} command={{ type: "Pass" }} disabled={!allowed.includes("Pass")}>
                Pass
            </CommandChoice>
        </Stack>
    )
}

import { Box, Stack } from "@mui/material"
import { useTranslation } from "react-i18next"
import BlockOutlined from "@mui/icons-material/BlockOutlined"

import { formatEuro } from "../../../ui/format.ts"
import { AssetChoice, CommandChoice, type DecisionEnv } from "./shared.tsx"

/** Buy one of the bonds still with the bank, or pass */
export const BondOfferBody = ({ env }: { env: DecisionEnv }) => {
    const { t } = useTranslation()
    const { allowed, options, send } = env
    return (
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", alignItems: "center" }}>
            {allowed.includes("BuyBond") && options.buyBond.map((number) => (
                <Box key={number}>
                    <AssetChoice
                        env={env}
                        id={`B:${number}`}
                        label={t("decision.bondOffer.buy", { number, price: formatEuro(options.bondPrice) })}
                        onSelect={() => void send({ type: "BuyBond", number })}
                    />
                </Box>
            ))}
            <CommandChoice
                env={env}
                label={t("decision.bondOffer.passLabel")}
                icon={<BlockOutlined />}
                command={{ type: "Pass" }}
                disabled={!allowed.includes("Pass")}
            >
                {t("decision.bondOffer.pass")}
            </CommandChoice>
        </Stack>
    )
}

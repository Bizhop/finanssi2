import { useTranslation } from "react-i18next"
import { FormControl, InputLabel, MenuItem, Select, Typography } from "@mui/material"

/** The game's rule options: the recommended ones are the defaults, the original printed rules stay available for legacy play */
export const RuleSettings = ({ loanLimit, setLoanLimit, minimumBid, setMinimumBid, meetingRule, setMeetingRule }: {
    loanLimit: string
    setLoanLimit: (value: string) => void
    minimumBid: string
    setMinimumBid: (value: string) => void
    meetingRule: string
    setMeetingRule: (value: string) => void
}) => {
    const { t } = useTranslation()
    return (
        <>
            <FormControl fullWidth>
                <InputLabel id="loan-limit-label">{t("rules.loanLimit")}</InputLabel>
                <Select labelId="loan-limit-label" label={t("rules.loanLimit")} value={loanLimit} onChange={(event) => setLoanLimit(event.target.value)}>
                    <MenuItem value="UNLIMITED">{t("rules.loanLimit.UNLIMITED")} {t("rules.recommended")}</MenuItem>
                    <MenuItem value="OFFICIAL">{t("rules.loanLimit.OFFICIAL")} {t("rules.original")}</MenuItem>
                </Select>
            </FormControl>
            <FormControl fullWidth>
                <InputLabel id="sale-minimum-label">{t("rules.saleMinimum")}</InputLabel>
                <Select labelId="sale-minimum-label" label={t("rules.saleMinimum")} value={minimumBid} onChange={(event) => setMinimumBid(event.target.value)}>
                    <MenuItem value="HALF_NOMINAL_PRICE">{t("rules.saleMinimum.HALF_NOMINAL_PRICE")} {t("rules.recommended")}</MenuItem>
                    <MenuItem value="NONE">{t("rules.saleMinimum.NONE")} {t("rules.original")}</MenuItem>
                </Select>
            </FormControl>
            <FormControl fullWidth>
                <InputLabel id="meeting-rule-label">{t("rules.meeting")}</InputLabel>
                <Select labelId="meeting-rule-label" label={t("rules.meeting")} value={meetingRule} onChange={(event) => setMeetingRule(event.target.value)}>
                    <MenuItem value="ALL_ASSETS_BOUGHT">{t("rules.meeting.ALL_ASSETS_BOUGHT")} {t("rules.recommended")}</MenuItem>
                    <MenuItem value="ANY_OTHER_OWNER">{t("rules.meeting.ANY_OTHER_OWNER")} {t("rules.original")}</MenuItem>
                </Select>
            </FormControl>
            <Typography variant="caption" color="text.secondary">
                {t("rules.help")}
            </Typography>
        </>
    )
}

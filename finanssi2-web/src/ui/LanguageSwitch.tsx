import { ToggleButton, ToggleButtonGroup } from "@mui/material"
import { useTranslation } from "react-i18next"

import { type Language, LANGUAGES, setLanguage } from "../i18n/index.ts"

export const LanguageSwitch = () => {
    const { t, i18n } = useTranslation()
    return (
        <ToggleButtonGroup
            exclusive
            size="small"
            value={i18n.language}
            aria-label={t("nav.language")}
            onChange={(_event, language: Language | null) => {
                if (language) void setLanguage(language)
            }}
        >
            {LANGUAGES.map((language) => <ToggleButton key={language} value={language} sx={{ px: 1.5 }}>{language.toUpperCase()}</ToggleButton>)}
        </ToggleButtonGroup>
    )
}

import type { JSX } from "react"
import { Box, Grid, IconButton, ToggleButton, ToggleButtonGroup, Tooltip } from "@mui/material"
import { useTranslation } from "react-i18next"
import { NavLink } from "react-router"
import HomeIcon from "@mui/icons-material/Home"
import LogoutIcon from "@mui/icons-material/Logout"
import CasinoIcon from "@mui/icons-material/Casino"
import AccountCircleIcon from "@mui/icons-material/AccountCircle"

import { auth } from "../auth/firebase.ts"
import { useCurrentUser } from "../auth/CurrentUserContext.tsx"
import { currentLanguage, type Language, LANGUAGES, setLanguage } from "../i18n/index.ts"

type MyNavLinkProps = {
    to: string
    label: string
    icon: JSX.Element
}

const MyNavLink = ({ to, label, icon }: MyNavLinkProps) => (
    <Grid size={1} sx={{ textAlign: "center" }}>
        <Tooltip title={label}>
            <IconButton component={NavLink} to={to} size="small" aria-label={label}>
                {icon}
            </IconButton>
        </Tooltip>
    </Grid>
)

const LanguageSwitch = () => {
    const { t } = useTranslation()
    return (
        <ToggleButtonGroup
            exclusive
            size="small"
            value={currentLanguage()}
            aria-label={t("nav.language")}
            onChange={(_event, language: Language | null) => {
                if (language) void setLanguage(language)
            }}
        >
            {LANGUAGES.map((language) => <ToggleButton key={language} value={language} sx={{ px: 1, py: 0 }}>{language.toUpperCase()}</ToggleButton>)}
        </ToggleButtonGroup>
    )
}

export const Header = () => {
    const { t } = useTranslation()
    const { user, status } = useCurrentUser()
    const logout = () => auth.signOut()

    return (
        <Box>
            <Grid container spacing={1}>
                <MyNavLink to="/" label={t("nav.frontPage")} icon={<HomeIcon />} />
                {user && status === "ready"
                    ? (
                        <>
                            <MyNavLink to="/games" label={t("nav.games")} icon={<CasinoIcon />} />
                            <MyNavLink to="/account" label={t("nav.account")} icon={<AccountCircleIcon />} />
                        </>
                    )
                    : null}
                <Grid offset="auto" sx={{ display: "flex", alignItems: "center" }}>
                    <LanguageSwitch />
                </Grid>
                {user && status === "ready" && (
                    <Grid size={1}>
                        <Tooltip title={t("nav.logOut")}>
                            <IconButton onClick={logout} color="error" aria-label={t("nav.logOut")}>
                                <LogoutIcon />
                            </IconButton>
                        </Tooltip>
                    </Grid>
                )}
            </Grid>
        </Box>
    )
}

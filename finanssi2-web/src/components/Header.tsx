import React, { useEffect } from "react"
import { Box, Grid, IconButton, Tooltip } from "@mui/material"
import { Navigate, NavLink } from "react-router"
import HomeIcon from "@mui/icons-material/Home"
import LogoutIcon from "@mui/icons-material/Logout"
import CasinoIcon from "@mui/icons-material/Casino"

import { auth } from "./firebase.ts"
import { useCurrentUser } from "./CurrentUserContext.tsx"

type TMyNavLinkProps = {
    to: string
    label: string
    icon: React.JSX.Element
}

const MyNavLink = ({ to, label, icon }: TMyNavLinkProps) => (
    <Grid size={1} sx={{ textAlign: "center" }}>
        <NavLink to={to}>
            <Tooltip title={label}>
                <IconButton size="small">
                    {icon}
                </IconButton>
            </Tooltip>
        </NavLink>
    </Grid>
)

const Header = () => {
    const { user, setUser } = useCurrentUser()
    const logout = () => auth.signOut()

    useEffect(() => {
        const unsubscribe = auth.onAuthStateChanged((user) => {
            setUser(user)
        })

        return () => unsubscribe()
    }, [])

    return (
        <Box>
            <Grid container spacing={1}>
                <MyNavLink to="/" label="Front Page" icon={<HomeIcon />} />
                {user
                    ? (
                        <>
                            <MyNavLink to="/games" label="Games" icon={<CasinoIcon />} />
                            <Grid size={1} offset="auto">
                                <Tooltip title="Log out">
                                    <IconButton onClick={logout} color="error">
                                        <LogoutIcon />
                                    </IconButton>
                                </Tooltip>
                            </Grid>
                        </>
                    )
                    : <Navigate to="/" />}
            </Grid>
        </Box>
    )
}

export default Header

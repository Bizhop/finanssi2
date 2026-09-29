import React, { useEffect } from "react"
import { Box, Grid2, IconButton, Tooltip } from "@mui/material"
import { Navigate, NavLink } from "react-router-dom"
import HomeIcon from "@mui/icons-material/Home"
import LogoutIcon from "@mui/icons-material/Logout"
import CasinoIcon from "@mui/icons-material/Casino"

import { auth } from "./firebase"
import { useCurrentUser } from "./CurrentUserContext"

type TMyNavLinkProps = {
    to: string,
    label: string,
    icon: JSX.Element
}

const MyNavLink = ({ to, label, icon }: TMyNavLinkProps) =>
    <Grid2 size={1} textAlign="center">
        <NavLink to={to}>
            <Tooltip title={label}>
                <IconButton size="small">
                    {icon}
                </IconButton>
            </Tooltip>
        </NavLink>
    </Grid2>

const Header = () => {
    const { user, setUser } = useCurrentUser()
    const logout = () => auth.signOut()

    useEffect(() => {
        const unsubscribe = auth.onAuthStateChanged(user => {
            setUser(user)
        })

        return () => unsubscribe()
    }, [])

    return <Box>
        <Grid2 container spacing={1}>
            <MyNavLink to="/" label="Front Page" icon={<HomeIcon />} />
            {user ? <>
                <MyNavLink to="/games" label="Games" icon={<CasinoIcon />} />
                <Grid2 size={1} offset="auto">
                    <Tooltip title="Log out">
                        <IconButton onClick={logout} color="error">
                            <LogoutIcon />
                        </IconButton>
                    </Tooltip>
                </Grid2>
            </> :
                <Navigate to="/" />
            }
        </Grid2>
    </Box>
}

export default Header

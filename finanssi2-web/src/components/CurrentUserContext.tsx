import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react"
import { User } from "firebase/auth"
import { gameApi } from "./gameApi.ts"

const CurrentUserContext = createContext<UserState | null>(null)

type CurrentUserProviderProps = {
    children: React.ReactNode
}

type UserState = {
    user: User | null
    debugMode: boolean
    capabilitiesReady: boolean
    refreshCapabilities: () => Promise<void>
    clearDebugAccess: () => void
    setUser: React.Dispatch<React.SetStateAction<User | null>>
}

export const CurrentUserProvider = ({ children }: CurrentUserProviderProps) => {
    const [user, setUser] = useState<User | null>(null)

    const userRef = useRef(user)
    userRef.current = user
    const [capability, setCapability] = useState<{ user: User; debugMode: boolean } | null>(null)
    const refreshCapabilities = useCallback(async () => {
        if (!user) return
        try {
            const result = await gameApi<{ debugMode: boolean }>(user, "/api/me/capabilities")
            if (userRef.current === user) setCapability({ user, debugMode: result.debugMode })
        } catch {
            if (userRef.current === user) setCapability({ user, debugMode: false })
        }
    }, [user])
    useEffect(() => {
        setCapability(null)
        void refreshCapabilities()
    }, [refreshCapabilities])
    const debugMode = capability?.user === user && capability.debugMode
    const clearDebugAccess = useCallback(() => setCapability(user ? { user, debugMode: false } : null), [user])

    return (
        <CurrentUserContext.Provider
            value={{ user, setUser, debugMode: Boolean(debugMode), capabilitiesReady: capability?.user === user, refreshCapabilities, clearDebugAccess }}
        >
            {children}
        </CurrentUserContext.Provider>
    )
}

export const useCurrentUser = () => {
    const context = useContext(CurrentUserContext)
    if (!context) {
        throw new Error("useCurrentUser must be used within a CurrentUserProvider")
    }
    return context
}

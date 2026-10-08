import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react"
import { onIdTokenChanged, User } from "firebase/auth"
import { auth } from "./firebase.ts"
import { gameApi } from "./gameApi.ts"

export type UserProfile = {
    id: string
    email: string
    displayName: string
    avatar: string | null
    avatarSource: "custom" | "provider" | null
    version: number
    capabilities: { debugMode: boolean }
}
export type AuthStatus = "initializing" | "signedOut" | "verificationRequired" | "profileLoading" | "ready" | "recoverableError"

const CurrentUserContext = createContext<UserState | null>(null)
type CurrentUserProviderProps = { children: React.ReactNode }
type UserState = {
    user: User | null
    profile: UserProfile | null
    status: AuthStatus
    error: string | null
    debugMode: boolean
    capabilitiesReady: boolean
    refreshProfile: () => Promise<void>
    refreshCapabilities: () => Promise<void>
    clearDebugAccess: () => void
    setUser: React.Dispatch<React.SetStateAction<User | null>>
}

export const CurrentUserProvider = ({ children }: CurrentUserProviderProps) => {
    const [user, setUser] = useState<User | null>(null)
    const [profile, setProfile] = useState<UserProfile | null>(null)
    const [status, setStatus] = useState<AuthStatus>("initializing")
    const [error, setError] = useState<string | null>(null)
    const userRef = useRef<User | null>(null)
    const profileRef = useRef<UserProfile | null>(null)
    const requestRef = useRef(0)
    userRef.current = user
    profileRef.current = profile

    const refreshProfile = useCallback(async () => {
        const current = userRef.current
        if (!current) return
        const request = ++requestRef.current
        if (!profileRef.current) setStatus("profileLoading")
        setError(null)
        try {
            const result = await gameApi<UserProfile>(current, "/api/me")
            if (userRef.current?.uid !== current.uid || request !== requestRef.current) return
            setProfile(result)
            setStatus("ready")
        } catch (reason) {
            if (userRef.current?.uid !== current.uid || request !== requestRef.current) return
            setError(reason instanceof Error ? reason.message : "Unable to load your account")
            if (!profileRef.current) setStatus("recoverableError")
        }
    }, [])

    useEffect(() =>
        onIdTokenChanged(auth, async (nextUser) => {
            const sameAccount = userRef.current?.uid === nextUser?.uid
            const request = ++requestRef.current
            userRef.current = nextUser
            setUser(nextUser)
            setError(null)
            if (!nextUser) {
                setProfile(null)
                setStatus("signedOut")
                return
            }
            if (!sameAccount) setProfile(null)
            if (!profileRef.current || !sameAccount) setStatus("profileLoading")
            try {
                if (request !== requestRef.current || userRef.current?.uid !== nextUser.uid) return
                if (!nextUser.emailVerified) {
                    setStatus("verificationRequired")
                    return
                }
                const result = await gameApi<UserProfile>(nextUser, "/api/me")
                if (request !== requestRef.current || userRef.current?.uid !== nextUser.uid) return
                setProfile(result)
                setStatus("ready")
            } catch (reason) {
                if (request !== requestRef.current || userRef.current?.uid !== nextUser.uid) return
                setError(reason instanceof Error ? reason.message : "Unable to load your account")
                if (!profileRef.current) setStatus("recoverableError")
            }
        }), [])

    const refreshCapabilities = refreshProfile
    const clearDebugAccess = useCallback(() => setProfile((current) => current ? { ...current, capabilities: { debugMode: false } } : null), [])
    return (
        <CurrentUserContext.Provider
            value={{
                user,
                profile,
                status,
                error,
                setUser,
                debugMode: Boolean(profile?.capabilities.debugMode),
                capabilitiesReady: status === "ready",
                refreshProfile,
                refreshCapabilities,
                clearDebugAccess,
            }}
        >
            {children}
        </CurrentUserContext.Provider>
    )
}

export const useCurrentUser = () => {
    const context = useContext(CurrentUserContext)
    if (!context) throw new Error("useCurrentUser must be used within a CurrentUserProvider")
    return context
}

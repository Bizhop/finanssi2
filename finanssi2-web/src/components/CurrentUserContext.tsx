import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react"
import { onIdTokenChanged, reload, type User } from "firebase/auth"
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

export const CurrentUserContext = createContext<UserState | null>(null)
type CurrentUserProviderProps = { children: ReactNode }
export type UserState = {
    user: User | null
    profile: UserProfile | null
    status: AuthStatus
    error: string | null
    debugMode: boolean
    refreshProfile: () => Promise<void>
    clearDebugAccess: () => void
}

export const CurrentUserProvider = ({ children }: CurrentUserProviderProps) => {
    const [user, setUser] = useState<User | null>(null)
    const [profile, setProfile] = useState<UserProfile | null>(null)
    const [status, setStatus] = useState<AuthStatus>("initializing")
    const [error, setError] = useState<string | null>(null)
    const userRef = useRef<User | null>(null)
    const profileRef = useRef<UserProfile | null>(null)
    const requestRef = useRef(0)

    // The latest values for the async handlers below, which compare against them after each await
    useEffect(() => {
        userRef.current = user
        profileRef.current = profile
    }, [user, profile])

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
                if (!nextUser.emailVerified && nextUser.providerData.some((provider) => provider.providerId === "google.com")) {
                    await gameApi(nextUser, "/api/me/verify-linked-google-email", { method: "POST" })
                    await reload(nextUser)
                    await nextUser.getIdToken(true)
                }
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

    const clearDebugAccess = useCallback(() => setProfile((current) => current ? { ...current, capabilities: { debugMode: false } } : null), [])
    const debugMode = Boolean(profile?.capabilities.debugMode)
    const value = useMemo(
        () => ({ user, profile, status, error, debugMode, refreshProfile, clearDebugAccess }),
        [user, profile, status, error, debugMode, refreshProfile, clearDebugAccess],
    )
    return <CurrentUserContext value={value}>{children}</CurrentUserContext>
}

export const useCurrentUser = () => {
    const context = useContext(CurrentUserContext)
    if (!context) throw new Error("useCurrentUser must be used within a CurrentUserProvider")
    return context
}

import { FormEvent, useState } from "react"
import { Alert, Button, Stack, TextField, Typography } from "@mui/material"
import {
    createUserWithEmailAndPassword,
    GoogleAuthProvider,
    linkWithCredential,
    reload,
    sendEmailVerification,
    sendPasswordResetEmail,
    signInWithEmailAndPassword,
    signInWithPopup,
    signOut,
    updateProfile,
    validatePassword,
} from "firebase/auth"
import { AuthCredential } from "firebase/auth"
import Chat from "./Chat.tsx"
import { auth } from "./firebase.ts"
import { useCurrentUser } from "./CurrentUserContext.tsx"

const errorText = (reason: unknown) => {
    if (typeof reason === "object" && reason && "code" in reason) {
        const code = String(reason.code)
        if (code === "auth/account-exists-with-different-credential") {
            return "An account already exists with this email. Sign in with its existing method to link Google."
        }
        if (code === "auth/email-already-in-use") return "An account already exists with this email. Sign in with your existing method."
        if (code === "auth/weak-password") return "The password does not meet your Firebase password policy."
        if (code === "auth/invalid-credential" || code === "auth/invalid-login-credentials") return "Email or password is incorrect."
    }
    return reason instanceof Error ? reason.message : "The request failed. Please try again."
}

const FrontPage = () => {
    const { user, profile, status, error, refreshProfile } = useCurrentUser()
    const [mode, setMode] = useState<"signin" | "register" | "reset">("signin")
    const [email, setEmail] = useState("")
    const [password, setPassword] = useState("")
    const [confirmation, setConfirmation] = useState("")
    const [displayName, setDisplayName] = useState("")
    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState<string | null>(null)
    const [pendingCredential, setPendingCredential] = useState<AuthCredential | null>(null)

    const run = async (action: () => Promise<unknown>) => {
        setBusy(true)
        setMessage(null)
        try {
            await action()
        } catch (reason) {
            setMessage(errorText(reason))
        } finally {
            setBusy(false)
        }
    }
    const submit = (event: FormEvent) => {
        event.preventDefault()
        void run(async () => {
            if (mode === "reset") {
                await sendPasswordResetEmail(auth, email.trim())
                setMessage("Password reset email sent.")
                return
            }
            if (mode === "register") {
                if (password !== confirmation) throw new Error("Passwords do not match.")
                const policy = await validatePassword(auth, password)
                if (!policy.isValid) throw new Error("Password does not meet the configured Firebase password policy.")
                if (displayName.trim().length < 1 || displayName.trim().length > 50) throw new Error("Display name must be 1–50 characters.")
                const result = await createUserWithEmailAndPassword(auth, email.trim(), password)
                await updateProfile(result.user, { displayName: displayName.trim() })
                await sendEmailVerification(result.user)
                localStorage.setItem(`pending-profile-name:${result.user.uid}`, displayName.trim())
                setMessage("Check your email for a verification link. Return here after verifying.")
                return
            }
            const result = await signInWithEmailAndPassword(auth, email.trim(), password)
            if (pendingCredential) {
                await linkWithCredential(result.user, pendingCredential)
                setPendingCredential(null)
                setMessage("Google sign-in linked to your account.")
            }
        })
    }

    const google = () =>
        void run(async () => {
            try {
                const credential = await signInWithPopup(auth, new GoogleAuthProvider())
                if (pendingCredential) {
                    await linkWithCredential(credential.user, pendingCredential)
                    setPendingCredential(null)
                }
            } catch (reason) {
                const code = typeof reason === "object" && reason && "code" in reason ? String(reason.code) : ""
                if (code === "auth/account-exists-with-different-credential") {
                    const credential = GoogleAuthProvider.credentialFromError(reason as Parameters<typeof GoogleAuthProvider.credentialFromError>[0])
                    if (credential) setPendingCredential(credential)
                    setMessage("This email uses another sign-in method. Sign in with that method below; Google will be linked after authentication.")
                    return
                }
                throw reason
            }
        })

    const verifyAgain = () =>
        void run(async () => {
            if (!user) return
            await reload(user)
            await user.getIdToken(true)
            if (user.emailVerified) {
                await refreshProfile()
                setMessage("Email verified.")
            } else setMessage("Firebase has not confirmed the verification yet. Try again in a moment.")
        })
    const resend = () =>
        void run(async () => {
            if (!user) return
            await sendEmailVerification(user)
            setMessage("Verification email sent.")
        })

    if (status === "initializing" || status === "profileLoading") return <Typography sx={{ p: 2 }}>Loading account…</Typography>
    if (user && status === "verificationRequired") {
        return (
            <Stack spacing={2} sx={{ p: 2, maxWidth: 520 }}>
                <Typography variant="h5">Verify your email</Typography>
                <Typography>Open the verification link sent to {user.email}, then return here.</Typography>
                {message && <Alert severity="info">{message}</Alert>}
                <Stack direction="row" spacing={1}>
                    <Button disabled={busy} onClick={resend}>Resend email</Button>
                    <Button disabled={busy} onClick={verifyAgain}>I’ve verified</Button>
                    <Button disabled={busy} onClick={() => void signOut(auth)}>Sign out</Button>
                </Stack>
            </Stack>
        )
    }
    if (user && status === "recoverableError") {
        return (
            <Stack spacing={2} sx={{ p: 2, maxWidth: 520 }}>
                <Alert severity="error">{error}</Alert>
                <Button onClick={() => void refreshProfile()}>Retry</Button>
                <Button onClick={() => void signOut(auth)}>Sign out</Button>
            </Stack>
        )
    }
    if (user && profile) return <Chat user={user} />

    return (
        <Stack component="form" onSubmit={submit} spacing={2} sx={{ p: 2, maxWidth: 420 }}>
            <Typography variant="h5">{mode === "register" ? "Create account" : mode === "reset" ? "Reset password" : "Sign in"}</Typography>
            {message && <Alert severity={message.includes("sent") || message.includes("Check") ? "success" : "info"}>{message}</Alert>}
            <TextField label="Email" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
            {mode === "register" && (
                <TextField
                    label="Display name"
                    required
                    slotProps={{ htmlInput: { maxLength: 50 } }}
                    value={displayName}
                    onChange={(event) => setDisplayName(event.target.value)}
                />
            )}
            {mode !== "reset" && (
                <TextField
                    label="Password"
                    type="password"
                    autoComplete={mode === "register" ? "new-password" : "current-password"}
                    required
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                />
            )}
            {mode === "register" && (
                <TextField
                    label="Confirm password"
                    type="password"
                    autoComplete="new-password"
                    required
                    value={confirmation}
                    onChange={(event) => setConfirmation(event.target.value)}
                />
            )}
            <Button type="submit" variant="contained" disabled={busy}>
                {mode === "register" ? "Register" : mode === "reset" ? "Send reset email" : "Sign in"}
            </Button>
            {mode === "signin" && <Button type="button" onClick={google} disabled={busy}>Continue with Google</Button>}
            {mode === "signin" && <Button type="button" onClick={() => setMode("reset")}>Forgot password?</Button>}
            {mode !== "signin" && <Button type="button" onClick={() => setMode("signin")}>Back to sign in</Button>}
            {mode !== "register" && <Button type="button" onClick={() => setMode("register")}>Create an account</Button>}
        </Stack>
    )
}

export default FrontPage

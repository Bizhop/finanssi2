import { type SubmitEvent, useState } from "react"
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
import type { AuthCredential } from "firebase/auth"
import { useTranslation } from "react-i18next"
import { Chat } from "../chat/Chat.tsx"
import { auth } from "../auth/firebase.ts"
import { useCurrentUser } from "../auth/CurrentUserContext.tsx"
import { t } from "../i18n/index.ts"

const errorText = (reason: unknown) => {
    if (typeof reason === "object" && reason && "code" in reason) {
        const code = String(reason.code)
        if (code === "auth/account-exists-with-different-credential") {
            return t("auth.error.accountExistsLink")
        }
        if (code === "auth/email-already-in-use") return t("auth.error.emailInUse")
        if (code === "auth/weak-password") return t("auth.error.weakPassword")
        if (code === "auth/invalid-credential" || code === "auth/invalid-login-credentials") return t("auth.error.invalidCredential")
    }
    return reason instanceof Error ? reason.message : t("common.requestFailed")
}

type Notice = { text: string; severity: "success" | "info" | "error" }
const success = (text: string): Notice => ({ text, severity: "success" })
const info = (text: string): Notice => ({ text, severity: "info" })

export const FrontPage = () => {
    const { t } = useTranslation()
    const { user, profile, status, error, refreshProfile } = useCurrentUser()
    const [mode, setMode] = useState<"signin" | "register" | "reset">("signin")
    const [email, setEmail] = useState("")
    const [password, setPassword] = useState("")
    const [confirmation, setConfirmation] = useState("")
    const [displayName, setDisplayName] = useState("")
    const [busy, setBusy] = useState(false)
    const [notice, setNotice] = useState<Notice | null>(null)
    const [pendingCredential, setPendingCredential] = useState<AuthCredential | null>(null)

    const run = async (action: () => Promise<unknown>) => {
        setBusy(true)
        setNotice(null)
        try {
            await action()
        } catch (reason) {
            setNotice({ text: errorText(reason), severity: "error" })
        } finally {
            setBusy(false)
        }
    }
    const submit = (event: SubmitEvent<HTMLFormElement>) => {
        event.preventDefault()
        void run(async () => {
            if (mode === "reset") {
                await sendPasswordResetEmail(auth, email.trim())
                setNotice(success(t("auth.resetSent")))
                return
            }
            if (mode === "register") {
                if (password !== confirmation) throw new Error(t("auth.passwordsDiffer"))
                const policy = await validatePassword(auth, password)
                if (!policy.isValid) throw new Error(t("auth.passwordPolicy"))
                if (displayName.trim().length < 1 || displayName.trim().length > 50) throw new Error(t("auth.displayNameLength"))
                const result = await createUserWithEmailAndPassword(auth, email.trim(), password)
                await updateProfile(result.user, { displayName: displayName.trim() })
                await sendEmailVerification(result.user)
                setNotice(success(t("auth.checkEmail")))
                return
            }
            const result = await signInWithEmailAndPassword(auth, email.trim(), password)
            if (pendingCredential) {
                await linkWithCredential(result.user, pendingCredential)
                setPendingCredential(null)
                setNotice(success(t("auth.googleLinked")))
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
                    setNotice(info(t("auth.otherMethod")))
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
                setNotice(success(t("auth.emailVerified")))
            } else setNotice(info(t("auth.notVerifiedYet")))
        })
    const resend = () =>
        void run(async () => {
            if (!user) return
            await sendEmailVerification(user)
            setNotice(success(t("auth.verificationSent")))
        })

    if (status === "initializing" || status === "profileLoading") return <Typography sx={{ p: 2 }}>{t("common.loadingAccount")}</Typography>
    if (user && status === "verificationRequired") {
        return (
            <Stack spacing={2} sx={{ p: 2, maxWidth: 520 }}>
                <Typography variant="h5">{t("auth.verifyTitle")}</Typography>
                <Typography>{t("auth.verifyBody", { email: user.email })}</Typography>
                {notice && <Alert severity={notice.severity}>{notice.text}</Alert>}
                <Stack direction="row" spacing={1}>
                    <Button disabled={busy} onClick={resend}>{t("auth.resend")}</Button>
                    <Button disabled={busy} onClick={verifyAgain}>{t("auth.verified")}</Button>
                    <Button disabled={busy} onClick={() => void signOut(auth)}>{t("auth.signOut")}</Button>
                </Stack>
            </Stack>
        )
    }
    if (user && status === "recoverableError") {
        return (
            <Stack spacing={2} sx={{ p: 2, maxWidth: 520 }}>
                <Alert severity="error">{error}</Alert>
                <Button onClick={() => void refreshProfile()}>{t("common.retry")}</Button>
                <Button onClick={() => void signOut(auth)}>{t("auth.signOut")}</Button>
            </Stack>
        )
    }
    if (user && profile) return <Chat user={user} />

    return (
        <Stack component="form" onSubmit={submit} spacing={2} sx={{ p: 2, maxWidth: 420 }}>
            <Typography variant="h5">{t(`auth.title.${mode}`)}</Typography>
            {notice && <Alert severity={notice.severity}>{notice.text}</Alert>}
            <TextField label={t("auth.email")} type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
            {mode === "register" && (
                <TextField
                    label={t("auth.displayName")}
                    required
                    slotProps={{ htmlInput: { maxLength: 50 } }}
                    value={displayName}
                    onChange={(event) => setDisplayName(event.target.value)}
                />
            )}
            {mode !== "reset" && (
                <TextField
                    label={t("auth.password")}
                    type="password"
                    autoComplete={mode === "register" ? "new-password" : "current-password"}
                    required
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                />
            )}
            {mode === "register" && (
                <TextField
                    label={t("auth.confirmPassword")}
                    type="password"
                    autoComplete="new-password"
                    required
                    value={confirmation}
                    onChange={(event) => setConfirmation(event.target.value)}
                />
            )}
            <Button type="submit" variant="contained" disabled={busy}>
                {t(`auth.submit.${mode}`)}
            </Button>
            {mode === "signin" && <Button type="button" onClick={google} disabled={busy}>{t("auth.google")}</Button>}
            {mode === "signin" && <Button type="button" onClick={() => setMode("reset")}>{t("auth.forgot")}</Button>}
            {mode !== "signin" && <Button type="button" onClick={() => setMode("signin")}>{t("auth.backToSignIn")}</Button>}
            {mode !== "register" && <Button type="button" onClick={() => setMode("register")}>{t("auth.createAccount")}</Button>}
        </Stack>
    )
}

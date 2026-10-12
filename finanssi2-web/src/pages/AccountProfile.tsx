import { type SubmitEvent, useState } from "react"
import { Alert, Avatar, Button, Stack, TextField, Typography } from "@mui/material"
import { EmailAuthProvider, GoogleAuthProvider, linkWithCredential, reauthenticateWithPopup, reload, validatePassword } from "firebase/auth"
import { toast } from "react-toastify"
import { useTranslation } from "react-i18next"
import { auth } from "../auth/firebase.ts"
import { useCurrentUser } from "../auth/CurrentUserContext.tsx"
import { avatarSrc, gameApi } from "../api/gameApi.ts"

export const AccountProfile = () => {
    const { t } = useTranslation()
    const { user, profile, refreshProfile } = useCurrentUser()
    const [displayName, setDisplayName] = useState(profile?.displayName ?? "")
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [saved, setSaved] = useState(false)
    const [password, setPassword] = useState("")
    const [passwordConfirmation, setPasswordConfirmation] = useState("")
    const [passwordMessage, setPasswordMessage] = useState<string | null>(null)
    const [passwordError, setPasswordError] = useState<string | null>(null)
    const [avatarError, setAvatarError] = useState<string | null>(null)
    const submit = async (event: SubmitEvent<HTMLFormElement>) => {
        event.preventDefault()
        if (!user || !profile) return
        setBusy(true)
        setError(null)
        setSaved(false)
        try {
            await gameApi(user, "/api/me/profile", { method: "PUT", body: JSON.stringify({ displayName, version: profile.version }) })
            await refreshProfile()
            setSaved(true)
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("account.saveFailed"))
        } finally {
            setBusy(false)
        }
    }
    const addPassword = async (event: SubmitEvent<HTMLFormElement>) => {
        event.preventDefault()
        if (!user || !profile) return
        setBusy(true)
        setPasswordError(null)
        setPasswordMessage(null)
        try {
            if (password !== passwordConfirmation) throw new Error(t("auth.passwordsDiffer"))
            const policy = await validatePassword(auth, password)
            if (!policy.isValid) throw new Error(t("auth.passwordPolicy"))
            const credential = EmailAuthProvider.credential(profile.email, password)
            try {
                await linkWithCredential(user, credential)
            } catch (reason) {
                if (!(typeof reason === "object" && reason && "code" in reason && reason.code === "auth/requires-recent-login")) throw reason
                await reauthenticateWithPopup(user, new GoogleAuthProvider())
                await linkWithCredential(user, credential)
            }
            await reload(user)
            await user.getIdToken(true)
            await gameApi(user, "/api/me/verify-linked-google-email", { method: "POST" })
            await reload(user)
            await user.getIdToken(true)
            setPassword("")
            setPasswordConfirmation("")
            const success = t("account.passwordAdded")
            setPasswordMessage(success)
            toast(success, { type: "success", autoClose: 4000 })
        } catch (reason) {
            setPasswordError(reason instanceof Error ? reason.message : t("account.passwordFailed"))
        } finally {
            setBusy(false)
        }
    }
    const uploadAvatar = async (file: File) => {
        if (!user || !profile) return
        setBusy(true)
        setAvatarError(null)
        const data = new FormData()
        data.append("file", file)
        try {
            await gameApi(user, `/api/me/avatar?version=${profile.version}`, { method: "PUT", body: data })
            await refreshProfile()
        } catch (reason) {
            setAvatarError(reason instanceof Error ? reason.message : t("account.uploadFailed"))
        } finally {
            setBusy(false)
        }
    }
    const removeAvatar = async () => {
        if (!user || !profile) return
        setBusy(true)
        setAvatarError(null)
        try {
            await gameApi(user, `/api/me/avatar?version=${profile.version}`, { method: "DELETE" })
            await refreshProfile()
        } catch (reason) {
            setAvatarError(reason instanceof Error ? reason.message : t("account.removeFailed"))
        } finally {
            setBusy(false)
        }
    }
    if (!user || !profile) return null
    return (
        <Stack spacing={2} sx={{ p: 2, maxWidth: 520 }}>
            <Typography variant="h5">{t("account.title")}</Typography>
            <Typography>{t("account.email", { email: profile.email })}</Typography>
            <Typography variant="h6">{t("account.avatar")}</Typography>
            <Avatar src={avatarSrc(profile.avatar)} alt={profile.displayName} sx={{ width: 96, height: 96, fontSize: 36 }}>
                {profile.displayName.slice(0, 1).toUpperCase()}
            </Avatar>
            <Stack direction="row" spacing={1}>
                <Button component="label" variant="outlined" disabled={busy}>
                    {t("account.upload")}
                    <input
                        hidden
                        type="file"
                        accept="image/jpeg,image/png"
                        onChange={(event) => {
                            const file = event.currentTarget.files?.[0]
                            event.currentTarget.value = ""
                            if (file) void uploadAvatar(file)
                        }}
                    />
                </Button>
                {profile.avatarSource === "custom" && <Button onClick={() => void removeAvatar()} disabled={busy}>{t("account.removeCustom")}</Button>}
            </Stack>
            {avatarError && <Alert severity="error">{avatarError}</Alert>}
            <Typography variant="caption">{t("account.avatarHelp")}</Typography>
            <Stack component="form" onSubmit={submit} spacing={2}>
                <TextField
                    label={t("account.displayName")}
                    required
                    value={displayName}
                    slotProps={{ htmlInput: { maxLength: 50 } }}
                    onChange={(event) => setDisplayName(event.target.value)}
                />
                {error && <Alert severity="error">{error}</Alert>}
                {saved && <Alert severity="success">{t("account.saved")}</Alert>}
                <Button type="submit" variant="contained" disabled={busy || displayName.trim().length === 0 || displayName.trim() === profile.displayName}>
                    {t("account.saveProfile")}
                </Button>
            </Stack>
            {passwordMessage && user.providerData.some((provider) => provider.providerId === "password") && <Alert severity="success">{passwordMessage}</Alert>}
            {passwordError && user.providerData.some((provider) => provider.providerId === "password") && <Alert severity="error">{passwordError}</Alert>}
            {user.providerData.some((provider) => provider.providerId === "google.com") &&
                !user.providerData.some((provider) => provider.providerId === "password") && (
                <Stack component="form" onSubmit={addPassword} spacing={2}>
                    <Typography variant="h6">{t("account.addPasswordTitle")}</Typography>
                    <TextField
                        label={t("account.newPassword")}
                        type="password"
                        required
                        autoComplete="new-password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                    />
                    <TextField
                        label={t("account.confirmPassword")}
                        type="password"
                        required
                        autoComplete="new-password"
                        value={passwordConfirmation}
                        onChange={(event) => setPasswordConfirmation(event.target.value)}
                    />
                    {passwordError && <Alert severity="error">{passwordError}</Alert>}
                    <Button type="submit" disabled={busy}>{t("account.addPassword")}</Button>
                </Stack>
            )}
        </Stack>
    )
}

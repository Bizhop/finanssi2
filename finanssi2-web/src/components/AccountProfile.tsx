import { FormEvent, useState } from "react"
import { Alert, Avatar, Button, Stack, TextField, Typography } from "@mui/material"
import { EmailAuthProvider, GoogleAuthProvider, linkWithCredential, reauthenticateWithPopup, validatePassword } from "firebase/auth"
import { auth } from "./firebase.ts"
import { useCurrentUser } from "./CurrentUserContext.tsx"
import { gameApi } from "./gameApi.ts"

const AccountProfile = () => {
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
    const submit = async (event: FormEvent) => {
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
            setError(reason instanceof Error ? reason.message : "Unable to save your profile")
        } finally {
            setBusy(false)
        }
    }
    const addPassword = async (event: FormEvent) => {
        event.preventDefault()
        if (!user || !profile) return
        setBusy(true)
        setPasswordError(null)
        setPasswordMessage(null)
        try {
            if (password !== passwordConfirmation) throw new Error("Passwords do not match.")
            const policy = await validatePassword(auth, password)
            if (!policy.isValid) throw new Error("Password does not meet the configured Firebase password policy.")
            const credential = EmailAuthProvider.credential(profile.email, password)
            try {
                await linkWithCredential(user, credential)
            } catch (reason) {
                if (!(typeof reason === "object" && reason && "code" in reason && reason.code === "auth/requires-recent-login")) throw reason
                await reauthenticateWithPopup(user, new GoogleAuthProvider())
                await linkWithCredential(user, credential)
            }
            setPassword("")
            setPasswordConfirmation("")
            setPasswordMessage("Password sign-in added to this Google account.")
        } catch (reason) {
            setPasswordError(reason instanceof Error ? reason.message : "Unable to add password sign-in")
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
            setAvatarError(reason instanceof Error ? reason.message : "Unable to upload avatar")
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
            setAvatarError(reason instanceof Error ? reason.message : "Unable to remove avatar")
        } finally {
            setBusy(false)
        }
    }
    if (!user || !profile) return null
    return (
        <Stack spacing={2} sx={{ p: 2, maxWidth: 520 }}>
            <Typography variant="h5">Account</Typography>
            <Typography>Email: {profile.email}</Typography>
            <Typography variant="h6">Avatar</Typography>
            <Avatar src={profile.avatar ?? undefined} alt={profile.displayName} sx={{ width: 96, height: 96, fontSize: 36 }}>
                {profile.displayName.slice(0, 1).toUpperCase()}
            </Avatar>
            <Stack direction="row" spacing={1}>
                <Button component="label" variant="outlined" disabled={busy}>
                    Upload image
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
                {profile.avatarSource === "custom" && <Button onClick={() => void removeAvatar()} disabled={busy}>Remove custom avatar</Button>}
            </Stack>
            {avatarError && <Alert severity="error">{avatarError}</Alert>}
            <Typography variant="caption">JPEG or PNG, up to 2 MiB. The image is cropped to a square.</Typography>
            <Stack component="form" onSubmit={submit} spacing={2}>
                <TextField
                    label="Display name"
                    required
                    value={displayName}
                    slotProps={{ htmlInput: { maxLength: 50 } }}
                    onChange={(event) => setDisplayName(event.target.value)}
                />
                {error && <Alert severity="error">{error}</Alert>}
                {saved && <Alert severity="success">Profile saved.</Alert>}
                <Button type="submit" variant="contained" disabled={busy || displayName.trim().length === 0 || displayName.trim() === profile.displayName}>
                    Save profile
                </Button>
            </Stack>
            {user.providerData.some((provider) => provider.providerId === "google.com") &&
                !user.providerData.some((provider) => provider.providerId === "password") && (
                <Stack component="form" onSubmit={addPassword} spacing={2}>
                    <Typography variant="h6">Add password sign-in</Typography>
                    <TextField
                        label="New password"
                        type="password"
                        required
                        autoComplete="new-password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                    />
                    <TextField
                        label="Confirm password"
                        type="password"
                        required
                        autoComplete="new-password"
                        value={passwordConfirmation}
                        onChange={(event) => setPasswordConfirmation(event.target.value)}
                    />
                    {passwordError && <Alert severity="error">{passwordError}</Alert>}
                    {passwordMessage && <Alert severity="success">{passwordMessage}</Alert>}
                    <Button type="submit" disabled={busy}>Add password</Button>
                </Stack>
            )}
        </Stack>
    )
}

export default AccountProfile

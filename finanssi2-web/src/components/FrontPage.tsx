import { useEffect } from "react"
import { IconButton, Stack, Tooltip } from "@mui/material"
import { UserCredential } from "firebase/auth"
import GoogleIcon from "@mui/icons-material/Google"
import { useSignInWithGoogle } from "react-firebase-hooks/auth"
import { toast } from "react-toastify"
import "react-toastify/dist/ReactToastify.css"

import Chat from "./Chat.tsx"
import { auth } from "./firebase.ts"
import { useCurrentUser } from "./CurrentUserContext.tsx"

const FrontPage = () => {
    const { user, setUser } = useCurrentUser()

    const [signInWithGoogle] = useSignInWithGoogle(auth)

    useEffect(() => {
        const unsubscribe = auth.onAuthStateChanged((user) => {
            setUser(user)
        })

        return () => unsubscribe()
    }, [])

    const loggedIn = (credential: UserCredential | undefined): void => {
        if (credential && credential.user && setUser) {
            setUser(credential.user)
        } else {
            toast("Unable to login", { type: "error" })
        }
    }

    return (
        <Stack direction="column">
            {user
                ? (
                    <>
                        <Chat user={user} />
                    </>
                )
                : (
                    <Tooltip title="Sign in with Google" placement="bottom-start">
                        <IconButton
                            color="error"
                            onClick={() => signInWithGoogle().then(loggedIn).catch(console.error)}
                        >
                            <GoogleIcon />
                        </IconButton>
                    </Tooltip>
                )}
        </Stack>
    )
}

export default FrontPage

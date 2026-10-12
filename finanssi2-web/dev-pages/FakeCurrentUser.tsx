// A signed-in user for the test pages, without Firebase: the app reads who is signed in from CurrentUserContext, so the pages provide
// a fixed value. The profile is what the fake backend serves from /api/me.
import type { ReactNode } from "react"
import type { User } from "firebase/auth"

import { CurrentUserContext, type UserState } from "../src/auth/CurrentUserContext.tsx"
import { PublicProfilesProvider } from "../src/auth/PublicProfiles.tsx"
import { type Person, profileOf } from "./people.ts"

export const FakeCurrentUser = ({ person, debugMode = false, children }: { person: Person; debugMode?: boolean; children: ReactNode }) => {
    // Only what the app uses of a Firebase user
    const user = {
        uid: `firebase-${person.key}`,
        email: person.email,
        emailVerified: true,
        displayName: person.name,
        photoURL: null,
        providerData: [],
        getIdToken: () => Promise.resolve("fake-token"),
    } as unknown as User
    const value: UserState = {
        user,
        profile: profileOf(person, debugMode),
        status: "ready",
        error: null,
        debugMode,
        refreshProfile: () => Promise.resolve(),
        clearDebugAccess: () => {},
    }
    return (
        <CurrentUserContext value={value}>
            <PublicProfilesProvider>{children}</PublicProfilesProvider>
        </CurrentUserContext>
    )
}

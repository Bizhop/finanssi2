import React, { createContext, useContext, useState } from "react"
import { User } from "firebase/auth"

const CurrentUserContext = createContext<UserState | null>(null)

type CurrentUserProviderProps = {
    children: React.ReactNode
}

type UserState = {
    user: User | null
    setUser: React.Dispatch<React.SetStateAction<User | null>>
}

export const CurrentUserProvider = ({children}: CurrentUserProviderProps) => {
    const [user, setUser] = useState<User | null>(null)

    return (
        <CurrentUserContext.Provider value={{user, setUser}}>
            {children}
        </CurrentUserContext.Provider>
    )
}

export const useCurrentUser = () => {
    const context = useContext(CurrentUserContext)
    if(!context) {
        throw new Error("useCurrentUser must be used within a CurrentUserProvider")
    }
    return context
}

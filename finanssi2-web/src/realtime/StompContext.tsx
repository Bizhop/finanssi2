import { createContext, type ReactNode, useContext, useEffect, useEffectEvent, useMemo, useState } from "react"
import { Client } from "@stomp/stompjs"

/** The current STOMP connection; a new value (and new subscriptions) for every connect */
export type StompConnection = {
    connected: boolean
    /** The last attempt to connect failed or the connection broke, and it has not been restored yet */
    problem?: boolean
    /** Subscribes on this connection; returns a function that unsubscribes */
    subscribe: (destination: string, onMessage: (body: string) => void) => () => void
}

const disconnected: StompConnection = { connected: false, subscribe: () => () => {} }
const failed: StompConnection = { ...disconnected, problem: true }

export const StompContext = createContext<StompConnection | null>(null)

type StompProviderProps = {
    /** ws:// or wss:// URL of the STOMP endpoint */
    url: string
    /** Connect only when enabled, e.g. when a user is logged in */
    enabled: boolean
    /** Called before every (re)connect, sent as the Authorization: Bearer header of the CONNECT frame */
    getAccessToken: () => Promise<string>
    children: ReactNode
}

export const StompProvider = ({ url, enabled, getAccessToken, children }: StompProviderProps) => {
    const [connectedClient, setConnectedClient] = useState<Client | null>(null)
    // Set when a connection attempt fails or the connection breaks; the client keeps retrying and the next connect clears it
    const [problem, setProblem] = useState(false)
    // An effect event, so that a new function on every render doesn't reconnect
    const fetchAccessToken = useEffectEvent(() => getAccessToken())

    useEffect(() => {
        if (!enabled) return
        const client = new Client({
            brokerURL: url,
            // Reconnects automatically after this delay, e.g. after a network break or a backend restart
            reconnectDelay: 5000,
            // A fresh token for every attempt: Firebase ID tokens expire after an hour
            beforeConnect: async (client) => {
                client.connectHeaders = { Authorization: `Bearer ${await fetchAccessToken()}` }
            },
            onConnect: () => {
                setProblem(false)
                setConnectedClient(client)
            },
            // Only if still current: a replaced client's close can arrive after the new one connected
            onWebSocketClose: () => setConnectedClient((current) => current === client ? null : current),
            onWebSocketError: () => setProblem(true),
            onStompError: (frame) => {
                console.error("STOMP error:", frame.headers.message)
                setProblem(true)
            },
        })
        client.activate()
        return () => {
            setConnectedClient(null)
            setProblem(false)
            void client.deactivate()
        }
    }, [url, enabled])

    const connection = useMemo<StompConnection>(() =>
        connectedClient
            ? {
                connected: true,
                subscribe: (destination, onMessage) => {
                    const subscription = connectedClient.subscribe(destination, (message) => onMessage(message.body))
                    // Not after a disconnect (or deactivate) has started: the connection's subscriptions go with it
                    return () => {
                        if (connectedClient.connected && connectedClient.active) subscription.unsubscribe()
                    }
                },
            }
            : problem
            ? failed
            : disconnected, [connectedClient, problem])

    return <StompContext value={connection}>{children}</StompContext>
}

const useStompConnection = () => {
    const connection = useContext(StompContext)
    if (!connection) throw new Error("STOMP hooks must be used within a StompProvider")
    return connection
}

export const useStompConnected = () => useStompConnection().connected

/** True while the realtime connection is down because of an error; it reconnects by itself */
export const useStompProblem = () => {
    const connection = useStompConnection()
    return connection.problem === true
}

/** Calls onMessage for every message to the destination; subscribes again after each reconnect */
export const useStompSubscription = (destination: string, onMessage: (body: string) => void) => {
    const connection = useStompConnection()
    // Always the latest handler, without resubscribing when its identity changes
    const receive = useEffectEvent((body: string) => onMessage(body))

    useEffect(() => {
        if (!connection.connected) return
        return connection.subscribe(destination, receive)
    }, [connection, destination])
}

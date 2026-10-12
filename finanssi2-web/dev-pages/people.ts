// The people of the test pages. Their ids are UUIDs because the app resolves names and avatars through /api/users, which only accepts
// UUIDs, like the real backend; the short keys are for the ?turn= parameter and for readable fixtures.
export type Person = { key: string; id: string; name: string; email: string }

const person = (key: string, name: string, index: number): Person => ({
    key,
    id: `00000000-0000-4000-8000-${index.toString().padStart(12, "0")}`,
    name,
    email: `${key}@example.com`,
})

export const tester = person("tester", "Maija Meikäläinen", 1)
export const olli = person("olli", "Olli Other", 2)
export const pekka = person("pekka", "Pekka Pörssi", 3)
export const liisa = person("liisa", "Liisa Laine", 4)
export const viewer = person("viewer", "Read Only Viewer", 5)
export const matti = person("matti", "Matti Meikäläinen", 6)
/** A sender whose profile the fake backend does not know, like a deleted account */
export const stranger = { key: "stranger", id: "00000000-0000-4000-8000-000000000099", name: "", email: "" } satisfies Person

export const directory: Person[] = [tester, olli, pekka, liisa, viewer, matti]

/** ?turn=olli and similar: a key from the URL as a player id */
export const idOfKey = (key: string | null, fallback: string) => directory.find((item) => item.key === key)?.id ?? fallback

/** Answers GET /api/users?ids=... like the backend: the known profiles among the ids */
export const publicProfiles = (url: URL) =>
    directory
        .filter((item) => url.searchParams.getAll("ids").includes(item.id))
        .map((item) => ({ id: item.id, displayName: item.name, avatar: null }))

/** Answers GET /api/me for a person */
export const profileOf = (item: Person, debugMode: boolean) => ({
    id: item.id,
    email: item.email,
    displayName: item.name,
    avatar: null,
    avatarSource: null,
    version: 1,
    capabilities: { debugMode },
})

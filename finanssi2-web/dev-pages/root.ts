/** The element the test page renders into */
export const appRoot = () => {
    const element = document.getElementById("app")
    if (!element) throw new Error("The page has no #app element")
    return element
}

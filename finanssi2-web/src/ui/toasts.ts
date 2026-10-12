import { toast } from "react-toastify"

/** Long enough to read an error; the container's default suits short confirmations */
const ERROR_TOAST_MS = 6000

export const errorToast = (message: string) => toast(message, { type: "error", autoClose: ERROR_TOAST_MS })

import * as fs from "node:fs"
import * as path from "node:path"

export interface UserDataValues {
    region: string
    apiHostname: string
    allowedOrigin: string
    debugEmails: string
    repoUrl: string
    firebaseSecretArn: string
    databaseAdminSecretArn: string
    databaseApplicationSecretArn: string
    cloudWatchAgentUrl: string
    hostLogGroup: string
    applicationLogGroup: string
}

// EC2 rejects user data above 16 KB.
const maxUserDataBytes = 16 * 1024

const userdataDir = path.join(__dirname, "userdata")

function readUserdata(file: string): string {
    return fs.readFileSync(path.join(userdataDir, file), "utf8")
}

// bootstrap.sh embeds the host scripts through `# @include <file>` lines inside heredocs, so each script stays a file shellcheck can read.
export function renderUserData(values: UserDataValues): string {
    const script = readUserdata("bootstrap.sh")
        .replace(/^# @include (.+)\n/gm, (_line, file: string) => readUserdata(file))
        .replaceAll("__API_HOSTNAME__", values.apiHostname)
        .replaceAll("__AWS_REGION__", values.region)
        .replaceAll("__ECR_REPOSITORY_URL__", values.repoUrl)
        .replaceAll("__FIREBASE_ADMIN_SECRET_ARN__", values.firebaseSecretArn)
        .replaceAll("__DATABASE_ADMIN_SECRET_ARN__", values.databaseAdminSecretArn)
        .replaceAll("__DATABASE_APPLICATION_SECRET_ARN__", values.databaseApplicationSecretArn)
        .replaceAll("__ALLOWED_ORIGIN__", values.allowedOrigin)
        .replaceAll("__DEBUG_ALLOWED_EMAILS__", values.debugEmails)
        .replaceAll("__INSTANCE_ID_DIMENSION__", "${aws:InstanceId}")
        .replaceAll("__CLOUDWATCH_AGENT_URL__", values.cloudWatchAgentUrl)
        .replaceAll("__HOST_LOG_GROUP__", values.hostLogGroup)
        .replaceAll("__APPLICATION_LOG_GROUP__", values.applicationLogGroup)
    const size = Buffer.byteLength(script, "utf8")
    if (size > maxUserDataBytes) throw new Error(`User data is ${size} bytes; EC2 allows at most ${maxUserDataBytes}`)
    return script
}

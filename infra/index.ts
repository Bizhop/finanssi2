import * as aws from "@pulumi/aws"
import * as pulumi from "@pulumi/pulumi"
import { createBackend } from "./backend"
import { createFrontend } from "./frontend"
import { createMonitoring } from "./monitoring"
import { createDatabase } from "./database"
import { createNetworking } from "./networking"
import { createFirebaseAdminSecret } from "./secrets"

const config = new pulumi.Config()
const region = new pulumi.Config("aws").get("region") ?? "eu-north-1"
const prefix = `finanssi2-${pulumi.getStack()}`
const instanceType = config.get("instanceType") ?? "t3.small"
const architecture = config.get("architecture") ?? "x86_64"
const frontendHostname = config.get("frontendHostname") ?? "finanssi.bizhop.fi"
const apiHostname = config.get("apiHostname") ?? "api.finanssi.bizhop.fi"
const allowedOrigin = config.get("allowedOrigin") ?? `https://${frontendHostname}`
const debugAllowedEmails = config.get("debugAllowedEmails") ?? ""
const monthlyBudgetLimitUsd = config.getNumber("monthlyBudgetLimitUsd")
const budgetAlertEmail = config.get("budgetAlertEmail") ?? ""
const firebaseWeb = config.requireObject<FirebaseWebConfig>("firebaseWeb")
const createFrontendDistribution = config.getBoolean("createFrontendDistribution") ?? false

interface FirebaseWebConfig {
    apiKey: string
    authDomain: string
    projectId: string
    storageBucket: string
    messagingSenderId: string
    appId: string
}

const firebaseWebKeys: (keyof FirebaseWebConfig)[] = [
    "apiKey",
    "authDomain",
    "projectId",
    "storageBucket",
    "messagingSenderId",
    "appId",
]
const missingFirebaseSettings = firebaseWebKeys.filter((key) => !firebaseWeb[key]?.trim())
if (missingFirebaseSettings.length > 0) {
    throw new Error(`Missing Firebase web configuration fields: ${missingFirebaseSettings.join(", ")}`)
}
if (firebaseWeb.authDomain !== `${firebaseWeb.projectId}.firebaseapp.com`) {
    throw new Error("firebaseWeb.authDomain must use the project's default Firebase auth domain")
}
const firebaseAdminJson = config.requireSecret("firebaseAdminJson").apply((json) => {
    let account: { project_id?: string; client_email?: string; private_key?: string }
    try {
        account = JSON.parse(json)
    } catch {
        throw new Error("firebaseAdminJson must be valid Firebase Admin service-account JSON")
    }
    if (!account.client_email || !account.private_key || account.project_id !== firebaseWeb.projectId) {
        throw new Error("firebaseAdminJson must contain credentials for the configured Firebase web project")
    }
    return json
})
if (!/^[a-z0-9.-]+$/.test(frontendHostname) || !/^[a-z0-9.-]+$/.test(apiHostname)) {
    throw new Error("Frontend and API hostnames must be DNS names without schemes or paths")
}
if (allowedOrigin !== `https://${frontendHostname}`) {
    throw new Error("allowedOrigin must exactly match the HTTPS frontendHostname")
}
if (architecture !== "x86_64" && architecture !== "arm64") {
    throw new Error("architecture must be x86_64 or arm64")
}
if (!/^[a-z0-9-]+\.[a-z0-9-]+$/.test(instanceType)) {
    throw new Error("instanceType must be an EC2 instance type such as t3.small")
}
if (instanceType.startsWith("t4g.") && architecture !== "arm64") {
    throw new Error("Graviton instance types require architecture=arm64")
}
if (instanceType.startsWith("t3.") && architecture !== "x86_64") {
    throw new Error("T3 instance types require architecture=x86_64")
}
const debugEmails = debugAllowedEmails.split(",").map((email) => email.trim()).filter(Boolean)
if (debugEmails.some((email) => !/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(email))) {
    throw new Error("debugAllowedEmails must contain comma-separated email addresses")
}
if (!monthlyBudgetLimitUsd || monthlyBudgetLimitUsd <= 0) {
    throw new Error("monthlyBudgetLimitUsd must be set to the operator's chosen alert amount")
}
if (!/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(budgetAlertEmail)) {
    throw new Error("budgetAlertEmail must be set to an address that should receive AWS cost alerts")
}

const engineVersion = config.get("postgresEngineVersion") ?? "16.6"
const minimumCapacity = config.getNumber("auroraMinCapacity") ?? 0
const maximumCapacity = config.getNumber("auroraMaxCapacity") ?? 1
const autoPauseSeconds = config.getNumber("auroraAutoPauseSeconds") ?? 300
if (!/^\d+\.\d+$/.test(engineVersion)) throw new Error("postgresEngineVersion must be an Aurora PostgreSQL major.minor version")
if (minimumCapacity !== 0) throw new Error("Aurora minimum capacity must remain 0 ACUs to allow auto-pause")
if (maximumCapacity < 0.5 || maximumCapacity > 128 || maximumCapacity % 0.5 !== 0) {
    throw new Error("auroraMaxCapacity must be between 0.5 and 128 ACUs in 0.5-ACU increments")
}
if (autoPauseSeconds < 300 || autoPauseSeconds > 86400) {
    throw new Error("auroraAutoPauseSeconds must be between 300 and 86400 seconds")
}

const provider = new aws.Provider("finanssi-aws", { region })

const networking = createNetworking(prefix, provider)
const database = createDatabase(prefix, networking, provider, {
    engineVersion,
    minCapacity: minimumCapacity,
    maxCapacity: maximumCapacity,
    autoPauseSeconds,
})
const firebaseAdminSecret = createFirebaseAdminSecret(prefix, provider, firebaseAdminJson)
const backend = createBackend(prefix, provider, {
    region,
    instanceType,
    architecture,
    apiHostname,
    allowedOrigin,
    debugEmails: debugEmails.join(","),
    firebaseSecret: firebaseAdminSecret,
    database,
    networking,
})
const certificateProvider = new aws.Provider("finanssi-certificate-aws", { region: "us-east-1" })
const frontend = createFrontend(prefix, provider, certificateProvider, {
    hostname: frontendHostname,
    createDistribution: createFrontendDistribution,
})
createMonitoring(prefix, provider, certificateProvider, {
    instance: backend.instance,
    monthlyBudgetUsd: monthlyBudgetLimitUsd,
    alertEmail: budgetAlertEmail,
})

export const selectedRegion = region
export const selectedInstanceType = instanceType
export const selectedArchitecture = architecture
export const configuredFrontendHostname = frontendHostname
export const configuredApiHostname = apiHostname
export const configuredAllowedOrigin = allowedOrigin
export const vpcId = networking.vpc.id
export const databaseEndpoint = database.cluster.endpoint
export const databasePort = database.cluster.port
export const databaseName = database.cluster.databaseName
export const databaseAdminSecretArn = database.adminSecret.arn
export const databaseApplicationSecretArn = database.applicationSecret.arn
export const firebaseAdminSecretArn = firebaseAdminSecret.arn
export const backendInstanceId = backend.instance.id
export const backendElasticIp = backend.elasticIp.publicIp
export const backendRepositoryUrl = backend.repository.repositoryUrl
export const backendApiUrl = `https://${apiHostname}`
export const frontendBucketName = frontend.bucket.bucket
export const frontendUrl = `https://${frontendHostname}`
export const frontendCertificateArn = frontend.certificate.arn
export const frontendCertificateDnsRecords = frontend.certificate.domainValidationOptions.apply((options) =>
    options.map((option) => ({ name: option.resourceRecordName, type: option.resourceRecordType, value: option.resourceRecordValue }))
)
export const frontendDistributionHostname = frontend.distribution?.domainName

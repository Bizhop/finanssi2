import * as aws from "@pulumi/aws"
import * as pulumi from "@pulumi/pulumi"
import * as random from "@pulumi/random"
import type { Networking } from "./networking"

export interface Database {
    cluster: aws.rds.Cluster
    instance: aws.rds.ClusterInstance
    adminSecret: aws.secretsmanager.Secret
    applicationSecret: aws.secretsmanager.Secret
}

export function createDatabase(
    prefix: string,
    networking: Networking,
    provider: aws.Provider,
    settings: { engineVersion: string; minCapacity: number; maxCapacity: number; autoPauseSeconds: number },
): Database {
    const passwordCharacters = "!#$%&()*+,-.:;<=>?[]^_{|}~"
    const adminPassword = new random.RandomPassword(`${prefix}-database-admin-password`, {
        length: 32,
        special: true,
        overrideSpecial: passwordCharacters,
    })
    const applicationPassword = new random.RandomPassword(`${prefix}-database-application-password`, {
        length: 32,
        special: true,
        overrideSpecial: passwordCharacters,
    })
    const databaseSubnetGroup = new aws.rds.SubnetGroup(`${prefix}-database-subnets`, {
        subnetIds: networking.privateSubnetIds,
        tags: { Name: `${prefix}-database-subnets` },
    }, { provider })

    const cluster = new aws.rds.Cluster(`${prefix}-database`, {
        clusterIdentifier: `${prefix}-database`,
        engine: "aurora-postgresql",
        engineMode: "provisioned",
        engineVersion: settings.engineVersion,
        databaseName: "finanssi",
        masterUsername: "finanssi_admin",
        masterPassword: adminPassword.result,
        storageEncrypted: true,
        serverlessv2ScalingConfiguration: {
            minCapacity: settings.minCapacity,
            maxCapacity: settings.maxCapacity,
            secondsUntilAutoPause: settings.autoPauseSeconds,
        },
        dbSubnetGroupName: databaseSubnetGroup.name,
        vpcSecurityGroupIds: [networking.databaseSecurityGroup.id],
        enableHttpEndpoint: false,
        backupRetentionPeriod: 1,
        preferredMaintenanceWindow: "sun:23:00-sun:23:30",
        skipFinalSnapshot: true,
        deletionProtection: false,
        applyImmediately: true,
        tags: { Name: `${prefix}-database`, Application: "finanssi2" },
    }, { provider })

    const instance = new aws.rds.ClusterInstance(`${prefix}-database-writer`, {
        identifier: `${prefix}-database-writer`,
        clusterIdentifier: cluster.id,
        instanceClass: "db.serverless",
        engine: "aurora-postgresql",
        engineVersion: settings.engineVersion,
        dbSubnetGroupName: databaseSubnetGroup.name,
        publiclyAccessible: false,
        tags: { Name: `${prefix}-database-writer` },
    }, { provider })

    const adminSecret = new aws.secretsmanager.Secret(`${prefix}-database-admin-secret`, {
        name: `${prefix}-database-admin`,
        description: "Aurora master credentials; used only for database administration and bootstrap",
        recoveryWindowInDays: 0,
        tags: { Application: "finanssi2" },
    }, { provider })
    new aws.secretsmanager.SecretVersion(`${prefix}-database-admin-version`, {
        secretId: adminSecret.id,
        secretString: pulumi.secret(pulumi.all([cluster.endpoint, adminPassword.result]).apply(([host, password]) => JSON.stringify({
            username: "finanssi_admin",
            password,
            engine: "aurora-postgresql",
            host,
            port: 5432,
            dbname: "finanssi",
        }))),
    }, { provider, dependsOn: [instance] })

    const applicationSecret = new aws.secretsmanager.Secret(`${prefix}-database-application-secret`, {
        name: `${prefix}-database-application`,
        description: "Finanssi application database credentials",
        recoveryWindowInDays: 0,
        tags: { Application: "finanssi2" },
    }, { provider })
    new aws.secretsmanager.SecretVersion(`${prefix}-database-application-version`, {
        secretId: applicationSecret.id,
        secretString: pulumi.secret(pulumi.all([cluster.endpoint, applicationPassword.result]).apply(([host, password]) => JSON.stringify({
            username: "finanssi_app",
            password,
            engine: "aurora-postgresql",
            host,
            port: 5432,
            dbname: "finanssi",
        }))),
    }, { provider, dependsOn: [instance] })

    return { cluster, instance, adminSecret, applicationSecret }
}

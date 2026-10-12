import * as aws from "@pulumi/aws"
import * as pulumi from "@pulumi/pulumi"
import type { Networking } from "./networking"
import type { Database } from "./database"
import { renderUserData } from "./userdata"

export interface Backend {
    instance: aws.ec2.Instance
    elasticIp: aws.ec2.Eip
    repository: aws.ecr.Repository
    hostLogGroup: aws.cloudwatch.LogGroup
    applicationLogGroup: aws.cloudwatch.LogGroup
    imageRefParameterName: string
}

export function createBackend(
    prefix: string,
    provider: aws.Provider,
    settings: {
        region: string
        instanceType: string
        architecture: "x86_64" | "arm64"
        apiHostname: string
        allowedOrigin: string
        debugEmails: string
        firebaseSecret: aws.secretsmanager.Secret
        database: Database
        networking: Networking
    },
): Backend {
    const repository = new aws.ecr.Repository(`${prefix}-backend-images`, {
        name: `${prefix}-backend`,
        imageTagMutability: "IMMUTABLE",
        forceDelete: true,
        imageScanningConfiguration: { scanOnPush: true },
        encryptionConfigurations: [{ encryptionType: "AES256" }],
    }, { provider })
    const logGroupPrefix = `/finanssi2/${pulumi.getStack()}`
    // The image reference is written by deploy-backend-image.sh (not managed here); the runtime settings are managed here.
    const imageRefParameterName = `/finanssi2/${pulumi.getStack()}/image-ref`
    const runtimeSettings = new aws.ssm.Parameter(`${prefix}-runtime-settings`, {
        name: `/finanssi2/${pulumi.getStack()}/runtime-settings`,
        type: "String",
        description: "Backend runtime settings read by finanssi2-run at service start",
        value: JSON.stringify({ allowedOrigin: settings.allowedOrigin, debugAllowedEmails: settings.debugEmails }),
    }, { provider })
    const accountId = aws.getCallerIdentityOutput({}, { provider }).accountId
    const hostLogGroup = new aws.cloudwatch.LogGroup(`${prefix}-host-logs`, {
        name: `${logGroupPrefix}/host`,
        retentionInDays: 30,
    }, { provider })
    const applicationLogGroup = new aws.cloudwatch.LogGroup(`${prefix}-application-logs`, {
        name: `${logGroupPrefix}/backend`,
        retentionInDays: 30,
    }, { provider })
    new aws.ecr.LifecyclePolicy(`${prefix}-backend-image-retention`, {
        repository: repository.name,
        policy: JSON.stringify({
            rules: [{
                rulePriority: 1,
                description: "Retain five most recent release images",
                selection: { tagStatus: "any", countType: "imageCountMoreThan", countNumber: 5 },
                action: { type: "expire" },
            }],
        }),
    }, { provider })

    const instanceRole = new aws.iam.Role(`${prefix}-backend-role`, {
        name: `${prefix}-backend-role`,
        assumeRolePolicy: aws.iam.assumeRolePolicyForPrincipal({ Service: "ec2.amazonaws.com" }),
    }, { provider })
    const ssmPolicy = new aws.iam.RolePolicyAttachment(`${prefix}-ssm-managed-policy`, {
        role: instanceRole.name,
        policyArn: "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore",
    }, { provider })
    const backendAccessPolicy = new aws.iam.RolePolicy(`${prefix}-backend-access-policy`, {
        role: instanceRole.id,
        policy: pulumi.jsonStringify({
            Version: "2012-10-17",
            Statement: [
                {
                    Effect: "Allow",
                    Action: "ecr:GetAuthorizationToken",
                    Resource: "*",
                },
                {
                    Effect: "Allow",
                    Action: ["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"],
                    Resource: repository.arn,
                },
                {
                    Effect: "Allow",
                    Action: ["secretsmanager:DescribeSecret", "secretsmanager:GetSecretValue"],
                    Resource: [settings.firebaseSecret.arn, settings.database.adminSecret.arn, settings.database.applicationSecret.arn],
                },
                {
                    Effect: "Allow",
                    Action: "ssm:GetParameter",
                    Resource: [
                        runtimeSettings.arn,
                        pulumi.interpolate`arn:aws:ssm:${settings.region}:${accountId}:parameter${imageRefParameterName}`,
                    ],
                },
                {
                    Effect: "Allow",
                    Action: "cloudwatch:PutMetricData",
                    Resource: "*",
                    Condition: { StringEquals: { "cloudwatch:namespace": "Finanssi2/EC2" } },
                },
                {
                    Effect: "Allow",
                    Action: ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"],
                    Resource: [pulumi.interpolate`${hostLogGroup.arn}:*`, pulumi.interpolate`${applicationLogGroup.arn}:*`],
                },
            ],
        }),
    }, { provider })
    const instanceProfile = new aws.iam.InstanceProfile(`${prefix}-backend-profile`, {
        role: instanceRole.name,
    }, { provider })

    const amiParameter = settings.architecture === "arm64"
        ? "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
        : "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
    const ami = aws.ssm.getParameterOutput({ name: amiParameter }, { provider }).value
    const userData = pulumi.all([
        repository.repositoryUrl,
        settings.firebaseSecret.arn,
        settings.database.adminSecret.arn,
        settings.database.applicationSecret.arn,
        runtimeSettings.name,
    ]).apply(([repoUrl, firebaseSecretArn, databaseAdminSecretArn, databaseApplicationSecretArn, runtimeSettingsName]) =>
        renderUserData({
            region: settings.region,
            apiHostname: settings.apiHostname,
            imageRefParameter: imageRefParameterName,
            runtimeSettingsParameter: runtimeSettingsName,
            repoUrl,
            firebaseSecretArn,
            databaseAdminSecretArn,
            databaseApplicationSecretArn,
            hostLogGroup: `${logGroupPrefix}/host`,
            applicationLogGroup: `${logGroupPrefix}/backend`,
        })
    )

    const networkInterface = new aws.ec2.NetworkInterface(`${prefix}-backend-eni`, {
        subnetId: settings.networking.publicSubnetIds[0],
        securityGroups: [settings.networking.backendSecurityGroup.id],
        tags: { Name: `${prefix}-backend-eni` },
    }, { provider })
    const elasticIp = new aws.ec2.Eip(`${prefix}-backend-ip`, {
        domain: "vpc",
        networkInterface: networkInterface.id,
        associateWithPrivateIp: networkInterface.privateIp,
        tags: { Name: `${prefix}-backend-ip` },
    }, { provider, protect: true }) // referenced by a manually maintained DNS record

    const instance = new aws.ec2.Instance(`${prefix}-backend`, {
        ami,
        instanceType: settings.instanceType,
        primaryNetworkInterface: { networkInterfaceId: networkInterface.id },
        iamInstanceProfile: instanceProfile.name,
        userData,
        userDataReplaceOnChange: true,
        monitoring: false,
        metadataOptions: { httpEndpoint: "enabled", httpTokens: "required", httpPutResponseHopLimit: 1 },
        creditSpecification: { cpuCredits: "standard" },
        rootBlockDevice: {
            volumeSize: 12,
            volumeType: "gp3",
            encrypted: true,
            deleteOnTermination: true,
        },
        tags: { Name: `${prefix}-backend` },
    }, {
        provider,
        dependsOn: [instanceProfile, ssmPolicy, backendAccessPolicy, elasticIp],
        deleteBeforeReplace: true,
        // Take AL2023 releases deliberately (pulumi up --replace) instead of replacing the host on a routine update.
        ignoreChanges: ["ami"],
    })

    return { instance, elasticIp, repository, hostLogGroup, applicationLogGroup, imageRefParameterName }
}

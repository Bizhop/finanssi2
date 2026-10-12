import * as aws from "@pulumi/aws"
import * as pulumi from "@pulumi/pulumi"
import type { Networking } from "./networking"
import type { Database } from "./database"

export interface Backend {
    instance: aws.ec2.Instance
    elasticIp: aws.ec2.Eip
    repository: aws.ecr.Repository
    hostLogGroup: aws.cloudwatch.LogGroup
    applicationLogGroup: aws.cloudwatch.LogGroup
}

function startupScript(values: {
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
}): string {
    const script = String.raw`#!/bin/bash
set -euo pipefail
umask 077

# Amazon Linux 2023 includes curl-minimal, which provides the curl command used below.
dnf install -y docker jq postgresql15 amazon-ssm-agent
curl -fsSL __CLOUDWATCH_AGENT_URL__ -o /tmp/amazon-cloudwatch-agent.rpm
dnf install -y /tmp/amazon-cloudwatch-agent.rpm
rm -f /tmp/amazon-cloudwatch-agent.rpm
curl -fsSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem -o /etc/ssl/certs/aws-rds-global-bundle.pem
echo 'fe45bbebf92ad3e27a583bbb2ddd1553c521ed4d49af5514dc0a40372ea5395c  /etc/ssl/certs/aws-rds-global-bundle.pem' | sha256sum --check --status
chmod 0644 /etc/ssl/certs/aws-rds-global-bundle.pem
case "$(uname -m)" in
    x86_64)
        caddy_arch=amd64
        caddy_sha512=a7a433a1b133efc3c8d10eb0b99d52a24b5ef5c322dc77f5282182b1c0402139ab83f3a99f0c52409df77d20123fb0b523edad8a66d8f5e49136197bf61ef0e7
        ;;
    aarch64)
        caddy_arch=arm64
        caddy_sha512=3db36ba90c7a6e8dda40ee3dd71fa08844c76b5fb08f61b31e5e78d2ed38e71c51dc7baed875e50d1ca1279196e84302967237386ae87c91ae9f2aaceada682e
        ;;
    *) echo "Unsupported Caddy host architecture: $(uname -m)" >&2; exit 1 ;;
esac
caddy_archive="caddy_2.11.7_linux_$caddy_arch.tar.gz"
curl -fsSL "https://github.com/caddyserver/caddy/releases/download/v2.11.7/$caddy_archive" -o "/tmp/$caddy_archive"
echo "$caddy_sha512  /tmp/$caddy_archive" | sha512sum --check --status
tar -xzf "/tmp/$caddy_archive" -C /usr/local/bin caddy
rm -f "/tmp/$caddy_archive"
chmod 0755 /usr/local/bin/caddy
getent group caddy >/dev/null || groupadd --system caddy
id -u caddy >/dev/null 2>&1 || useradd --system --gid caddy --home-dir /var/lib/caddy --shell /sbin/nologin caddy
install -d -o caddy -g caddy -m 0750 /var/lib/caddy /var/log/caddy
systemctl enable --now docker amazon-ssm-agent

install -d -m 0755 /etc/caddy
cat > /etc/caddy/Caddyfile <<'CADDY'
__API_HOSTNAME__ {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8080
}
CADDY
chmod 0644 /etc/caddy/Caddyfile
cat > /etc/systemd/system/caddy.service <<'CADDY_UNIT'
[Unit]
Description=Caddy web server
Documentation=https://caddyserver.com/docs/
After=network-online.target
Wants=network-online.target

[Service]
Type=notify
User=caddy
Group=caddy
Environment=HOME=/var/lib/caddy
ExecStart=/usr/local/bin/caddy run --environ --config /etc/caddy/Caddyfile --adapter caddyfile
ExecReload=/usr/local/bin/caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile --force
TimeoutStopSec=5s
LimitNOFILE=1048576
PrivateTmp=true
ProtectSystem=full
ReadWritePaths=/var/lib/caddy /var/log/caddy
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE
NoNewPrivileges=true
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=multi-user.target
CADDY_UNIT
systemctl daemon-reload
systemctl enable --now caddy

cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json <<'CWAGENT'
{
  "agent": { "metrics_collection_interval": 60, "run_as_user": "root" },
  "metrics": {
    "namespace": "Finanssi2/EC2",
    "append_dimensions": { "InstanceId": "__INSTANCE_ID_DIMENSION__" },
    "metrics_collected": {
      "mem": { "measurement": ["mem_used_percent"], "metrics_collection_interval": 60 },
      "disk": { "measurement": ["used_percent"], "resources": ["/"], "drop_device": true, "metrics_collection_interval": 60 }
    }
  },
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [{
          "file_path": "/var/log/messages",
          "log_group_name": "__HOST_LOG_GROUP__",
          "log_stream_name": "{instance_id}/messages",
          "timezone": "UTC"
        }]
      }
    }
  }
}
CWAGENT
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s

install -d -m 0755 /etc/finanssi2 /usr/local/sbin
install -d -m 0755 /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/finanssi2.conf <<'JOURNAL'
[Journal]
SystemMaxUse=200M
RuntimeMaxUse=64M
MaxRetentionSec=14day
JOURNAL
systemctl restart systemd-journald
cat > /etc/finanssi2/host.env <<'ENV'
AWS_REGION=__AWS_REGION__
ECR_REPOSITORY_URL=__ECR_REPOSITORY_URL__
FIREBASE_ADMIN_SECRET_ARN=__FIREBASE_ADMIN_SECRET_ARN__
DATABASE_ADMIN_SECRET_ARN=__DATABASE_ADMIN_SECRET_ARN__
DATABASE_APPLICATION_SECRET_ARN=__DATABASE_APPLICATION_SECRET_ARN__
ALLOWED_ORIGIN=__ALLOWED_ORIGIN__
DEBUG_ALLOWED_EMAILS=__DEBUG_ALLOWED_EMAILS__
ENV
chmod 0600 /etc/finanssi2/host.env

cat > /usr/local/sbin/finanssi2-image-ready <<'CHECK'
#!/bin/bash
set -eu
test -s /etc/finanssi2/image-ref
IMAGE_REF=$(cat /etc/finanssi2/image-ref)
source /etc/finanssi2/host.env
case "$IMAGE_REF" in
    "$ECR_REPOSITORY_URL"@sha256:*)
        DIGEST=$(printf '%s' "$IMAGE_REF" | rev | cut -d: -f1)
        [[ "$DIGEST" =~ ^[a-f0-9]{64}$ ]] && exit 0
        ;;
esac
exit 1
CHECK

cat > /usr/local/sbin/finanssi2-load-secrets <<'SECRETS'
#!/bin/bash
set -euo pipefail
set +x
source /etc/finanssi2/host.env
umask 077
install -d -o root -g root -m 0700 /run/finanssi2
install -d -o 10001 -g 10001 -m 0700 /run/finanssi2/config

aws secretsmanager get-secret-value --region "$AWS_REGION" --secret-id "$FIREBASE_ADMIN_SECRET_ARN" --query SecretString --output text > /run/finanssi2/firebase-admin.json
aws secretsmanager get-secret-value --region "$AWS_REGION" --secret-id "$DATABASE_ADMIN_SECRET_ARN" --query SecretString --output text > /run/finanssi2/admin-db.json
aws secretsmanager get-secret-value --region "$AWS_REGION" --secret-id "$DATABASE_APPLICATION_SECRET_ARN" --query SecretString --output text > /run/finanssi2/app-db.json
chmod 0600 /run/finanssi2/admin-db.json /run/finanssi2/app-db.json

DB_HOST=$(jq -er '.host' /run/finanssi2/admin-db.json)
DB_PORT=$(jq -er '.port' /run/finanssi2/admin-db.json)
DB_NAME=$(jq -er '.dbname' /run/finanssi2/admin-db.json)
ADMIN_USER=$(jq -er '.username' /run/finanssi2/admin-db.json)
ADMIN_PASSWORD=$(jq -er '.password' /run/finanssi2/admin-db.json)
APP_USER=$(jq -er '.username' /run/finanssi2/app-db.json)
APP_PASSWORD=$(jq -er '.password' /run/finanssi2/app-db.json)

printf '%s' "jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME" > /run/finanssi2/config/spring.datasource.url
printf '%s' "$APP_USER" > /run/finanssi2/config/spring.datasource.username
printf '%s' "$APP_PASSWORD" > /run/finanssi2/config/spring.datasource.password
cp /run/finanssi2/firebase-admin.json /run/finanssi2/config/firebase-admin.json
chown 10001:10001 /run/finanssi2/config/*
chmod 0400 /run/finanssi2/config/*

ESCAPED_ADMIN_PASSWORD=$(printf '%s' "$ADMIN_PASSWORD" | sed 's/:/\\:/g')
printf '%s:%s:%s:%s:%s\n' "$DB_HOST" "$DB_PORT" "$DB_NAME" "$ADMIN_USER" "$ESCAPED_ADMIN_PASSWORD" > /run/finanssi2/admin.pgpass
chmod 0600 /run/finanssi2/admin.pgpass
export PGPASSFILE=/run/finanssi2/admin.pgpass
connected=0
for attempt in $(seq 1 20); do
    if psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$ADMIN_USER connect_timeout=60 sslmode=verify-full sslrootcert=/etc/ssl/certs/aws-rds-global-bundle.pem" -w -v ON_ERROR_STOP=1 -Atc 'select 1' >/dev/null 2>&1; then
        connected=1
        break
    fi
    sleep 15
done
if [ "$connected" -ne 1 ]; then
    echo 'Unable to connect to Aurora after retrying' >&2
    exit 1
fi

case "$APP_PASSWORD" in *"'"*|*\\*) echo 'Invalid generated application database password' >&2; exit 1 ;; esac
psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$ADMIN_USER connect_timeout=60 sslmode=verify-full sslrootcert=/etc/ssl/certs/aws-rds-global-bundle.pem" -w -v ON_ERROR_STOP=1 <<SQL
SELECT format('CREATE ROLE finanssi_app LOGIN PASSWORD %L', '$APP_PASSWORD')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'finanssi_app') \gexec
SELECT format('ALTER ROLE finanssi_app LOGIN PASSWORD %L', '$APP_PASSWORD') \gexec
ALTER DATABASE finanssi OWNER TO finanssi_app;
GRANT ALL PRIVILEGES ON DATABASE finanssi TO finanssi_app;
GRANT ALL PRIVILEGES ON SCHEMA public TO finanssi_app;
SQL

rm -f /run/finanssi2/admin-db.json /run/finanssi2/app-db.json /run/finanssi2/firebase-admin.json /run/finanssi2/admin.pgpass
unset ADMIN_PASSWORD APP_PASSWORD
SECRETS

cat > /usr/local/sbin/finanssi2-run <<'RUN'
#!/bin/bash
set -euo pipefail
source /etc/finanssi2/host.env
IMAGE_REF=$(cat /etc/finanssi2/image-ref)
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REPOSITORY_URL"
docker pull "$IMAGE_REF"
exec docker run --rm --name finanssi2 \
    --memory=1280m --pids-limit=512 \
    --log-driver=awslogs \
    --log-opt awslogs-region="$AWS_REGION" \
    --log-opt awslogs-group=__APPLICATION_LOG_GROUP__ \
    --log-opt awslogs-create-group=false \
    --log-opt awslogs-stream="$(cat /var/lib/cloud/data/instance-id)/finanssi2-backend" \
    --publish 127.0.0.1:8080:8080 \
    --env SPRING_PROFILES_ACTIVE=prod \
    --env JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=40.0 \
    --env FINANSSI_ALLOWED_ORIGIN="$ALLOWED_ORIGIN" \
    --env FINANSSI_DEBUG_ALLOWED_EMAILS="$DEBUG_ALLOWED_EMAILS" \
    --env GOOGLE_APPLICATION_CREDENTIALS=/run/finanssi2/config/firebase-admin.json \
    --mount type=bind,source=/run/finanssi2/config,target=/run/finanssi2/config,readonly \
    "$IMAGE_REF"
RUN
chmod 0755 /usr/local/sbin/finanssi2-image-ready /usr/local/sbin/finanssi2-load-secrets /usr/local/sbin/finanssi2-run

cat > /etc/systemd/system/finanssi2-secrets.service <<'UNIT'
[Unit]
Description=Load Finanssi runtime secrets and prepare the application database role
After=network-online.target
Wants=network-online.target
ConditionPathExists=/etc/finanssi2/image-ref
ConditionPathExists=/usr/local/sbin/finanssi2-image-ready

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/finanssi2-image-ready
ExecStart=/usr/local/sbin/finanssi2-load-secrets
RemainAfterExit=yes
UNIT

cat > /etc/systemd/system/finanssi2.service <<'UNIT'
[Unit]
Description=Finanssi 2 backend container
After=docker.service finanssi2-secrets.service
Requires=docker.service finanssi2-secrets.service
ConditionPathExists=/etc/finanssi2/image-ref

[Service]
Type=simple
ExecStartPre=/usr/local/sbin/finanssi2-image-ready
ExecStartPre=/usr/bin/logger -t finanssi2 'backend container start'
ExecStart=/usr/local/sbin/finanssi2-run
ExecStop=/usr/bin/docker stop -t 60 finanssi2
Restart=always
RestartSec=10
TimeoutStartSec=600
TimeoutStopSec=90

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable finanssi2-secrets.service finanssi2.service
systemctl start finanssi2-secrets.service || true
systemctl start finanssi2.service || true
`

    return script
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
        policy: pulumi.all([
            repository.arn,
            settings.firebaseSecret.arn,
            settings.database.adminSecret.arn,
            settings.database.applicationSecret.arn,
            hostLogGroup.arn,
            applicationLogGroup.arn,
        ]).apply(([repoArn, firebaseArn, dbAdminArn, dbAppArn, hostLogArn, applicationLogArn]) =>
            JSON.stringify({
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
                        Resource: repoArn,
                    },
                    {
                        Effect: "Allow",
                        Action: ["secretsmanager:DescribeSecret", "secretsmanager:GetSecretValue"],
                        Resource: [firebaseArn, dbAdminArn, dbAppArn],
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
                        Resource: [`${hostLogArn}:*`, `${applicationLogArn}:*`],
                    },
                ],
            })
        ),
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
    ]).apply(([repoUrl, firebaseSecretArn, databaseAdminSecretArn, databaseApplicationSecretArn]) =>
        startupScript({
            region: settings.region,
            apiHostname: settings.apiHostname,
            allowedOrigin: settings.allowedOrigin,
            debugEmails: settings.debugEmails,
            repoUrl,
            firebaseSecretArn,
            databaseAdminSecretArn,
            databaseApplicationSecretArn,
            cloudWatchAgentUrl: `https://amazoncloudwatch-agent.s3.amazonaws.com/amazon_linux/${
                settings.architecture === "arm64" ? "arm64" : "amd64"
            }/latest/amazon-cloudwatch-agent.rpm`,
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

    return { instance, elasticIp, repository, hostLogGroup, applicationLogGroup }
}

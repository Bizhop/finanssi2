#!/bin/bash
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
# @include host/finanssi2-image-ready
CHECK

cat > /usr/local/sbin/finanssi2-load-secrets <<'SECRETS'
# @include host/finanssi2-load-secrets
SECRETS

cat > /usr/local/sbin/finanssi2-run <<'RUN'
# @include host/finanssi2-run
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

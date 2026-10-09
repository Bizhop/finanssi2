import * as aws from "@pulumi/aws"
import * as pulumi from "@pulumi/pulumi"

export function createFirebaseAdminSecret(
    prefix: string,
    provider: aws.Provider,
    firebaseAdminJson: pulumi.Input<string>,
): aws.secretsmanager.Secret {
    const secret = new aws.secretsmanager.Secret(`${prefix}-firebase-admin-secret`, {
        name: `${prefix}-firebase-admin`,
        description: "Firebase Admin service account for the production backend",
        recoveryWindowInDays: 0,
        tags: { Application: "finanssi2" },
    }, { provider })
    new aws.secretsmanager.SecretVersion(`${prefix}-firebase-admin-version`, {
        secretId: secret.id,
        secretString: firebaseAdminJson,
    }, { provider })
    return secret
}

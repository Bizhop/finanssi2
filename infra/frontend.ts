import * as aws from "@pulumi/aws"
import * as pulumi from "@pulumi/pulumi"

export interface Frontend {
    bucket: aws.s3.BucketV2
    certificate: aws.acm.Certificate
    distribution?: aws.cloudfront.Distribution
}

export function createFrontend(
    prefix: string,
    provider: aws.Provider,
    certificateProvider: aws.Provider,
    settings: { hostname: string; createDistribution: boolean },
): Frontend {
    const bucket = new aws.s3.BucketV2(`${prefix}-frontend`, {
        bucket: `${prefix}-frontend`,
        forceDestroy: true,
        tags: { Application: "finanssi2" },
    }, { provider })
    new aws.s3.BucketPublicAccessBlock(`${prefix}-frontend-public-access-block`, {
        bucket: bucket.id,
        blockPublicAcls: true,
        blockPublicPolicy: true,
        ignorePublicAcls: true,
        restrictPublicBuckets: true,
    }, { provider })
    new aws.s3.BucketOwnershipControls(`${prefix}-frontend-ownership`, {
        bucket: bucket.id,
        rule: { objectOwnership: "BucketOwnerEnforced" },
    }, { provider })
    new aws.s3.BucketServerSideEncryptionConfigurationV2(`${prefix}-frontend-encryption`, {
        bucket: bucket.id,
        rules: [{ applyServerSideEncryptionByDefault: { sseAlgorithm: "AES256" } }],
    }, { provider })
    new aws.s3.BucketLifecycleConfigurationV2(`${prefix}-frontend-asset-retention`, {
        bucket: bucket.id,
        rules: [{
            id: "expire-old-hashed-assets",
            status: "Enabled",
            filter: { prefix: "assets/" },
            expiration: { days: 90 },
        }],
    }, { provider })

    const originAccessControl = new aws.cloudfront.OriginAccessControl(`${prefix}-frontend-oac`, {
        name: `${prefix}-frontend-oac`,
        description: "CloudFront access to the private Finanssi frontend bucket",
        originAccessControlOriginType: "s3",
        signingBehavior: "always",
        signingProtocol: "sigv4",
    }, { provider })

    const certificate = new aws.acm.Certificate(`${prefix}-frontend-certificate`, {
        domainName: settings.hostname,
        validationMethod: "DNS",
        tags: { Application: "finanssi2" },
    }, { provider: certificateProvider })

    let distribution: aws.cloudfront.Distribution | undefined
    if (settings.createDistribution) {
        const validation = new aws.acm.CertificateValidation(`${prefix}-frontend-certificate-validation`, {
            certificateArn: certificate.arn,
            validationRecordFqdns: certificate.domainValidationOptions.apply((options) => options.map((option) => option.resourceRecordName)),
        }, { provider: certificateProvider })

        const routeFunction = new aws.cloudfront.Function(`${prefix}-spa-route-rewrite`, {
            name: `${prefix}-spa-route-rewrite`,
            runtime: "cloudfront-js-2.0",
            publish: true,
            code: `function handler(event) {
    var request = event.request;
    if (request.uri === "/games" || request.uri.indexOf("/games/") === 0) {
        request.uri = "/index.html";
    }
    return request;
}`,
            comment: "Rewrite game routes to the SPA document while preserving missing asset errors",
        }, { provider })
        const cachePolicy = new aws.cloudfront.CachePolicy(`${prefix}-frontend-cache`, {
            name: `${prefix}-frontend-cache`,
            comment: "Respect Cache-Control while supporting long-lived hashed assets",
            minTtl: 0,
            defaultTtl: 0,
            maxTtl: 31536000,
            parametersInCacheKeyAndForwardedToOrigin: {
                enableAcceptEncodingGzip: true,
                enableAcceptEncodingBrotli: true,
                cookiesConfig: { cookieBehavior: "none" },
                headersConfig: { headerBehavior: "none" },
                queryStringsConfig: { queryStringBehavior: "none" },
            },
        }, { provider })

        distribution = new aws.cloudfront.Distribution(`${prefix}-frontend-cdn`, {
            enabled: true,
            comment: "Finanssi 2 frontend",
            aliases: [settings.hostname],
            defaultRootObject: "index.html",
            origins: [{
                originId: "private-frontend-s3",
                domainName: bucket.bucketRegionalDomainName,
                originAccessControlId: originAccessControl.id,
            }],
            defaultCacheBehavior: {
                targetOriginId: "private-frontend-s3",
                viewerProtocolPolicy: "redirect-to-https",
                allowedMethods: ["GET", "HEAD", "OPTIONS"],
                cachedMethods: ["GET", "HEAD", "OPTIONS"],
                compress: true,
                cachePolicyId: cachePolicy.id,
                functionAssociations: [{ eventType: "viewer-request", functionArn: routeFunction.arn }],
            },
            restrictions: { geoRestriction: { restrictionType: "none" } },
            viewerCertificate: {
                acmCertificateArn: validation.certificateArn,
                sslSupportMethod: "sni-only",
                minimumProtocolVersion: "TLSv1.2_2021",
            },
            priceClass: "PriceClass_100",
            tags: { Application: "finanssi2" },
        }, { provider, dependsOn: [validation] })

        const bucketPolicy = new aws.s3.BucketPolicy(`${prefix}-frontend-cdn-policy`, {
            bucket: bucket.id,
            policy: pulumi.all([bucket.arn, distribution.arn]).apply(([bucketArn, distributionArn]) => JSON.stringify({
                Version: "2012-10-17",
                Statement: [{
                    Sid: "AllowCloudFrontReadOnly",
                    Effect: "Allow",
                    Principal: { Service: "cloudfront.amazonaws.com" },
                    Action: "s3:GetObject",
                    Resource: `${bucketArn}/*`,
                    Condition: { StringEquals: { "AWS:SourceArn": distributionArn } },
                }],
            })),
        }, { provider, dependsOn: [distribution] })
        void bucketPolicy
    }

    return { bucket, certificate, distribution }
}

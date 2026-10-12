import * as aws from "@pulumi/aws"
import * as pulumi from "@pulumi/pulumi"

export interface Frontend {
    bucket: aws.s3.Bucket
    certificate: aws.acm.Certificate
    distribution?: aws.cloudfront.Distribution | undefined
}

export function createFrontend(
    prefix: string,
    provider: aws.Provider,
    usEast1Provider: aws.Provider,
    settings: {
        hostname: string
        apiHostname: string
        firebaseAuthDomain: string
        createDistribution: boolean
        cspReportOnly: boolean
    },
): Frontend {
    const bucket = new aws.s3.Bucket(`${prefix}-frontend`, {
        bucket: `${prefix}-frontend`,
        forceDestroy: true,
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
    new aws.s3.BucketServerSideEncryptionConfiguration(`${prefix}-frontend-encryption`, {
        bucket: bucket.id,
        rules: [{ applyServerSideEncryptionByDefault: { sseAlgorithm: "AES256" } }],
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
    }, { provider: usEast1Provider, protect: true }) // referenced by manually maintained DNS records

    let distribution: aws.cloudfront.Distribution | undefined
    if (settings.createDistribution) {
        const validation = new aws.acm.CertificateValidation(`${prefix}-frontend-certificate-validation`, {
            certificateArn: certificate.arn,
            validationRecordFqdns: certificate.domainValidationOptions.apply((options) => options.map((option) => option.resourceRecordName)),
        }, { provider: usEast1Provider })

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

        // Allows the API (REST and websocket), Firebase sign-in (identity endpoints, the gapi script, the auth-domain iframe) and nothing else.
        // No Cross-Origin-Opener-Policy: it would break the Google sign-in popup.
        const contentSecurityPolicy = [
            "default-src 'none'",
            "script-src 'self' https://apis.google.com",
            "style-src 'self' 'unsafe-inline'",
            // Provider avatars (Google profile photos) are loaded straight from Google.
            `img-src 'self' data: blob: https://${settings.apiHostname} https://*.googleusercontent.com`,
            "font-src 'self' data:",
            `connect-src 'self' https://${settings.apiHostname} wss://${settings.apiHostname} https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://www.googleapis.com`,
            `frame-src https://${settings.firebaseAuthDomain} https://accounts.google.com`,
            "base-uri 'self'",
            "form-action 'self'",
            "object-src 'none'",
            "frame-ancestors 'none'",
        ].join("; ")
        const responseHeadersPolicy = new aws.cloudfront.ResponseHeadersPolicy(`${prefix}-frontend-headers`, {
            name: `${prefix}-frontend-headers`,
            comment: "Security headers for the Finanssi frontend",
            securityHeadersConfig: {
                strictTransportSecurity: { override: true, accessControlMaxAgeSec: 31536000, includeSubdomains: false, preload: false },
                contentTypeOptions: { override: true },
                frameOptions: { override: true, frameOption: "DENY" },
                referrerPolicy: { override: true, referrerPolicy: "strict-origin-when-cross-origin" },
                // Report-only mode lets a changed policy be tried against real sign-ins without blocking anything.
                ...(settings.cspReportOnly ? {} : { contentSecurityPolicy: { override: true, contentSecurityPolicy } }),
            },
            ...(settings.cspReportOnly
                ? { customHeadersConfig: { items: [{ header: "Content-Security-Policy-Report-Only", override: true, value: contentSecurityPolicy }] } }
                : {}),
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
                responseHeadersPolicyId: responseHeadersPolicy.id,
                functionAssociations: [{ eventType: "viewer-request", functionArn: routeFunction.arn }],
            },
            restrictions: { geoRestriction: { restrictionType: "none" } },
            viewerCertificate: {
                acmCertificateArn: validation.certificateArn,
                sslSupportMethod: "sni-only",
                minimumProtocolVersion: "TLSv1.2_2021",
            },
            priceClass: "PriceClass_100",
            httpVersion: "http2and3",
            isIpv6Enabled: true,
        }, { provider, dependsOn: [validation] })

        new aws.s3.BucketPolicy(`${prefix}-frontend-cdn-policy`, {
            bucket: bucket.id,
            policy: pulumi.jsonStringify({
                Version: "2012-10-17",
                Statement: [{
                    Sid: "AllowCloudFrontReadOnly",
                    Effect: "Allow",
                    Principal: { Service: "cloudfront.amazonaws.com" },
                    Action: "s3:GetObject",
                    Resource: pulumi.interpolate`${bucket.arn}/*`,
                    Condition: { StringEquals: { "AWS:SourceArn": distribution.arn } },
                }],
            }),
        }, { provider, dependsOn: [distribution] })
    }

    return { bucket, certificate, distribution }
}

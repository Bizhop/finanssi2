import * as aws from "@pulumi/aws"
import * as pulumi from "@pulumi/pulumi"

export interface Networking {
    vpc: aws.ec2.Vpc
    backendSecurityGroup: aws.ec2.SecurityGroup
    databaseSecurityGroup: aws.ec2.SecurityGroup
    publicSubnetIds: pulumi.Output<string>[]
    privateSubnetIds: pulumi.Output<string>[]
}

export function createNetworking(prefix: string, provider: aws.Provider): Networking {
    const zones = aws.getAvailabilityZonesOutput({ state: "available" }, { provider }).names
    const zoneA = zones.apply((names) => {
        if (names.length < 2) throw new Error("The selected AWS region must have at least two available availability zones")
        return names[0]
    })
    const zoneB = zones.apply((names) => {
        if (names.length < 2) throw new Error("The selected AWS region must have at least two available availability zones")
        return names[1]
    })

    const vpc = new aws.ec2.Vpc(`${prefix}-vpc`, {
        cidrBlock: "10.42.0.0/16",
        enableDnsHostnames: true,
        enableDnsSupport: true,
        tags: { Name: `${prefix}-vpc` },
    }, { provider })

    const internetGateway = new aws.ec2.InternetGateway(`${prefix}-igw`, {
        vpcId: vpc.id,
        tags: { Name: `${prefix}-igw` },
    }, { provider })

    const publicA = new aws.ec2.Subnet(`${prefix}-public-a`, {
        vpcId: vpc.id,
        cidrBlock: "10.42.0.0/24",
        availabilityZone: zoneA,
        mapPublicIpOnLaunch: false,
        tags: { Name: `${prefix}-public-a`, Tier: "public" },
    }, { provider })
    const publicB = new aws.ec2.Subnet(`${prefix}-public-b`, {
        vpcId: vpc.id,
        cidrBlock: "10.42.1.0/24",
        availabilityZone: zoneB,
        mapPublicIpOnLaunch: false,
        tags: { Name: `${prefix}-public-b`, Tier: "public" },
    }, { provider })
    const databaseA = new aws.ec2.Subnet(`${prefix}-database-a`, {
        vpcId: vpc.id,
        cidrBlock: "10.42.10.0/24",
        availabilityZone: zoneA,
        mapPublicIpOnLaunch: false,
        tags: { Name: `${prefix}-database-a`, Tier: "private" },
    }, { provider })
    const databaseB = new aws.ec2.Subnet(`${prefix}-database-b`, {
        vpcId: vpc.id,
        cidrBlock: "10.42.11.0/24",
        availabilityZone: zoneB,
        mapPublicIpOnLaunch: false,
        tags: { Name: `${prefix}-database-b`, Tier: "private" },
    }, { provider })

    const publicRouteTable = new aws.ec2.RouteTable(`${prefix}-public-routes`, {
        vpcId: vpc.id,
        routes: [{ cidrBlock: "0.0.0.0/0", gatewayId: internetGateway.id }],
        tags: { Name: `${prefix}-public-routes` },
    }, { provider })
    for (const [label, subnet] of [["a", publicA], ["b", publicB]] as const) {
        new aws.ec2.RouteTableAssociation(`${prefix}-public-route-${label}`, {
            subnetId: subnet.id,
            routeTableId: publicRouteTable.id,
        }, { provider })
    }

    const backendSecurityGroup = new aws.ec2.SecurityGroup(`${prefix}-backend-sg`, {
        vpcId: vpc.id,
        description: "Public HTTPS proxy; no direct Spring or SSH ingress",
        ingress: [
            { protocol: "tcp", fromPort: 80, toPort: 80, cidrBlocks: ["0.0.0.0/0"] },
            { protocol: "tcp", fromPort: 443, toPort: 443, cidrBlocks: ["0.0.0.0/0"] },
        ],
        egress: [{ protocol: "-1", fromPort: 0, toPort: 0, cidrBlocks: ["0.0.0.0/0"] }],
        tags: { Name: `${prefix}-backend-sg` },
    }, { provider })

    const databaseSecurityGroup = new aws.ec2.SecurityGroup(`${prefix}-database-sg`, {
        vpcId: vpc.id,
        description: "PostgreSQL ingress from the single backend instance only",
        ingress: [{ protocol: "tcp", fromPort: 5432, toPort: 5432, securityGroups: [backendSecurityGroup.id] }],
        tags: { Name: `${prefix}-database-sg` },
    }, { provider })

    return {
        vpc,
        backendSecurityGroup,
        databaseSecurityGroup,
        publicSubnetIds: [publicA.id, publicB.id],
        privateSubnetIds: [databaseA.id, databaseB.id],
    }
}

import * as aws from "@pulumi/aws"

export function createMonitoring(
    prefix: string,
    provider: aws.Provider,
    budgetProvider: aws.Provider,
    settings: {
        instance: aws.ec2.Instance
        hostLogGroup: aws.cloudwatch.LogGroup
        monthlyBudgetUsd: number
        alertEmail: string
    },
): void {
    const alarmTopic = new aws.sns.Topic(`${prefix}-alarm-notifications`, {
        name: `${prefix}-alarms`,
        tags: { Application: "finanssi2" },
    }, { provider })
    new aws.sns.TopicSubscription(`${prefix}-alarm-email-subscription`, {
        topic: alarmTopic.arn,
        protocol: "email",
        endpoint: settings.alertEmail,
    }, { provider })

    new aws.cloudwatch.MetricAlarm(`${prefix}-instance-status-alarm`, {
        name: `${prefix}-instance-status-failed`,
        alarmDescription: "EC2 system or instance status check failed",
        namespace: "AWS/EC2",
        metricName: "StatusCheckFailed",
        dimensions: { InstanceId: settings.instance.id },
        statistic: "Maximum",
        period: 300,
        evaluationPeriods: 2,
        threshold: 1,
        comparisonOperator: "GreaterThanOrEqualToThreshold",
        treatMissingData: "notBreaching",
        alarmActions: [alarmTopic.arn],
    }, { provider })
    new aws.cloudwatch.MetricAlarm(`${prefix}-cpu-alarm`, {
        name: `${prefix}-cpu-high`,
        alarmDescription: "Backend EC2 CPU has stayed high",
        namespace: "AWS/EC2",
        metricName: "CPUUtilization",
        dimensions: { InstanceId: settings.instance.id },
        statistic: "Average",
        period: 300,
        evaluationPeriods: 3,
        threshold: 80,
        comparisonOperator: "GreaterThanOrEqualToThreshold",
        treatMissingData: "notBreaching",
        alarmActions: [alarmTopic.arn],
    }, { provider })
    new aws.cloudwatch.MetricAlarm(`${prefix}-cpu-credit-alarm`, {
        name: `${prefix}-cpu-credit-low`,
        alarmDescription: "T3 CPU credit balance is low",
        namespace: "AWS/EC2",
        metricName: "CPUCreditBalance",
        dimensions: { InstanceId: settings.instance.id },
        statistic: "Minimum",
        period: 300,
        evaluationPeriods: 2,
        threshold: 10,
        comparisonOperator: "LessThanOrEqualToThreshold",
        treatMissingData: "notBreaching",
        alarmActions: [alarmTopic.arn],
    }, { provider })
    new aws.cloudwatch.MetricAlarm(`${prefix}-memory-alarm`, {
        name: `${prefix}-memory-high`,
        alarmDescription: "Backend EC2 memory use has stayed high",
        namespace: "Finanssi2/EC2",
        metricName: "mem_used_percent",
        dimensions: { InstanceId: settings.instance.id },
        statistic: "Average",
        period: 300,
        evaluationPeriods: 3,
        threshold: 85,
        comparisonOperator: "GreaterThanOrEqualToThreshold",
        treatMissingData: "notBreaching",
        alarmActions: [alarmTopic.arn],
    }, { provider })
    new aws.cloudwatch.MetricAlarm(`${prefix}-disk-alarm`, {
        name: `${prefix}-root-disk-high`,
        alarmDescription: "Backend root disk use has stayed high",
        namespace: "Finanssi2/EC2",
        metricName: "disk_used_percent",
        dimensions: { InstanceId: settings.instance.id, path: "/", fstype: "xfs" },
        statistic: "Average",
        period: 300,
        evaluationPeriods: 3,
        threshold: 80,
        comparisonOperator: "GreaterThanOrEqualToThreshold",
        treatMissingData: "notBreaching",
        alarmActions: [alarmTopic.arn],
    }, { provider })

    new aws.cloudwatch.LogMetricFilter(`${prefix}-backend-start-filter`, {
        name: `${prefix}-backend-starts`,
        logGroupName: settings.hostLogGroup.name,
        pattern: '"backend container start"',
        metricTransformation: {
            name: "BackendStarts",
            namespace: "Finanssi2/Host",
            value: "1",
        },
    }, { provider })
    new aws.cloudwatch.MetricAlarm(`${prefix}-backend-restart-alarm`, {
        name: `${prefix}-backend-restarts`,
        alarmDescription: "Backend container has started four or more times in five minutes",
        namespace: "Finanssi2/Host",
        metricName: "BackendStarts",
        statistic: "Sum",
        period: 300,
        evaluationPeriods: 1,
        threshold: 4,
        comparisonOperator: "GreaterThanOrEqualToThreshold",
        treatMissingData: "notBreaching",
        alarmActions: [alarmTopic.arn],
    }, { provider })

    new aws.budgets.Budget(`${prefix}-monthly-budget`, {
        name: `${prefix}-monthly-cost-alert`,
        budgetType: "COST",
        limitAmount: settings.monthlyBudgetUsd.toFixed(2),
        limitUnit: "USD",
        timeUnit: "MONTHLY",
        notifications: [
            {
                comparisonOperator: "GREATER_THAN",
                threshold: 80,
                thresholdType: "PERCENTAGE",
                notificationType: "ACTUAL",
                subscriberEmailAddresses: [settings.alertEmail],
            },
            {
                comparisonOperator: "GREATER_THAN",
                threshold: 100,
                thresholdType: "PERCENTAGE",
                notificationType: "FORECASTED",
                subscriberEmailAddresses: [settings.alertEmail],
            },
        ],
    }, { provider: budgetProvider })

}

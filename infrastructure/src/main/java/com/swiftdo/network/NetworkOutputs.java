package com.swiftdo.network;

/**
 * Defines the canonical keys used to store and retrieve
 * network-related values from SSM Parameter Store.
 *
 * <p>
 * Parameter names are combined with environment prefixes
 * at runtime.
 * </p>
 */
public class NetworkOutputs {

    private NetworkOutputs() {
        // Prevent instantiation
    }

    public static final String PARAMETER_VPC_ID = "vpcId";
    public static final String PARAMETER_HTTP_LISTENER = "httpListenerArn";
    public static final String PARAMETER_HTTPS_LISTENER = "httpsListenerArn";
    public static final String PARAMETER_LOADBALANCER_SECURITY_GROUP_ID = "loadBalancerSecurityGroupId";
    public static final String PARAMETER_ECS_CLUSTER_NAME = "ecsClusterName";
    public static final String PARAMETER_AVAILABILITY_ZONES = "availabilityZones";
    public static final String PARAMETER_PUBLIC_SUBNETS = "publicSubnetIds";
    public static final String PARAMETER_PRIVATE_SUBNETS = "privateSubnetIds";
    public static final String PARAMETER_LOAD_BALANCER_ARN = "loadBalancerArn";
    public static final String PARAMETER_LOAD_BALANCER_DNS_NAME = "loadBalancerDnsName";
    public static final String PARAMETER_LOAD_BALANCER_HOSTED_ZONE_ID = "loadBalancerCanonicalHostedZoneId";
}

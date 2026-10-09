package com.swiftdo.network;

import java.util.List;
import java.util.Optional;

/**
 * Immutable value object representing all network-related outputs
 * produced by the {@link NetworkConstruct}.
 *
 * <p>
 * This class aggregates identifiers and attributes required by
 * downstream stacks such as ECS services or application layers.
 * </p>
 *
 * <p>
 * Instances of this class are typically constructed via
 * {@link NetworkParameterStore#load}.
 * </p>
 */
public class NetworkOutputParameters {

    /** VPC identifier used as the network boundary. */
    private final String vpcId;

    /** ARN of the HTTP listener attached to the load balancer. */
    private final String httpListenerArn;

    /**
     * ARN of the HTTPS listener, if configured.
     *
     * <p>
     * Optional because HTTPS may not be enabled
     * in all environments.
     * </p>
     */
    private final Optional<String> httpsListenerArn;

    /** Security group ID associated with the Application Load Balancer. */
    private final String loadbalancerSecurityGroupId;

    /** Logical name of the ECS Cluster bound to the network. */
    private final String ecsClusterName;

    /** IDs of private subnets. */
    private final List<String> privateSubnets;

    /** IDs of public subnets used for ingress. */
    private final List<String> publicSubnets;

    /** Availability zones used by the VPC. */
    private final List<String> availabilityZones;

    /** ARN of the Application Load Balancer. */
    private final String loadBalancerArn;

    /** Public DNS name of the Application Load Balancer. */
    private final String loadBalancerDnsName;

    /** Hosted zone ID required for Route 53 alias records. */
    private final String loadBalancerCanonicalHostedZoneId;

    /**
     * Creates a fully populated network output object.
     *
     * <p>
     * All values are expected to originate from SSM Parameter Store
     * and represent already-provisioned infrastructure.
     * </p>
     */
    public NetworkOutputParameters(
            String vpcId,
            String httpListenerArn,
            Optional<String> httpsListenerArn,
            String loadbalancerSecurityGroupId,
            String ecsClusterName,
            List<String> privateSubnets,
            List<String> publicSubnets,
            List<String> availabilityZones,
            String loadBalancerArn,
            String loadBalancerDnsName,
            String loadBalancerCanonicalHostedZoneId
    ) {
        this.vpcId = vpcId;
        this.httpListenerArn = httpListenerArn;
        this.httpsListenerArn = httpsListenerArn;
        this.loadbalancerSecurityGroupId = loadbalancerSecurityGroupId;
        this.ecsClusterName = ecsClusterName;
        this.privateSubnets = privateSubnets;
        this.publicSubnets = publicSubnets;
        this.availabilityZones = availabilityZones;
        this.loadBalancerArn = loadBalancerArn;
        this.loadBalancerDnsName = loadBalancerDnsName;
        this.loadBalancerCanonicalHostedZoneId = loadBalancerCanonicalHostedZoneId;
    }

    /** @return VPC identifier */
    public String getVpcId() {
        return this.vpcId;
    }

    /** @return HTTP listener ARN */
    public String getHttpListenerArn() {
        return this.httpListenerArn;
    }

    /** @return optional HTTPS listener ARN */
    public Optional<String> getHttpsListenerArn() {
        return this.httpsListenerArn;
    }

    /** @return load balancer security group ID */
    public String getLoadbalancerSecurityGroupId() {
        return this.loadbalancerSecurityGroupId;
    }

    /** @return ECS cluster name */
    public String getEcsClusterName() {
        return this.ecsClusterName;
    }

    /** @return isolated subnet IDs */
    public List<String> getPrivateSubnets() {
        return this.privateSubnets;
    }

    /** @return public subnet IDs */
    public List<String> getPublicSubnets() {
        return this.publicSubnets;
    }

    /** @return availability zones */
    public List<String> getAvailabilityZones() {
        return this.availabilityZones;
    }

    /** @return load balancer ARN */
    public String getLoadBalancerArn() {
        return this.loadBalancerArn;
    }

    /** @return load balancer DNS name */
    public String getLoadBalancerDnsName() {
        return loadBalancerDnsName;
    }

    /** @return canonical hosted zone ID */
    public String getLoadBalancerCanonicalHostedZoneId() {
        return loadBalancerCanonicalHostedZoneId;
    }
}
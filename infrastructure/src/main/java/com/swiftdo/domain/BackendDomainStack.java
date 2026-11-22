package com.swiftdo.domain;

import com.swiftdo.core.ApplicationEnvironment;
import com.swiftdo.network.NetworkOutputParameters;
import com.swiftdo.network.NetworkParameterStore;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancerAttributes;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationLoadBalancer;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.constructs.Construct;

/**
 * Responsible for managing DNS records for Backend
 *
 * <p>
 *     Imports an existing Route53 public hosted zone
 *     Imports an existing Application Load Balancer assuming ALB has been deployed and its arn exits in SSM Parameter Store
 *     Creates an alias A record pointing the api domain to the ALB
 * </>
 */
public class BackendDomainStack extends Stack {

    /**
     * Creates a new {@link BackendDomainStack}.
     *
     * @param scope                  parent construct (usually the CDK {@link App})
     * @param constructId            logical identifier for the stack
     * @param awsEnvironment         AWS account and region configuration
     * @param applicationEnvironment application-level environment metadata
     * @param hostedZoneDomain       root domain of the Route 53 hosted zone
     * @param applicationDomain      fully qualified domain name to be routed to the ALB
     */
    public BackendDomainStack(
            final Construct scope,
            final String constructId,
            final Environment awsEnvironment,
            final ApplicationEnvironment applicationEnvironment,
            final String hostedZoneDomain,
            final String applicationDomain
            ){
        super(
                scope,
                constructId,
                StackProps.builder()
                        .stackName(applicationEnvironment.getApplicationName() + "-backend-domain-stack")
                        .env(awsEnvironment)
                        .build()
        );

        /*
         * Fetch the Hosted Zone(container that holds DNS records)
         * for a specific domain.
         */
        IHostedZone hostedZone = HostedZone.fromLookup(
                this,
                "HostedZone",
                HostedZoneProviderProps.builder()
                        .domainName(hostedZoneDomain)
                        .build()
        );

        // Load network-level identifiers produced by Network Stack
        NetworkOutputParameters networkOutputParameters = NetworkParameterStore.load(this, applicationEnvironment);

        // Import the ALB created by NetworkStack
        IApplicationLoadBalancer applicationLoadBalancer = ApplicationLoadBalancer.fromApplicationLoadBalancerAttributes(
                this,
                "LoadBalancer",
                ApplicationLoadBalancerAttributes.builder()
                        .loadBalancerArn(networkOutputParameters.getLoadBalancerArn())
                        .securityGroupId(networkOutputParameters.getLoadbalancerSecurityGroupId())
                        .loadBalancerCanonicalHostedZoneId(networkOutputParameters.getLoadBalancerCanonicalHostedZoneId())
                        .loadBalancerDnsName(networkOutputParameters.getLoadBalancerDnsName())
                        .build()
        );

        // Creates an Alias A record in Route53 that maps the api domain name to the ALB
        ARecord aRecord = ARecord.Builder.create(this, "AlbRecord")
                .recordName(applicationDomain)
                .zone(hostedZone)
                .target(RecordTarget.fromAlias(new LoadBalancerTarget(applicationLoadBalancer)))
                .build();

        applicationEnvironment.tag(this);
    }
}
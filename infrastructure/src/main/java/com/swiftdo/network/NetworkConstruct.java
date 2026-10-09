package com.swiftdo.network;

import com.swiftdo.core.ApplicationEnvironment;
import com.swiftdo.core.DeploymentStage;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.ssm.StringListParameter;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.*;

/**
 * A CDK Construct responsible for provisioning all network facing
 * infrastructure required by the application
 *
 * <p>
 *     This construct acts as the foundational layer upon which
 *     all service level constructs depends.
 * </p>
 * <p> Responsibilities include: </p>
 * <ul>
 *     <li> Creating a VPC and the Subnets withing. </li>
 *     <li> Defining the ECS Cluster bound to the VPC </li>
 *     <li> Creating and Configuring the ALB and the HTTP/HTTPS Listeners </li>
 *     <li> Exposing critical network identifiers via SSM Parameters </li>
 * </ul>
 *
 * <p> Architectural Note
 * <br>
     * The ECS Cluster is intentionally placed inside the network construct
     * because it is fundamentally coupled to the VPC lifecycle. An ECS
     * cluster cannot exist meaningfully without a network boundary, and
     * treating it as part of the network layer avoids circular dependencies
     * between stacks.
 * </p>
 */
public class NetworkConstruct extends Construct {

    /**
     * Deployment environment this network belongs to.
     *
     * <p> Used for: </p>
     * <ul>
     *   <li>Resource naming</li>
     *   <li>SSM parameter namespacing</li>
     *   <li>Tagging</li>
     * </ul>
     */
    private final ApplicationEnvironment applicationEnvironment;

    /**
     * The VPC that defines the network boundary
     * for all application resources.
     */
    private final IVpc vpc;

    /**
     * ECS Cluster associated with this network.
     *
     * <p>
     * All ECS services deployed in this environment
     * are expected to attach to this cluster.
     * </p>
     */
    private final ICluster ecsCluster;

    /**
     * HTTP listener attached to the Application Load Balancer.
     *
     * <p>
     * Always created and used as the primary entry point
     * for traffic, even when HTTPS is enabled. Todo: Check if Http is to be disabled when Https is enabled
     * </p>
     */
    private IApplicationListener httpListener;

    /**
     * HTTPS listener attached to the Application Load Balancer.
     *
     * <p>
     * Created only if an SSL certificate ARN is provided.
     * May be {@code null} for non-TLS environments.
     * </p>
     */
    private IApplicationListener httpsListener;

    /**
     * Security group associated with the Application Load Balancer.
     *
     * <p>
     * Controls inbound access to public-facing traffic.
     * </p>
     */
    private ISecurityGroup loadbalancerSecurityGroup;

    /**
     * Public-facing Application Load Balancer for the application.
     */
    private IApplicationLoadBalancer loadBalancer;

    /**
     * Creates and wires together all network-level infrastructure.
     *
     * <p> Construction order is intentional: </p>
     * <ol>
     *   <li>VPC is created first as the foundational resource</li>
     *   <li>Create Endpoints for S3, ECR, SNS etc. to connect to resources in private subnet</li>
     *   <li>ECS Cluster is bound to the VPC</li>
     *   <li>Load Balancer is provisioned inside the VPC</li>
     *   <li>Output parameters are published for downstream stacks</li>
     * </ol>
     *
     * @param scope Parent construct (typically a Stack)
     *
     * @param constructId Logical identifier within the construct tree
     *
     * @param applicationEnvironment Application Name and Deployment Stage (e.g. dev-swift-do)
     *
     * @param inputParameters Optional configuration affecting load balancer behavior
     */
    public NetworkConstruct(
            final Construct scope,
            final String constructId,
            final ApplicationEnvironment applicationEnvironment,
            final NetworkInputParameters inputParameters
    ){
        super(scope, constructId);
        this.applicationEnvironment = applicationEnvironment;

        // Core network boundary
        this.vpc = createVpc();

        // A VPC Gateway Endpoint to allow resources in private subnet to connect to S3 & DynamoDB Services
        createGatewayEndpoint();
        // A VPC Interface Endpoint to allow resources in private subnet to connect to ECR, SNS, SQS etc
        createInterfaceEndpoint();

        // Compute orchestration layer tied to the VPC
        this.ecsCluster = createEcsCluster();

        // Public ingress layer
        this.loadBalancer = createLoadBalancer(inputParameters);

        // Export identifiers for cross-stack consumption
        createOutputParameters();

        // Apply environment-wide tagging for cost allocation
        Tags.of(this).add("environment", applicationEnvironment.getDeploymentStage().getName());
    }

    /**
     * Creates the Virtual Private Cloud used by the application.
     * A private, isolated network inside AWS that nothing can enter
     * or leave unless you explicitly allow it.
     *
     * <p> The VPC is intentionally minimal: </p>
     * <ul>
     *   <li>Two Availability Zone for simplicity</li>
     *   <li>Public subnets for ingress</li>
     *   <li>Isolated subnets for private workloads</li>
     * </ul>
     *
     */
    private IVpc createVpc(){

        // Public subnet for internet-facing resources(Load Balancer)
        SubnetConfiguration publicSubnet = SubnetConfiguration.builder()
                .subnetType(SubnetType.PUBLIC)
                .name(prefixWithEnvironmentName("public-subnet")) // dev-public-subnet
                .build();

        // Isolated subnet for internal workloads(ECS Tasks, Databases) without internet access
        SubnetConfiguration isolatedSubnet = SubnetConfiguration.builder()
                .subnetType(SubnetType.PRIVATE_ISOLATED)
                .name(prefixWithEnvironmentName("private-subnet")) // dev-private-subnet
                .build();

        return Vpc.Builder.create(this, "Vpc")
                .vpcName(prefixWithEnvironmentName("vpc"))
                .maxAzs(2)
                .subnetConfiguration(
                        Arrays.asList(publicSubnet,isolatedSubnet)
                )
                .build();
    }

    /**
     * Creates a Gateway VPC Endpoint for Amazon S3.
     *
     * <p>
     * A Gateway Endpoint allows resources inside private subnets to access
     * Amazon S3 without requiring public IP, NAT Gateway or any public routing.
     * </p>
     *
     * <p>
     * Unlike Interface Endpoints, Gateway Endpoints operate at the
     * <strong>route table level</strong>. When traffic is destined for S3,
     * it is transparently routed through AWS’s internal network rather than the internet.
     * </p>
     *
     * <p>
     * This endpoint is attached only to {@code ISOLATED} subnets,
     * ensuring that ECS tasks, databases, and other internal workloads
     * can access S3 securely while remaining completely private.
     * </p>
     *
     * <p>
     * Typical use cases enabled by this endpoint:
     * </p>
     * <ul>
     *   <li>ECS tasks downloading application assets from S3</li>
     *   <li>Writing logs, backups, or reports to S3</li>
     *   <li>Accessing S3 without NAT Gateway cost</li>
     * </ul>
     *
     */
    private void createGatewayEndpoint() {
        vpc.addGatewayEndpoint("S3Endpoint",
                GatewayVpcEndpointOptions.builder()
                        .service(GatewayVpcEndpointAwsService.S3)
                        .subnets(List.of(
                                SubnetSelection.builder()
                                        .subnetType(SubnetType.PRIVATE_ISOLATED)
                                        .build()
                        ))
                        .build()
        );
    }

    /**
     * Creates Interface VPC Endpoints required for running ECS tasks
     * in private subnets without a NAT Gateway.
     *
     * <p>
     * Interface Endpoints create elastic network interfaces (ENIs)
     * inside the VPC that expose AWS services via private IP addresses.
     * DNS resolution is transparently redirected to these private endpoints.
     * </p>
     *
     * <p>
     * This enables ECS tasks in isolated subnets to:
     * </p>
     * <ul>
     *   <li>Pull Docker images from Amazon ECR</li>
     *   <li>Send logs to CloudWatch Logs</li>
     *   <li>Assume IAM roles via STS</li>
     * </ul>
     *
     * <p>
     * All endpoints are:
     * </p>
     * <ul>
     *   <li>Placed in {@code PRIVATE} subnets</li>
     *   <li>Protected by a dedicated security group</li>
     *   <li>Configured with private DNS enabled</li>
     * </ul>
     */
    private void createInterfaceEndpoint(){

        /*
         * Security group attached to all interface endpoints.
         * It allows inbound connections from resources inside the VPC
         * and permits outbound traffic to the AWS service.
         */
        ISecurityGroup interfaceEndpointSecurityGroup = SecurityGroup.Builder.create(this, "VpcEndpointSG")
                .vpc(vpc)
                .description("Security group for VPC interface endpoints")
                .allowAllOutbound(true)
                .build();
        interfaceEndpointSecurityGroup.addIngressRule(
                Peer.ipv4(vpc.getVpcCidrBlock()),
                Port.tcp(443),
                "Allow inbound HTTPS traffic from withing the VPC"
        );

        /*
         * List of AWS services required for ECS Fargate and others
         * to function in private subnets without internet access.
         */
        List<InterfaceVpcEndpointAwsService> services = List.of(
                InterfaceVpcEndpointAwsService.ECR,
                InterfaceVpcEndpointAwsService.ECR_DOCKER,
                InterfaceVpcEndpointAwsService.CLOUDWATCH_LOGS,
                InterfaceVpcEndpointAwsService.STS,
                InterfaceVpcEndpointAwsService.SECRETS_MANAGER,
                InterfaceVpcEndpointAwsService.SSM
        );

        /*
         * Create one interface endpoint per service.
         * Each endpoint is deployed into isolated subnets
         * and protected by the endpoint security group.
         */
        for (InterfaceVpcEndpointAwsService service : services) {
            vpc.addInterfaceEndpoint(service.getShortName() + "Endpoint",
                    InterfaceVpcEndpointOptions.builder()
                            .service(service)
                            .subnets(
                                    SubnetSelection.builder()
                                            .subnetType(SubnetType.PRIVATE_ISOLATED)
                                            .build()
                            )
                            .securityGroups(List.of(interfaceEndpointSecurityGroup))
                            .privateDnsEnabled(true)
                            .build()
            );
        }
    }

    /**
     * Creates an ECS Cluster bound to the VPC.
     *
     * <p>
     * The cluster serves as a logical grouping for ECS services
     * and provides the control plane for task scheduling.
     * Simply a logical 'container' where ECS schedules task.
     * It's not a machine, does not run container only provides coordination.
     * </p>
     *
     * <p>
     * The ECS must bind to our VPC, Subnet & Security Rules thus
     * we put ECS in the NetworkConstruct
     * </p>
     */
    private ICluster createEcsCluster(){
        return Cluster.Builder.create(this, "EcsCluster")
                .vpc(vpc)
                .clusterName(prefixWithEnvironmentName("ecs-cluster"))
                .build();
    }

    /**
     * Creates and configures the Application Load Balancer.
     *
     * <p>
     * ALB distributes incoming HTTP/s traffic across multiple
     * Targets based on request attribute.
     * The listener in alb receive requests matching the protocol
     * and port we configure. The receiving listener evaluates the
     * request against the rules we specify and if applicable
     * routes the request to appropriate target fom the target group.
     * We can use HTTPS listener to offload the work of TLS
     * encryption and decryption to ALB.
     * </p>
     *
     * <p>
     * Responsibilities handled here:
     * </p>
     *
     * <ul>
     *   <li>Security group creation</li>
     *   <li>Public ingress rules</li>
     *   <li>Target group configuration</li>
     *   <li>HTTP listener setup</li>
     *   <li>Optional HTTPS listener and redirection</li>
     * </ul>
     *
     * @param inputParameters Optional inputs affecting TLS configuration
     */
    private IApplicationLoadBalancer createLoadBalancer(NetworkInputParameters inputParameters){

        // Security group controlling public access to the ALB
        this.loadbalancerSecurityGroup = SecurityGroup.Builder.create(this, "LoadBalancerSecurityGroup")
                .securityGroupName(prefixWithEnvironmentName("lb-sg"))
                .description("Public Access to the Load Balancer")
                .vpc(vpc)
                .build();
        // Ingress Rule that is attached to the loadbalancerSecurityGroup. Alb should be reachable from internet.
        this.loadbalancerSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "Allow HTTP traffic from internet");
        this.loadbalancerSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(443), "Allow HTTPS traffic from internet");

        // Application loadbalancer
        IApplicationLoadBalancer applicationLoadBalancer = ApplicationLoadBalancer.Builder.create(this, "LoadBalancer")
                .loadBalancerName(prefixWithEnvironmentName("lb"))
                .vpc(vpc) // alb creates a Network Interface in the VPC it lives in.
                .internetFacing(true) // indicates that alb accepts traffic from internet and is not just for internal traffic
                .securityGroup(loadbalancerSecurityGroup)
                .build();

        // A Dummy Application Target Group so that we create ALB Listener here in the Network Construct. It will be overridden in the ServiceConstruct
        IApplicationTargetGroup dummyApplicationTargetGroup = ApplicationTargetGroup.Builder.create(this, "DummyApplicationTargetGroup")
                .vpc(vpc)
                .port(8080) // this must match the container port.
                .protocol(ApplicationProtocol.HTTP) // ALB uses HTTP to communicate with container and terminates TLS.
                .targetType(TargetType.IP) // Since we are using Fargate the alb will use private IP address to determine target.
                .targetGroupName(prefixWithEnvironmentName("no-op-targetGroup"))
                .deregistrationDelay(Duration.seconds(5))
                .healthCheck(
                        HealthCheck.builder()
                                .interval(Duration.seconds(10)) // every 10 seconds ALB pings the target
                                .timeout(Duration.seconds(5)) // if no response in 5 seconds consider it failure
                                .healthyThresholdCount(2) // 2 consecutive success means target is healthy
                                .build())
                .build();

        if (inputParameters.getSslCertificateArn().isPresent()) {
            String sslCertificateArn = inputParameters.getSslCertificateArn().get();

            // HTTPS Listener gets the dummy target group
            this.httpsListener = applicationLoadBalancer.addListener("HttpsListener", BaseApplicationListenerProps.builder()
                    .port(443)
                    .protocol(ApplicationProtocol.HTTPS)
                    .certificates(List.of(ListenerCertificate.fromArn(sslCertificateArn)))
                    .open(false) // Security group already handles ingress
                    .build()
            );
            this.httpsListener.addTargetGroups("HttpsTargetGroup", AddApplicationTargetGroupsProps.builder()
                    .targetGroups(List.of(dummyApplicationTargetGroup))
                    .build()
            );

            // HTTP Listener simply redirects all traffic to HTTPS by default
            this.httpListener = applicationLoadBalancer.addListener("HttpListener", BaseApplicationListenerProps.builder()
                    .port(80)
                    .protocol(ApplicationProtocol.HTTP)
                    .defaultAction(ListenerAction.redirect(RedirectOptions.builder()
                            .protocol("HTTPS")
                            .port("443")
                            .permanent(true)
                            .build()))
                    .open(false)
                    .build()
            );
        } else {
            // Non-TLS Environment: HTTP points to dummy target group
            this.httpListener = applicationLoadBalancer.addListener("HttpListener", BaseApplicationListenerProps.builder()
                    .port(80)
                    .protocol(ApplicationProtocol.HTTP)
                    .open(false)
                    .build()
            );
            this.httpListener.addTargetGroups("HttpTargetGroup", AddApplicationTargetGroupsProps.builder()
                    .targetGroups(List.of(dummyApplicationTargetGroup))
                    .build()
            );
        }

        return applicationLoadBalancer;
    }

    /**
     * Creates a standardized SSM parameter name
     * scoped to the deployment environment.
     * @param applicationEnvironment defines the application name & deployment stage
     * @param parameterKey 'key; value for the SSM parameter
     */
    private static String createParameterName(ApplicationEnvironment applicationEnvironment, String parameterKey){
        return applicationEnvironment.prefix("network-" + parameterKey);
    }

    /**
     * Publishes a single String parameter to SSM Parameter Store.
     * @param constructId logical id for the construct tree
     * @param name 'key' of the SSM parameter store entry
     * @param value 'value' of the SSM parameter store entry
     */
    private void putParameter(String constructId, String name, String value) {
        StringParameter.Builder.create(this, constructId)
                .parameterName(createParameterName(applicationEnvironment, name))
                .stringValue(value)
                .build();
    }

    /**
     * Publishes a list-valued parameter to SSM Parameter Store.
     * @param constructId logical id for the construct tree
     * @param name 'key' of the SSM parameter store entry
     * @param values list 'value' of the SSM parameter store entry
     */
    private void putListParameter(String constructId, String name, List<String> values) {
        StringListParameter.Builder.create(this, constructId)
                .parameterName(createParameterName(applicationEnvironment, name))
                .stringListValue(values)
                .build();
    }

    /**
     * Exports all network-related identifiers required
     * by downstream stacks.
     *
     * <p>
     * These parameters form the contract between the
     * network stack and dependent stacks.
     * </p>
     */
    private void createOutputParameters(){
        putParameter("VpcIdParameter", NetworkOutputs.PARAMETER_VPC_ID, this.vpc.getVpcId());
        putParameter("HttpListenerParameter", NetworkOutputs.PARAMETER_HTTP_LISTENER, this.httpListener.getListenerArn());
        if (this.httpsListener != null) {
            putParameter("HttpsListenerParameter", NetworkOutputs.PARAMETER_HTTPS_LISTENER, this.httpsListener.getListenerArn());
        }
        putParameter("LoadBalancerSecurityGroupIdParameter", NetworkOutputs.PARAMETER_LOADBALANCER_SECURITY_GROUP_ID, this.loadbalancerSecurityGroup.getSecurityGroupId());
        putParameter("EcsClusterNameParameter", NetworkOutputs.PARAMETER_ECS_CLUSTER_NAME, this.ecsCluster.getClusterName());
        putParameter("LoadBalancerArnParameter", NetworkOutputs.PARAMETER_LOAD_BALANCER_ARN, this.loadBalancer.getLoadBalancerArn());
        putParameter("LoadBalancerDnsNameParameter", NetworkOutputs.PARAMETER_LOAD_BALANCER_DNS_NAME, this.loadBalancer.getLoadBalancerDnsName());
        putParameter("LoadBalancerCanonicalHostedZoneIdParameter", NetworkOutputs.PARAMETER_LOAD_BALANCER_HOSTED_ZONE_ID, this.loadBalancer.getLoadBalancerCanonicalHostedZoneId());
        putListParameter("AvailabilityZoneParameter", NetworkOutputs.PARAMETER_AVAILABILITY_ZONES, this.vpc.getAvailabilityZones());
        putListParameter("PublicSubnetIdsParameter", NetworkOutputs.PARAMETER_PUBLIC_SUBNETS, this.vpc.getPublicSubnets().stream().map(ISubnet::getSubnetId).toList());
        putListParameter("PrivateSubnetIdsParameter", NetworkOutputs.PARAMETER_PRIVATE_SUBNETS, this.vpc.getIsolatedSubnets().stream().map(ISubnet::getSubnetId).toList());
    }

    /**
     * Prefixes resource names with the environment name
     * to avoid cross-environment collisions.
     */
    private String prefixWithEnvironmentName(String string) {
        return this.applicationEnvironment.prefix(string);
    }

    /**
     * Input configuration object for {@link NetworkConstruct}.
     *
     * <p>
     * This class represents optional, user-supplied configuration
     * that affects how network infrastructure is provisioned.
     * </p>
     */
    public static class NetworkInputParameters{

        /**
         * Optional ARN of an ACM certificate used to enable HTTPS
         * on the Application Load Balancer.
         *
         * <p> When present: </p>
         * <ul>
         *   <li>An HTTPS listener is created</li>
         *   <li>HTTP traffic is redirected to HTTPS</li>
         * </ul>
         *
         * <p>When absent:</p>
         * <ul>
         *   <li>Only an HTTP listener is configured</li>
         * </ul>
         */
        private Optional<String> sslCertificateArn;

        /**
         * Creates a new instance with no optional configuration applied.
         *
         * <p>
         * Defaults to HTTP-only traffic.
         * </p>
         */
        public NetworkInputParameters() {
            this.sslCertificateArn = Optional.empty();
        }

        /**
         * Enables HTTPS support by supplying an ACM certificate ARN.
         *
         * <p>
         * This method follows a fluent API style so that additional
         * configuration options can be chained in the future.
         * </p>
         *
         * @param sslCertificateArn
         *   ARN of the ACM certificate to attach to the HTTPS listener
         *
         * @return this instance for method chaining
         */
        public NetworkInputParameters withSslCertificateArn(String sslCertificateArn){
            Objects.requireNonNull(sslCertificateArn);
            this.sslCertificateArn = Optional.of(sslCertificateArn);
            return this;
        }

        /**
         * Returns the optional SSL certificate ARN.
         *
         * @return optional ACM certificate ARN
         */
        public Optional<String> getSslCertificateArn() {
            return sslCertificateArn;
        }
    }

    public IVpc getVpc(){
        return this.vpc;
    }

    public IApplicationListener getHttpListener(){
        return this.httpListener;
    }

    public IApplicationListener getHttpsListener(){
        return this.httpsListener;
    }

    public ISecurityGroup getLoadbalancerSecurityGroup(){
        return this.loadbalancerSecurityGroup;
    }

    public IApplicationLoadBalancer getLoadBalancer(){
        return this.loadBalancer;
    }

    public ICluster getEcsCluster(){
        return this.ecsCluster;
    }
}
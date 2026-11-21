package com.swiftdo.app;

import com.swiftdo.core.ApplicationEnvironment;
import com.swiftdo.core.DeploymentStage;
import com.swiftdo.network.NetworkConstruct;
import software.amazon.awscdk.*;
import com.swiftdo.core.Utility;

/**
 * CDK application entry point responsible for deploying
 * the network infrastructure stack.
 *
 * <p>
 * This application provisions all foundational network resources
 * required by the application, including:
 * </p>
 *
 * <ul>
 *   <li>VPC and subnet topology</li>
 *   <li>Application Load Balancer</li>
 *   <li>Http & Https Listeners</li/>
 *   <li>ECS Cluster</li>
 * </ul>
 *
 * <p>
 * This application is typically deployed early in the lifecycle
 * of an environment and consumed by downstream stacks such as
 * ECS services or application layers.
 * </p>
 */
public class NetworkApp {
    public static void main(String[] args) {
        // Root of the CDK Construct Tree
        App app = new App();

        // Resolve the logical deployment stage
        String stageName = (String) app.getNode().tryGetContext("environmentName");
        Utility.requireNonEmpty(stageName, "context variable 'environmentName' must not be null");
        DeploymentStage deploymentStage = DeploymentStage.from(stageName);

        // Resolve AWS region for deployment
        String region = (String) app.getNode().tryGetContext("region");
        Utility.requireNonEmpty(region, "context variable 'region' must not be null");

        // Resolve AWS account ID
        String accountId = (String) app.getNode().tryGetContext("accountId");
        Utility.requireNonEmpty(accountId, "context variable 'accountId' must not be null");

        // Reolve Application Name
        String applicationName = (String) app.getNode().tryGetContext("applicationName");
        Utility.requireNonEmpty(applicationName, "context variable 'applicationName' must not be null");


        // Collect optional network input parameters
        NetworkConstruct.NetworkInputParameters inputParameters = new NetworkConstruct.NetworkInputParameters();

        // SSL Certificate for HTTPs support
        String sslCertificateArn = Fn.importValue("swift-do-SslCertificateArn");
        Utility.requireNonEmpty(sslCertificateArn, "swift-do-SslCertificateArn must not be null");
        inputParameters.withSslCertificateArn(sslCertificateArn);

        // Explicitly define the AWS deployment environment
        Environment awsEnvironment = Utility.makeEnv(accountId, region);

        // Creating an Application Environment for naming conventions
        ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment(applicationName, deploymentStage);

        // Define the stack responsible for network infrastructure
        Stack networkStack = new Stack( app, "NetworkStack",
                StackProps.builder()
                        .stackName(applicationEnvironment.prefix("network-stack"))
                        .description("Stack provisions all foundational network resources required by the application")
                        .env(awsEnvironment)
                        .build()
        );

        // Attach the network construct to the stack
        NetworkConstruct networkConstruct = new NetworkConstruct(
                networkStack,
                "Network",
                applicationEnvironment,
                inputParameters
        );

        app.synth();
    }
}

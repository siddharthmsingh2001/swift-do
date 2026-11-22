package com.swiftdo.app;

import com.swiftdo.core.ApplicationEnvironment;
import com.swiftdo.core.DeploymentStage;
import com.swiftdo.core.Utility;
import com.swiftdo.domain.BackendDomainStack;
import com.swiftdo.network.NetworkOutputParameters;
import com.swiftdo.network.NetworkParameterStore;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;

/**
 * CDK application entry point responsible for configuring
 * DNS routing for the api domain.
 * <p>
 * This app assumes that:
 * <ul>
 *   <li>The public Route 53 hosted zone already exists</li>
 *   <li>An ALB has already been provisioned via {@link NetworkApp} and its arn is available via {@link NetworkOutputParameters} </li>
 *   <li>Network stack outputs are available in SSM Parameter Store</li>
 * </ul>
 */
public class BackendDomainApp {
    public static void main(String[] args) {
        // Root of the CDK Construct Tree
        App app = new App();

        // Resolve the logical deployment stage
        String stageName = (String) app.getNode().tryGetContext("environmentName");
        Utility.requireNonEmpty(stageName, "context variable 'environmentName' must not be null");
        DeploymentStage deploymentStage = DeploymentStage.from(stageName);

        // Resolve Application Name
        String applicationName = (String) app.getNode().getContext("applicationName");
        Utility.requireNonEmpty(applicationName, "context variable 'applicationName' must not be null");

        // AWS Account Id into which the stack will be deployed
        String accountId = (String) app.getNode().tryGetContext("accountId");
        Utility.requireNonEmpty(accountId, "context variable 'accountId' must not be null");

        // Exact hostname that Frontend will connect to via HTTPS as api.swift-do.com
        String applicationDomain = (String) app.getNode().tryGetContext("apiDomain");
        Utility.requireNonEmpty(applicationDomain, "context variable 'apiDomain' must not be null");

        // The authoritative DNS zone in Route 53 which is a public hosted zone
        String hostedZoneDomain = (String) app.getNode().tryGetContext("hostedZoneDomain");
        Utility.requireNonEmpty(hostedZoneDomain, "context variable 'hostedZoneDomain' must not be null");

        // AWS region for deployment
        String region = (String) app.getNode().tryGetContext("region");
        Utility.requireNonEmpty(region, "context variable 'region' must not be null");

        // Explicitly define the AWS deployment environment our backend is to be deployed in Mumbai thus the ap-south-1 is the region
        Environment awsEnvironment = Utility.makeEnv(accountId, region);

        // Creating an Application Environment for naming conventions
        ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment(applicationName, deploymentStage);


        new BackendDomainStack(
                app,
                "BackendDomainStack",
                awsEnvironment,
                applicationEnvironment,
                hostedZoneDomain,
                applicationDomain
        );

        app.synth();
    }
}

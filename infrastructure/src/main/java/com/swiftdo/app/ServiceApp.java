package com.swiftdo.app;

import com.swiftdo.core.ApplicationEnvironment;
import com.swiftdo.core.DeploymentStage;
import com.swiftdo.core.Utility;
import com.swiftdo.network.NetworkOutputParameters;
import com.swiftdo.network.NetworkParameterStore;
import com.swiftdo.service.ServiceConstruct;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;
import java.util.HashMap;
import java.util.Map;

/**
 * CDK application entry point responsible for deploying
 * the network infrastructure stack.
 *
 * <p>
 * This class represents the <strong>application-level bootstrap</strong>
 * for the Service layer of the infrastructure. It creates and configures
 * the following:
 * </p>
 * <ul>
 *   Todo: Add what this service app will configure and create
 * </ul>
 *
 */
public class ServiceApp {
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

        // Spring profile to activate inside the container
        String profileName = (String) app.getNode().tryGetContext("springProfile");
        Utility.requireNonEmpty(profileName, "context variable 'springProfile' must not be null");
        DeploymentStage springProfile = DeploymentStage.from(profileName);

        // ECR Image Repository Name for main application
        String repositoryName = (String) app.getNode().tryGetContext("repositoryName");
        Utility.requireNonEmpty(repositoryName, "context variable 'repositoryName' must not be null");

        // ECR Image Repository Tag
        String imageTag = (String) app.getNode().tryGetContext("imageTag");
        Utility.requireNonEmpty(imageTag, "context variable 'imageTag' must not be null");

        // AWS region for deployment
        String region = (String) app.getNode().tryGetContext("region");
        Utility.requireNonEmpty(region, "context variable 'region' must not be null");

        // Explicitly define the AWS deployment environment
        Environment awsEnvironment = Utility.makeEnv(accountId, region);

        // Creating an Application Environment for naming conventions
        ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment(applicationName, deploymentStage);

        /*
         * Stack that hosts all ECS service-related
         * resources.
         */
        Stack serviceStack = new Stack(app, "ServiceStack",
                StackProps.builder()
                        .stackName(applicationEnvironment.prefix("service-stack")) // dev-swift-do-service-stack
                        .env(awsEnvironment)
                        .build()
        );

        // Load network-level identifiers produced by Network Stack
        NetworkOutputParameters networkOutputParameters = NetworkParameterStore.load(serviceStack, applicationEnvironment);

        // Todo: Change the Constructor once adding DB, Cognito and SQS SecurityGroupIds
        // Defining the ServiceInputParameters
        ServiceConstruct.ServiceInputParameters serviceInputParameters = new ServiceConstruct.ServiceInputParameters(
                new ServiceConstruct.DockerImageSource(repositoryName, imageTag),
                environmentVariables(
                        serviceStack,
                        springProfile.getName()
                )
        );

        // Defining the Construct
        ServiceConstruct serviceConstruct = new ServiceConstruct(
                serviceStack,
                "Service",
                awsEnvironment,
                applicationEnvironment,
                serviceInputParameters,
                networkOutputParameters
        );

        app.synth();
    }

    static Map<String, String> environmentVariables(
            Construct scope,
            String springProfile
    ){
        Map<String, String> vars = new HashMap<>();
        // --- Spring Profile ---
        vars.put("SPRING_PROFILES_ACTIVE", springProfile);
        return vars;
    }
}

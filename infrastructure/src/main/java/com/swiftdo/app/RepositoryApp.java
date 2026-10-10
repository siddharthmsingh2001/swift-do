package com.swiftdo.app;

import com.swiftdo.core.ApplicationEnvironment;
import com.swiftdo.core.Utility;
import com.swiftdo.repository.RepositoryConstruct;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

/**
 * CDK application entry point for provisioning the ECR Repository Stack.
 *
 * <p>
 *     Class represents a standalone CDK app to deploy
 *     container registry infrastructure
 * </p>
 * <p>
 *     App is deployed before any ECS
 *     or CI/CD stacks
 * </p>
 */
public class RepositoryApp {
    public static void main(String[] args) {
        // Root of the CDK Construct Tree
        App app = new App();

        // Read required context variables provided via:
        // cdk deploy -c accountId=... -c region=... -c applicationName=...
        // OR
        // through cdk.json

        // Resolve AWS region for deployment
        String region = (String) app.getNode().tryGetContext("region");
        Utility.requireNonEmpty(region, "context variable 'region' must not be null");

        // Resolve AWS account ID
        String accountId = (String) app.getNode().tryGetContext("accountId");
        Utility.requireNonEmpty(accountId, "context variable 'accountId' must not be null");

        // Reolve Application Name
        String applicationName = (String) app.getNode().tryGetContext("applicationName");
        Utility.requireNonEmpty(applicationName, "context variable 'applicationName' must not be null");

        // Explicitly define the AWS deployment environment
        Environment awsEnvironment = Utility.makeEnv(accountId, region);

        Stack repositoryStack = new Stack(app, "RepositoryStack",
                StackProps.builder()
                        .stackName(applicationName + "-repository-stack")
                        .description("Stack provisions ECR Repository Stack")
                        .env(awsEnvironment)
                        .build()
        );

        // Attach the repository construct to the stack
        RepositoryConstruct repositoryConstruct = new RepositoryConstruct(
                repositoryStack,
                "Repository",
                new RepositoryConstruct.RepositoryInputParameters(
                        applicationName,
                        accountId,
                        10,
                        false
                )
        );

        app.synth();
    }
}

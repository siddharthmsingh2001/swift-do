package com.swiftdo.core;

import software.amazon.awscdk.Tags;
import software.constructs.IConstruct;

/**
 * Represents the logical environment for the Application, combining the Application name and Deployment stage.
 * <p> This class is a central utility used to: </>
 * <ul>
 *     <li> Generate consistent names for AWS Resources </>
 *     <li> Apply standard metadata Tags to the CDK Constructs </>
 *     <li> Todo: Insert and example here for convenient future reference </>
 * </>
 */
public class ApplicationEnvironment {

    private final String applicationName;
    private final DeploymentStage deploymentStage;

    /**
     * Simple Constructor to create the Application Environment
     * @param applicationName The name of the application e.g. "swift-do"
     * @param deploymentStage The target stage e.g. {@link DeploymentStage#DEV}
     */
    public ApplicationEnvironment(String applicationName, DeploymentStage deploymentStage){
        this.applicationName = applicationName;
        this.deploymentStage = deploymentStage;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public DeploymentStage getDeploymentStage(){
        return deploymentStage;
    }

    /**
     * Removes any characters that are typically invalid in AWS resource names.
     * Only alphanumeric characters and hyphens are permitted.
     */
    private String sanitize(String str){
        return str.replaceAll("[^a-zA-Z0-9-]","");
    }

    /**
     * Returns a string representation of the environment, typically used as a stack name prefix.
     * Format: {stage}-{applicationName}
     */
    @Override
    public String toString(){
        return sanitize(deploymentStage.getName() + "-" + applicationName);
    }

    /**
     * Prefixes a given string with the environment identifier.
     * @param string The suffix to append (e.g., "vpc" or "database").
     * @return  A combined string: {stage}-{applicationName}-{suffix}
     */
    public String prefix(String string){
        return this + "-" + string;
    }

    /**
     * Applies standard environment tags to a CDK construct.
     * <p> Adds the following tags: </>
     * <li><b>deployment</b>: The name of the deployment stage.</li>
     * <li><b>application</b>: The name of the application.</li>
     * @param construct The CDK construct (Stack, Resources, etc.) to tag.
     */
    public void tag(IConstruct construct){
        Tags.of(construct).add("deployment", deploymentStage.getName());
        Tags.of(construct).add("application", applicationName);
    }
}

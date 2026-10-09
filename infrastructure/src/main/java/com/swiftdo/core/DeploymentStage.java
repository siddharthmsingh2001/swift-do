package com.swiftdo.app;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Defines the supported deployment environments across the infrastructure and application.
 */
public enum DeploymentStage {

    /** Development environment for local testing and features in progress. */
    DEV("dev"),

    /** Staging environment for pre-production integration testing. */
    STAGING("staging"),

    /** Production environment serving live traffic. */
    PROD("prod");

    // Cache lookup map to avoid array allocations on every call
    private static final Map<String, DeploymentStage> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    stage -> stage.name.toLowerCase(),
                    Function.identity()
            ));

    private final String name;

    DeploymentStage(String name){
        this.name = name;
    }

    /**
     * Returns the lower-case string representation of the deployment stage.
     *
     * @return String stage key (e.g., "dev", "staging", "PROD").
     */
    public String getName() {
        return name;
    }

    /**
     * Parses a string configuration value into its corresponding {@link DeploymentStage}.
     *
     * @param value the raw environment name (e.g., from AWS CDK context or environment variables); case-insensitive
     * @return the matching {@link DeploymentStage} instance
     * @throws IllegalArgumentException if {@code value} is null or does not match any valid stage
     */
    public static DeploymentStage from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Deployment stage value cannot be null");
        }

        DeploymentStage stage = BY_NAME.get(value.toLowerCase());
        if (stage == null) {
            throw new IllegalArgumentException(String.format(
                    "Unknown deployment stage '%s'. Valid values are: %s",
                    value, BY_NAME.keySet()
            ));
        }
        return stage;
    }
}

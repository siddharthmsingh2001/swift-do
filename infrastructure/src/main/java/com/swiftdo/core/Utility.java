package com.swiftdo.core;

import software.amazon.awscdk.Environment;

/**
 * Utility class for common validation logic, used primarily to validate
 * CDK context variables at the start of the application.
 */
public class Utility {

    /**
     * Ensures a string is neither null nor contains only whitespace.
     * @param string The value to check.
     * @param message The error message to display if validation fails.
     * @throws IllegalArgumentException if validation fails.
     */
    public static void requireNonEmpty(String string, String message) {
        if (string == null || string.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Creates an AWS {@link Environment} definition for CDK stacks.
     *
     * @param account AWS account ID
     * @param region  AWS region
     * @return configured {@link Environment}
     */
    public static Environment makeEnv(String account, String region) {
        return Environment.builder()
                .account(account)
                .region(region)
                .build();
    }
}

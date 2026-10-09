package com.swiftdo.network;

import com.swiftdo.core.DeploymentStage;
import software.amazon.awscdk.services.ssm.ParameterValueType;
import software.amazon.awscdk.services.ssm.StringListParameter;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.List;
import java.util.Optional;

/**
 * Utility class responsible for loading network-related outputs
 * from AWS SSM Parameter Store.
 *
 * <p>
 * This class acts as the read-side counterpart to the
 * {@link NetworkConstruct}, which writes parameters.
 * </p>
 */
public class NetworkParameterStore {

    private NetworkParameterStore(){
        // Prevent instantiation
    }

    /**
     * Loads all network outputs for a given deployment stage.
     *
     * <p>
     * This method retrieves values written by the
     * {@link NetworkConstruct} and assembles them into a
     * {@link NetworkOutputParameters} object.
     * </p>
     *
     * @param scope Construct used for parameter resolution
     *
     * @param stage Deployment environment to load parameters for
     *
     * @return fully populated {@link NetworkOutputParameters}
     */
    public static NetworkOutputParameters load(
            Construct scope,
            DeploymentStage stage
    ){
        return new NetworkOutputParameters(
                get(scope, stage, NetworkOutputs.PARAMETER_VPC_ID),
                get(scope, stage, NetworkOutputs.PARAMETER_HTTP_LISTENER),
                getOptional(scope, stage, NetworkOutputs.PARAMETER_HTTPS_LISTENER),
                get(scope, stage, NetworkOutputs.PARAMETER_LOADBALANCER_SECURITY_GROUP_ID),
                get(scope, stage, NetworkOutputs.PARAMETER_ECS_CLUSTER_NAME),
                getList(scope, stage, NetworkOutputs.PARAMETER_PRIVATE_SUBNETS, ParameterValueType.AWS_EC2_SUBNET_ID),
                getList(scope, stage, NetworkOutputs.PARAMETER_PUBLIC_SUBNETS, ParameterValueType.AWS_EC2_SUBNET_ID),
                getList(scope, stage, NetworkOutputs.PARAMETER_AVAILABILITY_ZONES, ParameterValueType.AWS_EC2_AVAILABILITYZONE_NAME),
                get(scope, stage, NetworkOutputs.PARAMETER_LOAD_BALANCER_ARN),
                get(scope, stage, NetworkOutputs.PARAMETER_LOAD_BALANCER_DNS_NAME),
                get(scope, stage, NetworkOutputs.PARAMETER_LOAD_BALANCER_HOSTED_ZONE_ID)
        );
    }

    /**
     * Retrieves an optional parameter value.
     *
     * <p>
     * A stored string literal {@code "null"} is interpreted
     * as an absent value.
     * </p>
     */
    private static Optional<String> getOptional(Construct scope, DeploymentStage stage, String key) {
        String value = get(scope, stage, key);
        return "null".equals(value) ? Optional.empty() : Optional.of(value);
    }

    /**
     * Retrieves a single string parameter from SSM.
     */
    private static String get(Construct scope, DeploymentStage stage, String key) {
        return StringParameter.fromStringParameterName(
                scope,
                key,
                parameterName(stage, key)
        ).getStringValue();
    }

    /**
     * Retrieves a list-valued parameter from SSM.
     */
    private static List<String> getList(Construct scope, DeploymentStage stage, String key, ParameterValueType valueType){
        return StringListParameter.valueForTypedListParameter(scope, parameterName(stage, key), valueType);
    }

    /**
     * Constructs the fully-qualified SSM parameter name
     * using the deployment stage.
     */
    private static String parameterName(DeploymentStage stage, String key) {
        return stage.getName() + "-network-" + key;
    }
}
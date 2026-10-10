package com.swiftdo.repository;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.iam.AccountPrincipal;
import software.constructs.Construct;

import java.util.Collections;
import java.util.Objects;

/**
 * A CDK Construct is responsible for provisioning
 * and configuring an Amazon Elastic Container Registry
 * repository
 *
 * <p>
 * This construct encapsulates all ECR-related infrastructure concerns
 * for the application, including:
 * </p>
 * <ul>
 *     <li>Creation of an ECR repository</li>
 *      <li>Lifecycle policies to control image retention</li>
 *      <li>IAM permissions for pushing and pulling images</li>
 * </ul>
 *
 */
public class RepositoryConstruct extends Construct{

    /**
     *The ECR repository instance created by this construct.
     */
    private final IRepository repository;

    public RepositoryConstruct(
            final Construct scope,
            final String constructId,
            final RepositoryInputParameters inputParameters
    ) {
        super(scope, constructId);

        // Create the ECR Repository
        this.repository = Repository.Builder.create(this, "EcrRepository")

                // Repository name is derived from application name
                // Here it would be swift-do-repository-stack
                .repositoryName(inputParameters.repositoryName)

                // Removal policy controls what happens to repo
                // when this stack is deleted
                .removalPolicy(inputParameters.retainRegistryOnDelete ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)

                // Lifecycle rules prevent unbounded image growth,
                // which could otherwise lead to unnecessary storage costs
                // - rulePriority dictates which image to delete first
                // - description describes the lifecycle rule
                // - maxImageCount dictates the maximum number of images to be stored in the repo
                .lifecycleRules(Collections.singletonList(LifecycleRule.builder()
                        .rulePriority(1)
                        .description(String.format("Limit to %d images", inputParameters.maxImageCount))
                        .maxImageCount(inputParameters.maxImageCount)
                        .build()
                ))
                .build();

        // Grant push and pull permissions to
        // the specified AWS account
        this.repository.grantPullPush(
                new AccountPrincipal(inputParameters.accountId)
        );
        // Todo: revisit the permissions to be assigned for Pull Request for ECS.
    }

    /**
     * Immutable configuration holder for {@link RepositoryConstruct}
     */
    public static class RepositoryInputParameters{

        /**
         * AWS Account ID that will be granted permission
         * to push and pull images from the repository.
         */
        private final String accountId;

        /**
         * Logical application name used as the base
         * for the repository name.
         */
        private final String repositoryName;

        /**
         * Maximum number of container images
         * retained in the repository.
         */
        private final int maxImageCount;

        /**
         * Determines whether the repository should be retained
         * or destroyed when the stack is deleted.
         */
        private final boolean retainRegistryOnDelete;

        /**
         * Creates a validated configuration object for the repository.
         *
         * @param repositoryName
         *   Base name used for constructing the ECR repository name.
         *
         * @param accountId
         *   AWS Account ID that receives push/pull permissions.
         *
         * @param maxImageCount
         *   Maximum number of images retained by lifecycle rules.
         *
         * @param retainRegistryOnDelete
         *   Whether the repository should survive stack deletion.
         */
        public  RepositoryInputParameters(
                String repositoryName,
                String accountId,
                int maxImageCount,
                boolean retainRegistryOnDelete
        ){
            Objects.requireNonNull(accountId, "accountId must not be null");
            Objects.requireNonNull(repositoryName, "repositoryName must not be null");
            this.accountId = accountId;
            this.repositoryName = repositoryName;
            this.maxImageCount = maxImageCount;
            this.retainRegistryOnDelete = retainRegistryOnDelete;
        }

    }
}

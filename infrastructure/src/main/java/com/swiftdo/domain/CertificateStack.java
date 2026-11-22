package com.swiftdo.domain;

import com.swiftdo.core.ApplicationEnvironment;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.certificatemanager.ICertificate;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

/**
 * CDK stack for provisioning an SSL/TLS certificate
 * using AWS Certificate Manager.
 *
 * <p>
 *     The certificate is validated using DNS validation via Route 53
 *     meaning ACM checks weather we own the domain name via the specific DNS name
 *     stored in Route 53.
 * </>
 * <p>
 *     The Stack will be used to issue certificate both for
 *     frontend in us-east-1 for CDN and backend int ap-south-1 and the
 *     arn will be made available via Cloudformation exports.
 * </p>
 */
public class CertificateStack extends Stack{

    /**
     * Creates a new {@link CertificateStack}.
     *
     * @param scope                  parent construct (usually the CDK {@link App})
     * @param constructId            logical identifier for the stack
     * @param awsEnvironment         AWS account and region configuration
     * @param applicationEnvironment application-level environment metadata
     * @param applicationDomain      fully qualified domain name for the certificate
     * @param hostedZoneDomain       root domain of the Route 53 hosted zone
     */
    public CertificateStack(
            final Construct scope,
            final String constructId,
            final Environment awsEnvironment,
            final ApplicationEnvironment applicationEnvironment,
            final String applicationDomain,
            final String hostedZoneDomain
    ){
        super(
                scope,
                constructId,
                StackProps.builder()
                        .stackName(applicationEnvironment.getApplicationName() + "-certificate-stack")
                        .env(awsEnvironment)
                        .build()
        );

        /*
         * Fetch the Hosted Zone(container that holds DNS records)
         * for a specific domain.
         */
        IHostedZone hostedZone = HostedZone.fromLookup(
                this,
                "HostedZone",
                HostedZoneProviderProps.builder()
                        .domainName(hostedZoneDomain)
                        .build()
        );

        /*
         * Creating a new SSL DNS-validated certificate
         */
        ICertificate webCertificate = Certificate.Builder.create(this, "WebCertificate")
                .domainName(applicationDomain)
                .validation(CertificateValidation.fromDns(hostedZone))
                .build();

        /*
         * Exports the certificate ARN so it can be imported
         * and reused by other CloudFormation stacks like NetworkStack
         * or FrontendDomainStack
         */
        new CfnOutput(
                this,
                "SslCertificateArn",
                CfnOutputProps.builder()
                        .exportName("swift-do-SslCertificateArn")
                        .value(webCertificate.getCertificateArn())
                        .description("Certificate ARN which is to be used for setting up HTTPs")
                        .build()
        );
    }
}

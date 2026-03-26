package com.rytmo.server

import com.rytmo.library.exceptions.BridgeCustomerException
import com.rytmo.library.exceptions.EmailAlreadyExistsException
import com.rytmo.library.exceptions.KycLinkCreationException
import com.rytmo.library.services.BridgeApiException
import com.rytmo.library.services.BridgeCustomerService
import com.rytmo.library.services.KycService
import com.rytmo.library.services.OnboardingService
import com.rytmo.models.auth.AuthorizedUser
import com.rytmo.models.bridge.BridgeCustomer
import com.rytmo.models.bridge.CreateBridgeCustomerRequest
import com.rytmo.models.customer.Customer
import com.rytmo.models.customer.OnboardRequest
import com.rytmo.models.kyc.CreateKycLinkRequest
import com.rytmo.models.kyc.KycLinkResponse
import com.rytmo.models.onboarding.BridgeOnboardingStatus
import com.rytmo.server.auth.PrivyProtected
import com.rytmo.server.auth.filters.PrivyAuthFilterScope
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes
import org.jboss.logging.Logger

@Path("/customers")
@SecuritySchemes(
    SecurityScheme(
        securitySchemeName = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
    ),
)
class CustomerResource {
    @Inject lateinit var onboardingService: OnboardingService

    @Inject lateinit var kycService: KycService

    @Inject lateinit var bridgeCustomerService: BridgeCustomerService

    @Inject lateinit var log: Logger

    @POST
    @Path("/onboard")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "201",
        description = "Customer created",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = Customer::class),
            ),
        ],
    )
    fun onboard(request: OnboardRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val customer =
                onboardingService.onboard(
                    email = request.email,
                    name = request.name,
                    privyUserId = authorizedUser.userId,
                )

            Response.status(Response.Status.CREATED).entity(customer).build()
        } catch (e: EmailAlreadyExistsException) {
            Response.status(Response.Status.CONFLICT).entity(mapOf("error" to e.message)).build()
        }
    }

    @POST
    @Path("/kyc")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "201",
        description = "KYC link created",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = KycLinkResponse::class),
            ),
        ],
    )
    fun createKycLink(request: CreateKycLinkRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val kycLinkResponse =
                kycService.createKycLinkByExternalId(
                    externalId = authorizedUser.userId,
                    fullName = request.fullName,
                    email = request.email,
                    redirectUri = request.redirectUri,
                )
            Response.status(Response.Status.CREATED).entity(kycLinkResponse).build()
        } catch (e: KycLinkCreationException) {
            if (e.message?.contains("Customer not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to create KYC link"))
                    .build()
            }
        }
    }

    @POST
    @Path("/bridge")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "201",
        description = "Bridge customer created",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = BridgeCustomer::class),
            ),
        ],
    )
    fun createBridgeCustomer(request: CreateBridgeCustomerRequest, @Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val bridgeCustomer =
                bridgeCustomerService.createByExternalId(
                    externalId = authorizedUser.userId,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    email = request.email,
                )
            Response.status(Response.Status.CREATED).entity(bridgeCustomer).build()
        } catch (e: BridgeCustomerException) {
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to create Bridge customer"))
                    .build()
            }
        }
    }

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Current customer",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = Customer::class),
            ),
        ],
    )
    fun getCustomer(@Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        val customer =
            onboardingService.getCustomerByExternalId(authorizedUser.userId)
                ?: return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Customer not found"))
                    .build()

        return Response.ok(customer).build()
    }

    @GET
    @Path("/kyc")
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "KYC links for current customer",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(type = SchemaType.ARRAY, implementation = KycLinkResponse::class),
            ),
        ],
    )
    fun listKycLinks(@Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val kycLinks = kycService.listKycLinksByExternalId(authorizedUser.userId)
            Response.ok(kycLinks).build()
        } catch (e: KycLinkCreationException) {
            log.error("Failed to get KYC links: ${e.message}", e)
            if (e.message?.contains("not found") == true) {
                Response.status(Response.Status.NOT_FOUND).entity(mapOf("error" to e.message)).build()
            } else {
                Response.status(Response.Status.BAD_GATEWAY)
                    .entity(mapOf("error" to "Failed to get KYC links"))
                    .build()
            }
        }
    }

    @GET
    @Path("/me/onboarding/bridge")
    @Produces(MediaType.APPLICATION_JSON)
    @PrivyProtected
    @APIResponse(
        responseCode = "200",
        description = "Bridge onboarding status for current customer",
        content =
        [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = BridgeOnboardingStatus::class),
            ),
        ],
    )
    fun getBridgeOnboardingStatus(@Context requestContext: ContainerRequestContext): Response {
        val authorizedUser =
            requestContext.getProperty(PrivyAuthFilterScope.AUTHORIZED_USER_PROPERTY) as AuthorizedUser

        return try {
            val status =
                kycService.getOnboardingStatusByExternalId(authorizedUser.userId)
                    ?: return Response.status(Response.Status.NOT_FOUND)
                        .entity(mapOf("error" to "Customer not found"))
                        .build()

            Response.ok(status).build()
        } catch (e: BridgeApiException) {
            log.error("Failed to get onboarding status: ${e.message}", e)
            Response.status(Response.Status.BAD_GATEWAY)
                .entity(mapOf("error" to "Failed to get onboarding status"))
                .build()
        }
    }
}

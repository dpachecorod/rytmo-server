package com.rytmo.server

import com.rytmo.library.exceptions.EmailAlreadyExistsException
import com.rytmo.library.services.OnboardingService
import com.rytmo.models.customer.Customer
import com.rytmo.models.customer.OnboardRequest
import com.rytmo.server.test.PrivyTestProfile
import com.rytmo.server.utils.AccessTokenUtil
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.junit.mockito.MockitoConfig
import io.restassured.RestAssured
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.Instant

@QuarkusTest
@TestProfile(PrivyTestProfile::class)
class CustomerResourceTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @InjectMock
    @MockitoConfig(convertScopes = true)
    lateinit var onboardingService: OnboardingService

    @Test
    fun `onboard should create customer and return 201`() {
        val email = "john@example.com"
        val name = "John Doe"
        val now = Instant.now()

        val customer =
            Customer(
                id = "customer-123",
                email = email,
                name = name,
                createdAt = now,
                updatedAt = now,
            )

        whenever(onboardingService.onboard(any(), any(), any())).thenReturn(customer)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .contentType(ContentType.JSON)
                .body(OnboardRequest(email, name))
                .`when`()
                .post("/customers/onboard")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .`as`(Customer::class.java)

        assertEquals("customer-123", response.id)
        assertEquals(email, response.email)
        assertEquals(name, response.name)
        assertNotNull(response.createdAt)
        assertNotNull(response.updatedAt)
    }

    @Test
    fun `onboard should return 409 when email already exists`() {
        val email = "existing@example.com"
        val name = "John Doe"

        whenever(onboardingService.onboard(any(), any(), any()))
            .thenThrow(EmailAlreadyExistsException(email))

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .contentType(ContentType.JSON)
            .body(OnboardRequest(email, name))
            .`when`()
            .post("/customers/onboard")
            .then()
            .statusCode(409)
    }

    @Test
    fun `onboard should return 401 when not authenticated`() {
        val email = "john@example.com"
        val name = "John Doe"

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(OnboardRequest(email, name))
            .`when`()
            .post("/customers/onboard")
            .then()
            .statusCode(401)
    }
}

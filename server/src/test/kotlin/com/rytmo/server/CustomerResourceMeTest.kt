package com.rytmo.server

import com.rytmo.library.services.OnboardingService
import com.rytmo.models.customer.Customer
import com.rytmo.server.test.PrivyTestProfile
import com.rytmo.server.utils.AccessTokenUtil
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.junit.mockito.MockitoConfig
import io.restassured.RestAssured
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
class CustomerResourceMeTest {
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
    fun `getCustomer should return 200 with customer profile`() {
        val now = Instant.now()

        val customer =
            Customer(
                id = "customer-123",
                email = "john@example.com",
                name = "John Doe",
                createdAt = now,
                updatedAt = now,
            )
        whenever(onboardingService.getCustomerByExternalId(any())).thenReturn(customer)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        val response =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .`when`()
                .get("/customers/me")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(Customer::class.java)

        assertEquals("customer-123", response.id)
        assertEquals("john@example.com", response.email)
        assertEquals("John Doe", response.name)
        assertNotNull(response.createdAt)
        assertNotNull(response.updatedAt)
    }

    @Test
    fun `getCustomer should return 401 without authentication token`() {
        RestAssured.given().`when`().get("/customers/me").then().statusCode(401)
    }

    @Test
    fun `getCustomer should return 404 when customer not found`() {
        whenever(onboardingService.getCustomerByExternalId(any())).thenReturn(null)

        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)

        RestAssured.given()
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/customers/me")
            .then()
            .statusCode(404)
    }
}

package com.rytmo.server

import com.rytmo.models.GreetingOutput
import com.rytmo.server.test.PrivyTestProfile
import com.rytmo.server.utils.AccessTokenUtil
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@QuarkusTest
@TestProfile(PrivyTestProfile::class)
public class GreetingResourceTest {
    @Inject
    @ConfigProperty(name = "privy.private-key-pem")
    lateinit var privateKeyPem: String

    @Inject
    @ConfigProperty(name = "privy.app-id")
    lateinit var appId: String

    @Test
    fun testHelloEndpoint() {
        val accessToken = AccessTokenUtil.generateMockAccessToken(privateKeyPem, appId)
        val inputName = "David"
        val actual: GreetingOutput =
            RestAssured.given()
                .header("Authorization", "Bearer $accessToken")
                .queryParam("name", inputName)
                .`when`()
                .get("/hello")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .`as`(GreetingOutput::class.java)
        assertEquals(inputName, actual.name)
    }
}

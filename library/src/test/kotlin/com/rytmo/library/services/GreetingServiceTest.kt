package com.rytmo.library.services

import com.rytmo.models.GreetingInput
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class GreetingServiceTest {
    private lateinit var service: GreetingService

    @BeforeEach
    fun setUp() {
        service = GreetingService()
    }

    @Test
    fun `should greet with name`() {
        val actual = service.greet(GreetingInput("John"))
        assert(actual == "John")
    }
}

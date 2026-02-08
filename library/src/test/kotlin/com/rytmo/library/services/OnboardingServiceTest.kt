package com.rytmo.library.services

import com.rytmo.library.exceptions.EmailAlreadyExistsException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.library.persistence.customers.CustomerDao
import com.rytmo.library.persistence.customers.CustomerDynamoDbBean
import com.rytmo.library.persistence.customers.CustomerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class OnboardingServiceTest {
    private lateinit var customerService: CustomerService
    private lateinit var customerIdentityService: CustomerIdentityService
    private lateinit var customerDao: CustomerDao
    private lateinit var onboardingService: OnboardingService

    @BeforeEach
    fun setUp() {
        customerService = mock()
        customerIdentityService = mock()
        customerDao = mock()
        onboardingService = OnboardingService(customerService, customerIdentityService, customerDao)
    }

    @Test
    fun `onboard should create customer and link privy identity`() {
        val email = "john@example.com"
        val name = "John Doe"
        val privyUserId = "privy-123"

        val createdCustomer =
            CustomerDynamoDbBean().apply {
                id = "customer-id"
                this.email = email
                this.name = name
                createdAt = Instant.now()
                updatedAt = Instant.now()
            }

        whenever(customerDao.existsByEmail(email)).thenReturn(false)
        whenever(customerService.createCustomer(email, name)).thenReturn(createdCustomer)
        whenever(customerIdentityService.linkIdentity(any(), any(), any()))
            .thenReturn(
                CustomerIdentityDynamoDbBean(),
            )

        val result = onboardingService.onboard(email, name, privyUserId)

        assertEquals("customer-id", result.id)
        assertEquals(email, result.email)
        assertEquals(name, result.name)

        verify(customerDao).existsByEmail(email)
        verify(customerService).createCustomer(email, name)
        verify(customerIdentityService)
            .linkIdentity(
                internalCustomerId = "customer-id",
                provider = "privy",
                externalId = privyUserId,
            )
    }

    @Test
    fun `onboard should throw exception when email already exists`() {
        val email = "existing@example.com"
        val name = "John Doe"
        val privyUserId = "privy-123"

        whenever(customerDao.existsByEmail(email)).thenReturn(true)

        val exception =
            assertThrows(EmailAlreadyExistsException::class.java) {
                onboardingService.onboard(email, name, privyUserId)
            }

        assertEquals("A customer with email 'existing@example.com' already exists", exception.message)

        verify(customerDao).existsByEmail(email)
        verify(customerService, never()).createCustomer(any(), any())
        verify(customerIdentityService, never()).linkIdentity(any(), any(), any())
    }

    @Test
    fun `onboard should use privy as provider constant`() {
        val email = "john@example.com"
        val name = "John Doe"
        val privyUserId = "privy-123"

        val createdCustomer =
            CustomerDynamoDbBean().apply {
                id = "customer-id"
                this.email = email
                this.name = name
            }

        whenever(customerDao.existsByEmail(email)).thenReturn(false)
        whenever(customerService.createCustomer(email, name)).thenReturn(createdCustomer)

        val captor = argumentCaptor<String>()
        whenever(
            customerIdentityService.linkIdentity(
                any(),
                captor.capture(),
                any(),
            ),
        )
            .thenReturn(CustomerIdentityDynamoDbBean())

        onboardingService.onboard(email, name, privyUserId)

        assertEquals(OnboardingService.PROVIDER_PRIVY, captor.firstValue)
    }
}

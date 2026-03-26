package com.rytmo.library.services

import com.rytmo.library.exceptions.DeviceTokenException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityDao
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.library.persistence.devicetokens.DeviceTokenDao
import com.rytmo.library.persistence.devicetokens.DeviceTokenDynamoDbBean
import com.rytmo.library.persistence.devicetokens.DeviceTokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

class ExpoDeviceTokenServiceTest {
    private lateinit var customerIdentityDao: CustomerIdentityDao
    private lateinit var deviceTokenDao: DeviceTokenDao
    private lateinit var customerIdentityService: CustomerIdentityService
    private lateinit var deviceTokenService: DeviceTokenService
    private lateinit var expoDeviceTokenService: ExpoDeviceTokenService

    private val externalId = "did:privy:user123"
    private val customerId = "customer-123"
    private val expoToken = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]"

    @BeforeEach
    fun setUp() {
        customerIdentityDao = mock()
        deviceTokenDao = mock()
        customerIdentityService = CustomerIdentityService(customerIdentityDao)
        deviceTokenService = DeviceTokenService(deviceTokenDao)
        expoDeviceTokenService = ExpoDeviceTokenService(customerIdentityService, deviceTokenService)
    }

    private fun customerIdentityWithId(internalId: String) = com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean().apply {
        internalCustomerId = internalId
    }

    @Test
    fun `registerByExternalId creates token when not exists`() {
        whenever(customerIdentityDao.findByExternalId(externalId))
            .thenReturn(customerIdentityWithId(customerId))
        whenever(deviceTokenDao.get(any(), any())).thenReturn(null)
        val captor = argumentCaptor<DeviceTokenDynamoDbBean>()
        whenever(deviceTokenDao.create(captor.capture())).thenAnswer { invocation ->
            val bean = invocation.getArgument<DeviceTokenDynamoDbBean>(0)
            bean.createdAt = Instant.now()
            bean.updatedAt = Instant.now()
            bean
        }

        val response = expoDeviceTokenService.registerByExternalId(externalId, expoToken)

        assertEquals(expoToken, response.expoToken)
        assertNotNull(response.createdAt)
        assertEquals(customerId, captor.firstValue.customerId)
        assertEquals(expoToken, captor.firstValue.expoToken)
    }

    @Test
    fun `registerByExternalId calls update not create when token already exists (upsert)`() {
        whenever(customerIdentityDao.findByExternalId(externalId))
            .thenReturn(customerIdentityWithId(customerId))
        val existing =
            DeviceTokenDynamoDbBean().apply {
                this.customerId = customerId
                this.tokenHash = "somehash"
                this.expoToken = expoToken
                this.createdAt = Instant.now()
                this.updatedAt = Instant.now()
            }
        whenever(deviceTokenDao.get(any(), any())).thenReturn(existing)
        val updateCaptor = argumentCaptor<DeviceTokenDynamoDbBean>()
        whenever(deviceTokenDao.update(updateCaptor.capture())).thenAnswer { invocation ->
            val bean = invocation.getArgument<DeviceTokenDynamoDbBean>(0)
            bean.updatedAt = Instant.now()
            bean
        }

        expoDeviceTokenService.registerByExternalId(externalId, expoToken)

        verify(deviceTokenDao).update(any())
        verify(deviceTokenDao, never()).create(any())
    }

    @Test
    fun `registerByExternalId throws DeviceTokenException when customer not found`() {
        whenever(customerIdentityDao.findByExternalId(externalId)).thenReturn(null)

        assertThrows(DeviceTokenException::class.java) {
            expoDeviceTokenService.registerByExternalId(externalId, expoToken)
        }
    }

    @Test
    fun `deleteByExternalId deletes by customerId and tokenHash`() {
        whenever(customerIdentityDao.findByExternalId(externalId))
            .thenReturn(customerIdentityWithId(customerId))

        expoDeviceTokenService.deleteByExternalId(externalId, expoToken)

        verify(deviceTokenDao).delete(any(), any())
    }

    @Test
    fun `deleteByExternalId throws DeviceTokenException when customer not found`() {
        whenever(customerIdentityDao.findByExternalId(externalId)).thenReturn(null)

        assertThrows(DeviceTokenException::class.java) {
            expoDeviceTokenService.deleteByExternalId(externalId, expoToken)
        }
    }
}

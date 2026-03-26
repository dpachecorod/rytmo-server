package com.rytmo.library.services

import com.rytmo.library.exceptions.DeviceTokenException
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.library.persistence.devicetokens.DeviceTokenDynamoDbBean
import com.rytmo.library.persistence.devicetokens.DeviceTokenService
import com.rytmo.models.devicetoken.DeviceTokenResponse
import java.security.MessageDigest

class ExpoDeviceTokenService(private val customerIdentityService: CustomerIdentityService, private val deviceTokenService: DeviceTokenService) {
    fun registerByExternalId(externalId: String, expoToken: String): DeviceTokenResponse {
        val customerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw DeviceTokenException("Customer not found for external ID: $externalId")
        val tokenHash = sha256hex(expoToken)
        val existing = deviceTokenService.get(customerId, tokenHash)
        val bean =
            if (existing != null) {
                deviceTokenService.update(existing)
            } else {
                val newBean = DeviceTokenDynamoDbBean()
                newBean.customerId = customerId
                newBean.tokenHash = tokenHash
                newBean.expoToken = expoToken
                deviceTokenService.create(newBean)
            }
        return toResponse(bean)
    }

    fun deleteByExternalId(externalId: String, expoToken: String) {
        val customerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId)
                ?: throw DeviceTokenException("Customer not found for external ID: $externalId")
        val tokenHash = sha256hex(expoToken)
        deviceTokenService.delete(customerId, tokenHash)
    }

    private fun sha256hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun toResponse(bean: DeviceTokenDynamoDbBean): DeviceTokenResponse = DeviceTokenResponse(
        expoToken = bean.expoToken,
        createdAt = bean.createdAt?.toString(),
        updatedAt = bean.updatedAt?.toString(),
    )
}

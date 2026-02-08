package com.rytmo.library.persistence.customeridentities

import com.rytmo.library.persistence.CompositeKeyBaseService

class CustomerIdentityService(private val customerIdentityDao: CustomerIdentityDao) : CompositeKeyBaseService<CustomerIdentityDynamoDbBean>(customerIdentityDao) {
    fun linkIdentity(internalCustomerId: String, provider: String, externalId: String): CustomerIdentityDynamoDbBean {
        val identity = CustomerIdentityDynamoDbBean()
        identity.internalCustomerId = internalCustomerId
        identity.provider = provider
        identity.externalId = externalId
        return create(identity)
    }

    fun getIdentity(internalCustomerId: String, provider: String): CustomerIdentityDynamoDbBean? = get(internalCustomerId, provider)

    fun unlinkIdentity(internalCustomerId: String, provider: String) = delete(internalCustomerId, provider)

    fun listIdentitiesForCustomer(internalCustomerId: String): List<CustomerIdentityDynamoDbBean> = listByPartitionKey(internalCustomerId)

    fun getInternalCustomerIdByExternalId(externalId: String): String? = customerIdentityDao.findByExternalId(externalId)?.internalCustomerId

    fun findIdentityByExternalId(extId: String): CustomerIdentityDynamoDbBean? = customerIdentityDao.findByExternalId(extId)
}

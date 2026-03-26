package com.rytmo.library.persistence.devicetokens

import com.rytmo.library.persistence.CompositeKeyBaseService

class DeviceTokenService(deviceTokenDao: DeviceTokenDao) : CompositeKeyBaseService<DeviceTokenDynamoDbBean>(deviceTokenDao)

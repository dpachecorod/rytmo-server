package com.rytmo.library.persistence

import java.time.Instant

interface CompositeKeyPersistable {
    var partitionKey: String
    var sortKey: String
    var createdAt: Instant?
    var updatedAt: Instant?
    var ttl: Long?
}

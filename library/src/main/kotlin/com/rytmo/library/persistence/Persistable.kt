package com.rytmo.library.persistence

import java.time.Instant

interface Persistable {
    var id: String
    var createdAt: Instant?
    var updatedAt: Instant?
    var ttl: Long?
}

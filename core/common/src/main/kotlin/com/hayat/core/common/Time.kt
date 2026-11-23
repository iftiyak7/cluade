package com.hayat.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** Source of the current time and zone. Inject this instead of calling Instant.now() directly. */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
    fun zonedNow(): ZonedDateTime = now().atZone(zone())
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Generates collision-resistant local identifiers. */
interface IdGenerator {
    fun newId(): String
}

class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = java.util.UUID.randomUUID().toString()
}

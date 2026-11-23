package com.hayat.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Injectable dispatchers so tests can substitute a TestDispatcher. */
data class HayatDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)

/** Qualifier for the process-wide CoroutineScope that outlives screens (receivers, workers). */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

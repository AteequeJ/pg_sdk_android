package com.pgsdk.di

import android.content.Context
import com.pgsdk.core.PGConfig
import com.pgsdk.network.PGApiClient
import com.pgsdk.network.PGPaymentRepository
import com.pgsdk.storage.PGSecureStorage

/**
 * Minimal, hand-rolled dependency container for the SDK's internals.
 *
 * Deliberately not Hilt/Dagger: pulling a DI framework into a library forces that
 * framework (and its annotation processor / KSP version) onto every merchant app that
 * depends on us, which is a common source of dependency conflicts for SDK consumers.
 * The object graph here is small and static enough that manual wiring is clearer and
 * has zero footprint on host apps.
 */
internal object PGServiceLocator {

    @Volatile
    private var repository: PGPaymentRepository? = null

    @Volatile
    private var secureStorage: PGSecureStorage? = null

    fun initialize(context: Context, config: PGConfig) {
        val apiService = PGApiClient.create(config)
        repository = PGPaymentRepository(apiService)
        secureStorage = PGSecureStorage.create(context)
    }

    fun requireRepository(): PGPaymentRepository =
        repository ?: error("PGServiceLocator not initialized -- call PGPaymentSDK.initialize() first")

    fun requireSecureStorage(): PGSecureStorage =
        secureStorage ?: error("PGServiceLocator not initialized -- call PGPaymentSDK.initialize() first")

    /** Visible for testing: allows tests to inject fakes without touching real networking. */
    internal fun overrideForTesting(repository: PGPaymentRepository, secureStorage: PGSecureStorage) {
        this.repository = repository
        this.secureStorage = secureStorage
    }

    internal fun reset() {
        repository = null
        secureStorage = null
    }
}

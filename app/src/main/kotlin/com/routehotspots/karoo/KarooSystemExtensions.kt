package com.routehotspots.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Flow-wrapper om [KarooSystemService.addConsumer] — dit zit niet in de gepubliceerde
 * `karoo-ext`-library, maar is het exacte patroon uit de officiële sample-extensie
 * (`app/src/main/kotlin/io/hammerhead/sampleext/extension/Extensions.kt` in
 * hammerheadnav/karoo-ext), hier 1-op-1 overgenomen.
 */
inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> {
    return callbackFlow {
        val listenerId = addConsumer<T> {
            trySend(it)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

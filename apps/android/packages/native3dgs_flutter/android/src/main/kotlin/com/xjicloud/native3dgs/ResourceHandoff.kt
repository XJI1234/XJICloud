package com.xjicloud.native3dgs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal suspend fun <T : Any> acquireWithHandoff(dispatcher: CoroutineDispatcher,
    release: (T) -> Unit, acquire: suspend () -> T): T {
    // withContext can discard its result on the return dispatcher. Keep an owner
    // outside that boundary until the caller has actually received the resource.
    var unclaimed: T? = null
    try {
        val result = withContext(dispatcher) { acquire().also { unclaimed = it } }
        unclaimed = null
        return result
    } finally { unclaimed?.let(release) }
}

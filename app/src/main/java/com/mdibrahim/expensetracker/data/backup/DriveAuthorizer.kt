package com.mdibrahim.expensetracker.data.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface DriveAuth {
    data class Granted(val accessToken: String) : DriveAuth

    /** First use, or access was revoked: the UI must launch this to ask the user. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuth
}

/**
 * Gets a Google Drive access token via Play services' AuthorizationClient.
 *
 * Scope is drive.file: the app sees only files it created itself, never the rest
 * of the user's Drive. Once granted, [authorize] returns a fresh token silently --
 * which is what lets the background auto-backup work without UI.
 */
@Singleton
class DriveAuthorizer @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()

    suspend fun authorize(): DriveAuth {
        val result = client.authorize(request).await()
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) {
            DriveAuth.NeedsConsent(pending)
        } else {
            DriveAuth.Granted(checkNotNull(result.accessToken) { "No access token returned" })
        }
    }

    /** Token from the consent screen's result, or null if the user backed out. */
    fun tokenFromConsentResult(data: Intent?): String? =
        runCatching { client.getAuthorizationResultFromIntent(data).accessToken }.getOrNull()

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}

/** Minimal Task -> coroutine bridge, avoiding the kotlinx-coroutines-play-services dependency. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

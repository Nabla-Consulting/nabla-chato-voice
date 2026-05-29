package com.nabla.chatovoice.data.remote

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import androidx.annotation.MainThread
import com.nabla.chatovoice.R
import com.nabla.chatovoice.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MsalRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scopes = listOf("Files.ReadWrite", "User.Read")
    private var msalApp: ISingleAccountPublicClientApplication? = null

    private suspend fun getApp(): ISingleAccountPublicClientApplication {
        msalApp?.let { return it }
        // MSAL app creation MUST run on Main thread
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                PublicClientApplication.createSingleAccountPublicClientApplication(
                    context,
                    R.raw.msal_config,
                    object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                        override fun onCreated(app: ISingleAccountPublicClientApplication) {
                            msalApp = app
                            cont.resume(app)
                        }
                        override fun onError(e: MsalException) {
                            cont.resumeWithException(e)
                        }
                    }
                )
            }
        }
    }

    /** Try silent token first; returns null if interactive sign-in is required. */
    suspend fun acquireTokenSilent(): String? {
        return try {
            // ALL MSAL calls must run on IO — never on Main
            withContext(Dispatchers.IO) {
                val app = getApp()
                DebugLogger.log("MSAL", "acquireTokenSilent: checking current account")
                val currentAccountResult = app.currentAccount
                val account = currentAccountResult?.currentAccount
                DebugLogger.log("MSAL", "currentAccount: ${account?.username ?: "none"}")
                if (account == null) return@withContext null
                // Use the account's own authority — personal accounts have a specific tenant
                val authority = account.authority
                    ?: "https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad"
                DebugLogger.log("MSAL", "using authority: $authority")
                val params = AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(authority)
                    .withScopes(scopes)
                    .build()
                val result = app.acquireTokenSilent(params)
                DebugLogger.log("MSAL", "silent token ok")
                result.accessToken
            }
        } catch (e: Exception) {
            DebugLogger.log("MSAL", "silent failed: ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Get a graph token using silent first, then interactive fallback (Main).
     * acquireTokenSilent() handles its own threading internally now.
     * acquireTokenInteractive uses app.acquireToken which requires the Main thread.
     */
    suspend fun getGraphToken(activity: Activity? = null): String? {
        DebugLogger.log("MSAL", "getGraphToken called, activity=${activity?.let { it::class.simpleName } ?: "null"}")
        val silent = acquireTokenSilent()
        DebugLogger.log("MSAL", "silent result: ${if (silent != null) "OK" else "null"}")
        if (silent != null) return silent
        if (activity == null) {
            DebugLogger.log("MSAL", "no activity for interactive, returning null")
            return null
        }
        // Sign out first if there's a stale account — avoids "account does not match" error
        withContext(Dispatchers.IO) {
            try {
                val app = getApp()
                if (app.currentAccount?.currentAccount != null) {
                    DebugLogger.log("MSAL", "signing out stale account before interactive")
                    app.signOut()
                }
            } catch (e: Exception) {
                DebugLogger.log("MSAL", "signout error (ignored): ${e.message}")
            }
        }
        return withContext(Dispatchers.Main) {
            DebugLogger.log("MSAL", "launching interactive auth...")
            try {
                val token = acquireTokenInteractive(activity)
                DebugLogger.log("MSAL", "interactive auth OK")
                token
            } catch (e: Exception) {
                DebugLogger.log("MSAL", "interactive failed: ${e::class.simpleName}: ${e.message}")
                null
            }
        }
    }

    /** Interactive sign-in - MUST be called from the Main thread (MSAL requirement). */
    @MainThread
    suspend fun acquireTokenInteractive(activity: Activity): String {
        val app = getApp() // ensures msalApp is initialized before interactive
        return withContext(Dispatchers.Main) { suspendCancellableCoroutine { cont ->
            val params = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(scopes)
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        DebugLogger.log("MSAL", "interactive token ok")
                        cont.resume(result.accessToken)
                    }
                    override fun onError(e: MsalException) {
                        cont.resumeWithException(e)
                    }
                    override fun onCancel() {
                        cont.resumeWithException(Exception("Sign-in cancelled"))
                    }
                })
                .build()
            app.acquireToken(params)
        } }
    }

    fun isSignedIn(): Boolean = msalApp?.currentAccount?.currentAccount != null
}

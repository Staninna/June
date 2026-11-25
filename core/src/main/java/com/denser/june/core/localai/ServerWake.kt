package com.denser.june.core.localai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Starts the server without opening its app (docs/LOCAL_LLM_API.md §3.5): bind its wake service,
 * call `wake()`, unbind. The server checks that the caller is a paired app.
 */
object ServerWake {
    const val ACTION_WAKE = "${LocalAiContract.SERVER_PACKAGE}.action.WAKE"
    private const val DESCRIPTOR = "io.shubham0204.smollmandroid.IWake"
    private const val TRANSACTION_WAKE = IBinder.FIRST_CALL_TRANSACTION
    private const val BIND_TIMEOUT_MS = 5_000L

    enum class Result {
        /** The server runs or is starting; poll `/health`. */
        Started,
        /** This app is not paired (or was revoked); pair again. */
        NotPaired,
        /** The server has no model; the user has to pick one in the server app. */
        NoModel,
        /**
         * Android did not let the server start: the client is in the background and the server's
         * battery use is restricted. Open the server app instead.
         */
        NotAllowed,
        /** Not installed, too old to have a wake service, or the call failed. */
        Unavailable,
    }

    suspend fun wake(context: Context, serverPackage: String = LocalAiContract.SERVER_PACKAGE): Result {
        val connection = Connection()
        val intent = Intent(ACTION_WAKE).setPackage(serverPackage)
        val bound =
            try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (_: SecurityException) {
                false
            }
        try {
            if (!bound) return Result.Unavailable
            val binder = withTimeoutOrNull(BIND_TIMEOUT_MS) { connection.binder.await() } ?: return Result.Unavailable
            return withContext(Dispatchers.IO) { call(binder) }
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }

    private fun call(binder: IBinder): Result {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            binder.transact(TRANSACTION_WAKE, data, reply, 0)
            reply.readException()
            when (reply.readInt()) {
                0 -> Result.Started
                1 -> Result.NotPaired
                2 -> Result.NoModel
                3 -> Result.NotAllowed
                else -> Result.Unavailable
            }
        } catch (_: RemoteException) {
            Result.Unavailable
        } catch (_: RuntimeException) {
            Result.Unavailable
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private class Connection : ServiceConnection {
        val binder = CompletableDeferred<IBinder?>()

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {}

        override fun onNullBinding(name: ComponentName?) {
            binder.complete(null)
        }

        override fun onBindingDied(name: ComponentName?) {
            binder.complete(null)
        }
    }
}

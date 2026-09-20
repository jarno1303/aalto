package fi.aalto.radio

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the phone has a working internet connection, followed for the
 * whole process from one system callback. Lets the screen say "waiting for
 * the network" instead of a bare error while the station cannot play.
 */
internal object NetworkState {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        _online.value = runCatching {
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }.getOrDefault(true)
        runCatching {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }

                override fun onLost(network: Network) {
                    _online.value = false
                }
            })
        }
    }
}

@Composable
internal fun rememberOnline(): Boolean {
    NetworkState.start(LocalContext.current)
    val online by NetworkState.online.collectAsState()
    return online
}

package com.ultrasys.ultrachat.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Разрешения и состояние Bluetooth. На Android 12+ используются runtime-
 * разрешения BLUETOOTH_*, на более старых версиях для поиска устройств
 * требуется геолокация.
 */
object NetworkPermissions {
    data class Snapshot(
        val missingPermissions: List<String> = emptyList(),
        val locationEnabled: Boolean = false,
        val bluetoothEnabled: Boolean = false,
        val bluetoothSupported: Boolean = false,
    ) {
        val runtimeGranted: Boolean
            get() = missingPermissions.isEmpty()
    }

    fun requiredRuntimePermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        return permissions.distinct().toTypedArray()
    }

    fun snapshot(context: Context): Snapshot {
        val appContext = context.applicationContext
        val missing = requiredRuntimePermissions().filter {
            ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
        }
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        val bluetoothEnabled = runCatching {
            bluetoothAdapter?.isEnabled == true
        }.getOrDefault(false)

        return Snapshot(
            missingPermissions = missing,
            locationEnabled = isLocationEnabled(appContext),
            bluetoothEnabled = bluetoothEnabled,
            bluetoothSupported = bluetoothAdapter != null,
        )
    }

    fun hasBluetoothAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val required = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }.getOrDefault(false)
    }
}

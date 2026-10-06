package com.bjlure.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bjlure.app.ui.AppRoot
import com.bjlure.app.ui.FishingViewModel
import com.bjlure.app.ui.location.LocationProvider
import com.bjlure.app.ui.theme.BjLureTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: FishingViewModel by viewModels()

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            fetchLocation()
        } else {
            viewModel.onLocationFailed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BjLureTheme {
                AppRoot(
                    viewModel = viewModel,
                    onRequestLocation = { askLocation() },
                )
            }
        }
        // 首次进入就尝试定位；没权限时界面上的按钮会再问一次
        if (LocationProvider.hasPermission(this)) {
            fetchLocation()
        }
    }

    private fun askLocation() {
        viewModel.onLocating()
        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    private fun fetchLocation() {
        viewModel.onLocating()
        lifecycleScope.launch {
            val loc = LocationProvider.currentLocation(this@MainActivity)
            if (loc != null) {
                viewModel.onLocation(loc.latitude, loc.longitude)
            } else {
                viewModel.onLocationFailed()
            }
        }
    }
}

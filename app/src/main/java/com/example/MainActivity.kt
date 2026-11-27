package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.KernPulseApp
import com.example.ui.KernPulseViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: KernPulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KernPulseApp(viewModel = viewModel)
        }
    }
}

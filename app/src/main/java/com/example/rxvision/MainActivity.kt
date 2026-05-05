package com.example.rxvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.rxvision.ui.theme.DarkBackground
import com.example.rxvision.ui.theme.RxVisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RxVisionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = DarkBackground
                ) {
                    RxVisionScreen()
                }
            }
        }
    }
}
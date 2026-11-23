package com.codeside.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.codeside.app.ui.CodeSideApp
import com.codeside.app.ui.theme.CodeSideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodeSideTheme {
                CodeSideApp()
            }
        }
    }
}

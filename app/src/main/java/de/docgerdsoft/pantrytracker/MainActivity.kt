package de.docgerdsoft.pantrytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PantryTrackerApp).container
        setContent {
            PantryTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PantryTrackerNavGraph(container = container)
                }
            }
        }
    }
}

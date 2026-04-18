package com.example.whereabouts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whereabouts.navigation.WhereAboutsNavGraph
import com.example.whereabouts.ui.theme.WhereAboutsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhereAboutsTheme {
                WhereAboutsNavGraph()
            }
        }
    }
}

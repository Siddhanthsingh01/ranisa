package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.RanisaApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Set default timezone to Asia/Kolkata for consistent IST time in both emulator and devices
    try {
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "Failed to set default timezone", e)
    }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        RanisaApp()
      }
    }
  }
}


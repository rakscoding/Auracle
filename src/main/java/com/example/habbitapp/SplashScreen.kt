package com.example.habbitapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*

class SplashScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Add a delay and decide where to navigate next
        GlobalScope.launch {
            delay(3000L) // 3-second delay

            // Check if the user is registered
//            val sharedPreferences = getSharedPreferences("HabbitAppPrefs", MODE_PRIVATE)
//            val isRegistered = sharedPreferences.getBoolean("isRegistered", false)

            // Navigate based on registration state
//            val intent = if (isRegistered) {
//                Intent(this@SplashScreen, MainActivity::class.java)
//            } else {
//                Intent(this@SplashScreen, RegisterActivity::class.java)
//            }
            // Temporary change for testing
            val intent = Intent(this@SplashScreen, RegisterActivity::class.java)

            startActivity(intent)
            finish() // Close the SplashScreen activity
        }
    }
}

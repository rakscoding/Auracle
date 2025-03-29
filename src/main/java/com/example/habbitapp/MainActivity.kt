package com.example.habbitapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Retrieve the TextView for the greeting message
        val greetingTextView = findViewById<TextView>(R.id.greetingText)

        // Get the user's name from SharedPreferences
        val sharedPreferences: SharedPreferences = getSharedPreferences("HabbitAppPrefs", MODE_PRIVATE)
        val userName = sharedPreferences.getString("userName", "User") ?: "User"

        // Set the greeting text dynamically
        greetingTextView.text = "Welcome $userName!"

        // Set up the button to navigate to ScreenTimeActivity
        val screenTimeButton = findViewById<Button>(R.id.buttonScreenTime)
        screenTimeButton.setOnClickListener {
            val intent = Intent(this, ScreenTimeActivity::class.java)
            startActivity(intent)
        }

        // Set up the button to navigate to WaterIntakeActivity
        val waterIntakeButton = findViewById<Button>(R.id.buttonWaterIntake)
        waterIntakeButton.setOnClickListener {
            val intent = Intent(this, WaterIntakeActivity::class.java)
            startActivity(intent)
        }

        // Set up the button to navigate to HealthyDietActivity
        val healthyDietButton = findViewById<Button>(R.id.buttonHealthyDiet)
        healthyDietButton.setOnClickListener {
            val intent = Intent(this, HealthyDietActivity::class.java)
            startActivity(intent)
        }

        // Set up the button to navigate to WorkoutActivity
        val workoutButton = findViewById<Button>(R.id.buttonWorkout)
        workoutButton.setOnClickListener {
            val intent = Intent(this, WorkoutActivity::class.java)
            startActivity(intent)
        }
    }
}

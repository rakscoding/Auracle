package com.example.habbitapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

data class HydrationTip(
    val tip: String,
    val alternatives: List<String>
)

class WaterIntakeActivity : AppCompatActivity() {

    private var waterIntake = 0.0
    private var waterGoal = 3.0 // Default goal 3 liters

    private lateinit var tvDailyWaterRequirement: TextView
    private lateinit var tvCurrentIntake: TextView
    private lateinit var etWaterIntake: EditText
    private lateinit var etWaterGoal: EditText
    private lateinit var btnSubmitWater: Button
    private lateinit var btnSetGoal: Button
    private lateinit var tvRecommendation: TextView
    private lateinit var tvHydrationTip: TextView // TextView for hydration tip

    private val db = Firebase.firestore
    private val TAG = "WaterIntakeActivity"

    // Hydration tips data
    private var currentTipIndex = 0
    private val hydrationTips = listOf(
        HydrationTip(
            "Start your day with a glass of water! Drinking water first thing in the morning helps kickstart your metabolism.",
            listOf("Warm lemon water", "Coconut water", "Herbal tea")
        ),
        HydrationTip(
            "Keep a reusable water bottle with you throughout the day. Visual cues help build better hydration habits.",
            listOf("Infused water with fruits", "Green tea", "Sparkling water")
        ),
        HydrationTip(
            "Drink a glass of water before each meal. This helps with digestion and prevents overeating.",
            listOf("Vegetable juice", "Buttermilk", "Coconut water")
        ),
        HydrationTip(
            "Set reminders on your phone to drink water every 2 hours during the day.",
            listOf("Smoothies", "Herbal iced tea", "Fresh fruit juice")
        ),
        HydrationTip(
            "Eat water-rich fruits and vegetables like cucumber, watermelon, and oranges to boost hydration.",
            listOf("Cucumber juice", "Watermelon juice", "Orange-infused water")
        ),
        HydrationTip(
            "Have a glass of water after every bathroom break to maintain hydration levels.",
            listOf("Mint-infused water", "Green coconut water", "Chamomile tea")
        ),
        HydrationTip(
            "Track your urine color - pale yellow indicates good hydration, darker means drink more water.",
            listOf("Aloe vera juice", "Rose water", "Lime water")
        ),
        HydrationTip(
            "Drink extra water during and after exercise to replace lost fluids through sweat.",
            listOf("Sports drinks", "Electrolyte water", "Coconut water")
        )
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_water_intake)

        // Initialize views
        initializeViews()

        // Set initial texts
//        updateWaterRequirementText()
        updateCurrentIntakeText()
        generateWaterIntakeRecommendation()
        showRandomTip() // Show initial tip

        // Set click listeners
        setupClickListeners()
    }
    private fun initializeViews() {
        tvDailyWaterRequirement = findViewById(R.id.tvDailyWaterRequirement)
        tvCurrentIntake = findViewById(R.id.tvCurrentIntake)
        etWaterIntake = findViewById(R.id.etWaterIntake)
        etWaterGoal = findViewById(R.id.etWaterGoal)
        btnSubmitWater = findViewById(R.id.btnSubmitWater)
        btnSetGoal = findViewById(R.id.btnSetGoal)
        tvRecommendation = findViewById(R.id.tvRecommendation)
        tvHydrationTip = findViewById(R.id.tvHydrationTip) // TextView for hydration tip
    }

    private fun updateCurrentIntakeText() {
        tvCurrentIntake.text = getString(
            R.string.current_intake,
            waterIntake
        )
    }
    private fun setupClickListeners() {
        btnSubmitWater.setOnClickListener {
            handleWaterIntakeSubmission()
        }

        btnSetGoal.setOnClickListener {
            handleWaterGoalSubmission()
        }
    }

    private fun handleWaterIntakeSubmission() {
        val input = etWaterIntake.text.toString()
        if (input.isNotEmpty()) {
            try {
                val intakeAmount = input.toDouble()
                if (intakeAmount > 0) {
                    waterIntake += intakeAmount

                    // Log water intake update
                    Log.d(TAG, "Added water intake: $intakeAmount")
                    Log.d(TAG, "Total water intake: $waterIntake")

                    // Update UI
                    updateCurrentIntakeText()
                    generateWaterIntakeRecommendation()

                    // Show toast with update
                    Toast.makeText(
                        this,
                        getString(R.string.water_intake_updated, waterIntake),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Clear input field
                    etWaterIntake.text.clear()

                    // Save data
                    saveWaterData()
                } else {
                    Toast.makeText(this, "Please enter a positive value", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.error_invalid_input), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleWaterGoalSubmission() {
        val input = etWaterGoal.text.toString()
        if (input.isNotEmpty()) {
            try {
                val newGoal = input.toDouble()
                if (newGoal > 0) {
                    waterGoal = newGoal

                    // Log goal update
                    Log.d(TAG, "New water goal set: $waterGoal")

                    // Update UI
//                    updateWaterRequirementText()
                    generateWaterIntakeRecommendation()

                    // Show confirmation
                    Toast.makeText(
                        this,
                        getString(R.string.new_water_goal, waterGoal),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Clear input field
                    etWaterGoal.text.clear()

                    // Save goal
                    saveWaterGoal()
                } else {
                    Toast.makeText(this, "Please enter a positive value", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.error_invalid_input), Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateWaterIntakeRecommendation() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Calculate percentage completion
        val percentageComplete = (waterIntake / waterGoal) * 100
        Log.d(TAG, "Percentage complete: $percentageComplete%")

        val recommendation = when {
            waterIntake == 0.0 -> {
                "Start your day right! You need to drink %.2f liters today."
                    .format(Locale.getDefault(), waterGoal)
            }
            waterIntake >= waterGoal -> {
                "Congratulations! You've met your daily water goal of %.2f liters."
                    .format(Locale.getDefault(), waterGoal)
            }
            else -> {
                val remainingWater = waterGoal - waterIntake
                val hoursLeft = (24 - currentHour).coerceAtLeast(1) // Ensure we don't divide by zero
                val hourlyIntake = remainingWater / hoursLeft

                when {
                    percentageComplete < 25 -> {
                        "You've only reached %.1f%% of your goal. Try to drink %.2f liters per hour to catch up."
                            .format(Locale.getDefault(), percentageComplete, hourlyIntake)
                    }
                    percentageComplete < 50 -> {
                        "You're getting there! %.1f%% complete. Aim for %.2f liters per hour to reach your goal."
                            .format(Locale.getDefault(), percentageComplete, hourlyIntake)
                    }
                    percentageComplete < 75 -> {
                        "Good progress! %.1f%% complete. Keep going with %.2f liters per hour."
                            .format(Locale.getDefault(), percentageComplete, hourlyIntake)
                    }
                    else -> {
                        "Almost there! %.1f%% complete. Just %.2f liters to go!"
                            .format(Locale.getDefault(), percentageComplete, remainingWater)
                    }
                }
            }
        }

        tvRecommendation.text = recommendation
    }

    private fun showRandomTip() {
        currentTipIndex = Random.nextInt(hydrationTips.size)
        updateTipDisplay()
    }

    private fun updateTipDisplay() {
        val currentTip = hydrationTips[currentTipIndex]
        val tipText = StringBuilder()
        tipText.append("💧 Tip: ${currentTip.tip}\n\n")
        tipText.append("🌿 Try these alternatives:\n")
        currentTip.alternatives.forEach { alternative ->
            tipText.append("• $alternative\n")
        }

        tvHydrationTip.text = tipText.toString()
    }

    private fun saveWaterData() {
        val userId = "user123" // Replace with actual user ID
        val data = hashMapOf(
            "waterIntake" to waterIntake,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("waterIntake")
            .document(userId)
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "Water intake data saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving water intake data: ${e.message}")
                Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveWaterGoal() {
        val userId = "user123" // Replace with actual user ID
        val data = hashMapOf(
            "waterGoal" to waterGoal,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("waterGoals")
            .document(userId)
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "Water goal saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving water goal: ${e.message}")
                Toast.makeText(this, "Error saving goal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

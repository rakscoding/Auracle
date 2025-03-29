package com.example.habbitapp

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log

class ScreenTimeActivity : AppCompatActivity() {

    private lateinit var tflite: Interpreter
    private lateinit var scaler: JSONObject
    private var totalScreenTime: Long = 0
    private var isGoalSet = false
    private var screenTimeGoalUser: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_time)

        val screenTimeTextView: TextView = findViewById(R.id.screen_time_text)
        val appUsageContainer: LinearLayout = findViewById(R.id.app_usage_container)
        val recommendationTextView: TextView = findViewById(R.id.recommendation_text)
        val goalInput: EditText = findViewById(R.id.goal_input)
        val saveGoalButton: Button = findViewById(R.id.save_goal_button)

        loadModelAndScaler()

        if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            Toast.makeText(
                this,
                "Please enable Usage Access permission for screen time tracking.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(intent)
        }

        fetchAppUsageData { appUsageData ->
            totalScreenTime = appUsageData.sumOf { it.usageMinutes.toLong() }

            val hours = totalScreenTime / 60
            val minutes = totalScreenTime % 60

            screenTimeTextView.text = if (hours > 0) {
                getString(R.string.total_screen_time, "$hours hr $minutes min")
            } else {
                getString(R.string.total_screen_time, "$minutes min")
            }

            if (!isGoalSet) {
                // Check if screen time exceeds 3.5 hours (210 minutes)
                if (totalScreenTime > 210) {
                    recommendationTextView.text = "Recommendation: Reduce Screen Time"
                    recommendationTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                } else {
                    val prediction = predictScreenTime(totalScreenTime.toFloat())
                    recommendationTextView.text = when (prediction) {
                        0 -> "Recommendation: Reduce Screen Time"
                        1 -> "Screen Time is Healthy"
                        else -> "Error during prediction"
                    }
                    recommendationTextView.setTextColor(
                        if (prediction == 1) resources.getColor(android.R.color.holo_green_dark, theme)
                        else resources.getColor(android.R.color.holo_red_dark, theme)
                    )
                }
                recommendationTextView.visibility = TextView.VISIBLE
            }

            appUsageContainer.removeAllViews()
            if (appUsageData.isEmpty()) {
                val placeholderText = TextView(this).apply {
                    text = "No app usage data available."
                    textSize = 16f
                    setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                }
                appUsageContainer.addView(placeholderText)
            } else {
                for (app in appUsageData) {
                    val appUsageTextView = TextView(this).apply {
                        text = "${getAppName(app.appName)}: ${app.usageMinutes} minutes"
                        textSize = 16f
                    }
                    appUsageContainer.addView(appUsageTextView)
                }
            }
        }

        saveGoalButton.setOnClickListener {
            val goalInputText = goalInput.text.toString()
            val timeParts = goalInputText.split(":").map { it.trim() }
            if (timeParts.size != 2) {
                Toast.makeText(this, "Enter the goal in the correct format (e.g., hh:mm).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val goalHours = timeParts[0].toIntOrNull() ?: -1
            val goalMinutes = timeParts[1].toIntOrNull() ?: -1
            if (goalHours < 0 || goalMinutes < 0 || goalMinutes >= 60) {
                Toast.makeText(this, "Enter a valid goal in hh:mm format.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            screenTimeGoalUser = (goalHours * 60 + goalMinutes).toFloat()
            isGoalSet = true

            // Update recommendation based on goal
            val prediction = predictWithGoal(totalScreenTime.toFloat(), screenTimeGoalUser)
            recommendationTextView.text = when (prediction) {
                0 -> "Recommendation: Reduce Screen Time"
                1 -> "Screen Time is Healthy"
                else -> "Error during prediction"
            }
            recommendationTextView.setTextColor(
                if (prediction == 1) resources.getColor(android.R.color.holo_green_dark, theme)
                else resources.getColor(android.R.color.holo_red_dark, theme)
            )
            recommendationTextView.visibility = TextView.VISIBLE
        }
    }

    private fun loadModelAndScaler() {
        try {
            val modelFile = assets.open("screen_time_tf_model.tflite").readBytes()
            val modelBuffer = ByteBuffer.allocateDirect(modelFile.size).apply {
                order(ByteOrder.nativeOrder())
                put(modelFile)
            }
            tflite = Interpreter(modelBuffer)

            val scalerFile =
                assets.open("scalerscreen.json").bufferedReader().use(BufferedReader::readText)
            scaler = JSONObject(scalerFile)
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading model/scaler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchAppUsageData(callback: (List<AppUsageData>) -> Unit) {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 24 * 60 * 60 * 1000,
            currentTime
        )
        val appUsageData = stats.filter { it.totalTimeInForeground > 0 }
            .map {
                val usageMinutes = (it.totalTimeInForeground / (1000 * 60)).toInt()
                Log.d("ScreenTimeActivity", "App: ${it.packageName}, Usage: $usageMinutes minutes")
                AppUsageData(appName = it.packageName, usageMinutes = usageMinutes)
            }
            .filter { it.usageMinutes > 0 } // Final filter to ensure no 0 minutes

        callback(appUsageData)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        }
    }

    private fun predictScreenTime(screenTime: Float): Int {
        return try {
            val timeOfDayEncoded = 2f // Example encoded value
            val appTypeEncoded = 1f // Example encoded value
            val screenTimeHours = screenTime / 60.0f // Derived feature

            // Raw inputs - Only based on screen time
            val rawInputs = floatArrayOf(
                screenTime,
                timeOfDayEncoded,
                appTypeEncoded,
                0f, // No goal
                screenTimeHours
            )
            // Scale inputs
            scaleInputs(rawInputs)

            // Prepare input buffer
            val inputBuffer = ByteBuffer.allocateDirect(4 * rawInputs.size).apply {
                order(ByteOrder.nativeOrder())
                for (value in rawInputs) putFloat(value)
            }
            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(4 * 2).apply {
                order(ByteOrder.nativeOrder())
            }

            // Run inference
            tflite.run(inputBuffer, outputBuffer)

            // Get the result
            outputBuffer.rewind()
            val outputArray = FloatArray(2)
            outputBuffer.asFloatBuffer().get(outputArray)

            // Return the predicted class
            outputArray.toList().indexOf(outputArray.maxOrNull() ?: -1f)
        } catch (e: Exception) {
            e.printStackTrace()
            -1 // Return -1 for error
        }
    }

    private fun predictWithGoal(screenTime: Float, goal: Float): Int {
        return try {
            val timeOfDayEncoded = 2f // Example encoded value
            val appTypeEncoded = 1f // Example encoded value
            val screenTimeHours = screenTime / 60.0f // Derived feature

            // Raw inputs - Based on screen time and goal
            val rawInputs = floatArrayOf(
                screenTime,
                timeOfDayEncoded,
                appTypeEncoded,
                goal,
                screenTimeHours
            )
            // Scale inputs
            scaleInputs(rawInputs)
            // Prepare input buffer
            val inputBuffer = ByteBuffer.allocateDirect(4 * rawInputs.size).apply {
                order(ByteOrder.nativeOrder())
                for (value in rawInputs) putFloat(value)
            }
            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(4 * 2).apply {
                order(ByteOrder.nativeOrder())
            }
            // Run inference
            tflite.run(inputBuffer, outputBuffer)
            // Get the result
            outputBuffer.rewind()
            val outputArray = FloatArray(2)
            outputBuffer.asFloatBuffer().get(outputArray)
            // Return the predicted class
            outputArray.toList().indexOf(outputArray.maxOrNull() ?: -1f)
        } catch (e: Exception) {
            e.printStackTrace()
            -1 // Return -1 for error
        }
    }

    private fun scaleInputs(inputs: FloatArray) {
        val minVals = scaler.getJSONArray("min")
        val scaleVals = scaler.getJSONArray("scale")
        for (i in inputs.indices) {
            inputs[i] =
                (inputs[i] - minVals.getDouble(i).toFloat()) / scaleVals.getDouble(i).toFloat()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    data class AppUsageData(val appName: String, val usageMinutes: Int)
}

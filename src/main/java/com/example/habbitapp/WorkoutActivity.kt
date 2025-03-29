package com.example.habbitapp

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class WorkoutActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var tfliteInterpreter: Interpreter
    private lateinit var sensorManager: SensorManager
    private var stepCount: Int = 0
    private val db = FirebaseFirestore.getInstance()
    private val activityDetailsMap = mutableMapOf<String, Map<String, Any>>()
    private val userPreferences = mutableListOf<String>()

    companion object {
        const val REQUEST_CODE_ACTIVITY_RECOGNITION = 1001
        const val CALORIES_PER_STEP = 0.04 // Example: 1 step = 0.04 calories
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout)

        val stepsTextView = findViewById<TextView>(R.id.tvSteps)
        val stepCaloriesTextView = findViewById<TextView>(R.id.tvStepCalories)
        val activityTypeInput = findViewById<AutoCompleteTextView>(R.id.etActivityType)
        val durationEditText = findViewById<EditText>(R.id.etDuration)
        val calculateButton = findViewById<Button>(R.id.btnCalculate)
        val activityCaloriesTextView = findViewById<TextView>(R.id.tvActivityCalories)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val uploadStatus = findViewById<TextView>(R.id.tvUploadStatus)
        val activitySuggestionTextView = findViewById<TextView>(R.id.tvActivitySuggestion)

        activityCaloriesTextView.visibility = View.GONE
        progressBar.visibility = ProgressBar.VISIBLE
        uploadStatus.visibility = TextView.VISIBLE

        uploadDatasetToFirebase {
            progressBar.visibility = ProgressBar.GONE
            uploadStatus.visibility = TextView.GONE

            fetchActivities {
                val activityNames = activityDetailsMap.keys.toList()
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, activityNames)
                activityTypeInput.setAdapter(adapter)
            }
        }

        if (!loadTFLiteModel()) {
            Toast.makeText(this, "Error loading the model", Toast.LENGTH_LONG).show()
            return
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                REQUEST_CODE_ACTIVITY_RECOGNITION
            )
        } else {
            registerStepCounter(stepsTextView, stepCaloriesTextView)
        }
        calculateButton.setOnClickListener {
            val activityType = activityTypeInput.text.toString().trim()
            val durationInput = durationEditText.text.toString().trim()

            if (activityType.isEmpty() || durationInput.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val duration = durationInput.toFloatOrNull()
            if (duration == null || duration <= 0) {
                Toast.makeText(this, "Invalid duration value", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val predictedCalories = predictCalories(activityType, duration)
            if (predictedCalories == null) {
                Toast.makeText(this, "Invalid activity type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            activityCaloriesTextView.visibility = View.VISIBLE
            activityCaloriesTextView.text = getString(R.string.calories_burned_from_activity, predictedCalories)

            userPreferences.add(activityType)

            // Show the suggestions text view and populate it
            activitySuggestionTextView.visibility = View.VISIBLE
            suggestActivity(activitySuggestionTextView)
        }

        suggestActivity(activitySuggestionTextView)
    }

    private fun loadTFLiteModel(): Boolean {
        return try {
            val modelFile = assets.open("calories_burnt_tf_model.tflite").readBytes()
            val byteBuffer = ByteBuffer.allocateDirect(modelFile.size).apply {
                order(ByteOrder.nativeOrder())
                put(modelFile)
            }
            tfliteInterpreter = Interpreter(byteBuffer)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun registerStepCounter(stepsTextView: TextView, stepCaloriesTextView: TextView) {
        try {
            val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepSensor != null) {
                sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                stepsTextView.text = "Step Counter Sensor not available"
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing step sensor: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateCaloriesFromSteps(steps: Int): Double {
        return steps * CALORIES_PER_STEP
    }

    private fun predictCalories(activityType: String, duration: Float): Float? {
        val activityDetails = activityDetailsMap[activityType]
        return if (activityDetails == null) {
            null
        } else {
            val caloriesPerMinute = activityDetails["Calories per Minute"] as? Double ?: return null
            (caloriesPerMinute * duration).toFloat()
        }
    }

    private fun uploadDatasetToFirebase(onComplete: () -> Unit) {
        val jsonFile = assets.open("Workout2.json")
        val jsonString = jsonFile.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)

        val collectionRef = db.collection("activities")
        val batch = db.batch()

        for (i in 0 until jsonArray.length()) {
            val activityObject = jsonArray.getJSONObject(i)
            val activityData = hashMapOf(
                "Activity Name" to activityObject.getString("Activity Name"),
                "Duration (minutes)" to activityObject.getInt("Duration (minutes)"),
                "Calories Burnt" to activityObject.getInt("Calories Burnt"),
                "Time of Day" to activityObject.getString("Time of Day"),
                "Activity Type" to activityObject.getString("Activity Type"),
                "Calories per Minute" to activityObject.getDouble("Calories per Minute"),
                "Intensity" to activityObject.getString("Intensity")
            )
            val newDoc = collectionRef.document()
            batch.set(newDoc, activityData)
        }

        batch.commit()
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to upload dataset: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchActivities(onComplete: () -> Unit) {
        db.collection("activities")
            .get()
            .addOnSuccessListener { querySnapshot ->
                querySnapshot.documents.forEach { document ->
                    val activityName = document.getString("Activity Name")
                    if (activityName != null) {
                        activityDetailsMap[activityName] = document.data ?: emptyMap()
                    }
                }
                onComplete()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch activities: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun suggestActivity(suggestionTextView: TextView) {
        val availableActivities = activityDetailsMap.keys.toList()
        val excludedActivities = userPreferences

        // Filter out activities that have already been suggested
        val remainingActivities = availableActivities.filter { !excludedActivities.contains(it) }

        if (remainingActivities.isNotEmpty()) {
            val randomActivity = remainingActivities[Random.nextInt(remainingActivities.size)]
            suggestionTextView.text = getString(R.string.suggested_activity, randomActivity)
        } else {
            suggestionTextView.text = getString(R.string.no_more_suggestions)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            stepCount = event.values[0].toInt()
            findViewById<TextView>(R.id.tvSteps).text = getString(R.string.steps_walked, stepCount)

            val caloriesFromSteps = calculateCaloriesFromSteps(stepCount)
            findViewById<TextView>(R.id.tvStepCalories).text = getString(R.string.calories_burned_from_steps, caloriesFromSteps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}

package com.example.habbitapp

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONException
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HealthyDietActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var selectedMeal: String
    private val dailyMeals = mutableMapOf<String, MutableList<Map<String, Any>>>()
    private var totalCalories = 0.0
    private val dailyCaloriesCollection = "dailyCalories"
    private lateinit var tflite: Interpreter
    private lateinit var scaler: JSONObject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_healthy_diet)

        val tvHeading = findViewById<TextView>(R.id.tvHeading)
        val btnBreakfast = findViewById<Button>(R.id.btnBreakfast)
        val btnLunch = findViewById<Button>(R.id.btnLunch)
        val btnSnacks = findViewById<Button>(R.id.btnSnacks)
        val btnDinner = findViewById<Button>(R.id.btnDinner)
        val dropdownSuggestions = findViewById<AutoCompleteTextView>(R.id.dropdownSuggestions)
        val tvMealDetails = findViewById<TextView>(R.id.tvMealDetails)
        val tvTotalCalories = findViewById<TextView>(R.id.tvTotalCalories)

        tvHeading.text = "Eat Healthy, Stay Healthy"

        // Load TensorFlow Lite model and scaler metadata
        try {
            loadTFLiteModel()
            loadScalerMetadata()
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing model: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        dropdownSuggestions.visibility = View.GONE

        val mealButtons = listOf(btnBreakfast, btnLunch, btnSnacks, btnDinner)
        mealButtons.forEach { button ->
            button.setOnClickListener {
                selectedMeal = button.text.toString()
                dropdownSuggestions.visibility = View.VISIBLE
                dropdownSuggestions.setText("")
                dropdownSuggestions.hint = "Enter $selectedMeal meal"
            }
        }

        fetchFoodSuggestions { foodItems ->
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, foodItems)
            dropdownSuggestions.setAdapter(adapter)

            dropdownSuggestions.setOnItemClickListener { _, _, position, _ ->
                val selectedFood = adapter.getItem(position) ?: return@setOnItemClickListener
                storeMealData(selectedFood) { mealData ->
                    updateMealDetails(tvMealDetails, mealData)
                    updateTotalCalories(tvTotalCalories, mealData["Calories"] as Number)

                    val prediction = predictDietSuitability(mealData)
                    Toast.makeText(this, "Prediction: $prediction", Toast.LENGTH_SHORT).show()
                }
                dropdownSuggestions.setText("")
            }
        }
    }

    private fun loadTFLiteModel() {
        val modelBuffer = assets.open("healthy_diet_model.tflite").use { inputStream ->
            val modelBytes = ByteArray(inputStream.available())
            inputStream.read(modelBytes)
            ByteBuffer.allocateDirect(modelBytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(modelBytes)
            }
        }
        tflite = Interpreter(modelBuffer)
    }

    private fun loadScalerMetadata() {
        val jsonString = assets.open("scaler(diet).json").bufferedReader().use { it.readText() }
        scaler = JSONObject(jsonString)
    }

    private fun preprocessData(foodData: Map<String, Any>): FloatArray {
        val minValues = scaler.optJSONArray("min") ?: throw JSONException("Missing 'min' key in scaler.json")
        val rangeValues = scaler.optJSONArray("range") ?: throw JSONException("Missing 'range' key in scaler.json")

        val calories = foodData["Calories"].toString().toDouble()
        val servingSize = foodData["Serving Size"].toString().toDouble()
        val caloriesPerServing = calories / servingSize

        val normalizedCalories = ((calories - minValues.getDouble(0)) / rangeValues.getDouble(0)).toFloat()
        val normalizedServingSize = ((servingSize - minValues.getDouble(1)) / rangeValues.getDouble(1)).toFloat()
        val normalizedCaloriesPerServing =
            ((caloriesPerServing - minValues.getDouble(2)) / rangeValues.getDouble(2)).toFloat()

        return floatArrayOf(normalizedCalories, normalizedServingSize, normalizedCaloriesPerServing)
    }

    private fun predictDietSuitability(foodData: Map<String, Any>): String {
        val input = preprocessData(foodData)

        val inputBuffer = ByteBuffer.allocateDirect(4 * input.size).apply {
            order(ByteOrder.nativeOrder())
            input.forEach { putFloat(it) }
        }

        val output = Array(1) { FloatArray(4) } // Update size to match model's output
        tflite.run(inputBuffer, output)

        val predictedIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        return when (predictedIndex) {
            0 -> "Healthy"
            1 -> "Moderate"
            2 -> "Unhealthy"
            else -> "Unknown"
        }
    }

    private fun fetchFoodSuggestions(callback: (List<String>) -> Unit) {
        db.collection("foodItems")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val foodItems = querySnapshot.documents.mapNotNull { it.getString("Food Name") }
                callback(foodItems)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching food items: ${e.message}", Toast.LENGTH_SHORT).show()
                callback(emptyList())
            }
    }

    private fun storeMealData(foodName: String, callback: (Map<String, Any>) -> Unit) {
        db.collection("foodItems")
            .whereEqualTo("Food Name", foodName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val foodItem = querySnapshot.documents.firstOrNull()?.data ?: return@addOnSuccessListener
                val mealData = mapOf(
                    "Meal" to selectedMeal,
                    "Food Name" to foodItem["Food Name"]!!,
                    "Serving Size" to foodItem["Serving Size"]!!,
                    "Calories" to foodItem["Calories"]!!
                )
                db.collection(dailyCaloriesCollection)
                    .add(mealData)
                    .addOnSuccessListener {
                        dailyMeals.getOrPut(selectedMeal) { mutableListOf() }.add(mealData)
                        callback(mealData)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error saving meal data: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun updateMealDetails(tvMealDetails: TextView, mealData: Map<String, Any>) {
        val mealDetails = dailyMeals.entries.joinToString("\n") { entry ->
            val mealName = entry.key
            val foodDetails = entry.value.joinToString("\n") { meal ->
                "${meal["Food Name"]} - ${meal["Serving Size"]} serving(s) - ${meal["Calories"]} calories"
            }
            "$mealName:\n$foodDetails"
        }
        tvMealDetails.text = mealDetails
    }

    private fun updateTotalCalories(tvTotalCalories: TextView, mealCalories: Number) {
        totalCalories += mealCalories.toDouble()
        tvTotalCalories.text = "Total Calories for the Day: $totalCalories"
    }
}

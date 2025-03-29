package com.example.habbitapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private lateinit var progressBar: ProgressBar

    // Activity Result Launcher for Google Sign-In
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            progressBar.visibility = View.GONE
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            } else {
                Toast.makeText(this, "Google Sign-In canceled", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("HabbitAppPrefs", MODE_PRIVATE)

        // Initialize ProgressBar
        progressBar = findViewById(R.id.progressBar)
        progressBar.visibility = View.GONE

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Register button logic for app registration
        val registerButton = findViewById<Button>(R.id.buttonRegister)
        registerButton.setOnClickListener {
            val nameInput = findViewById<EditText>(R.id.editTextName).text.toString().trim()
            val emailInput = findViewById<EditText>(R.id.editTextEmail).text.toString().trim()
            val passwordInput = findViewById<EditText>(R.id.editTextPassword).text.toString().trim()

            if (validateInputs(nameInput, emailInput, passwordInput)) {
                progressBar.visibility = View.VISIBLE
                firebaseAuth.createUserWithEmailAndPassword(emailInput, passwordInput)
                    .addOnCompleteListener(this) { task ->
                        progressBar.visibility = View.GONE
                        if (task.isSuccessful) {
                            val user = firebaseAuth.currentUser
                            user?.let {
                                saveUserToDatabase(it.uid, nameInput, emailInput, "")
                            }

                            // Save user name in SharedPreferences
                            saveUserToSharedPreferences(nameInput)

                            // Navigate to MainActivity
                            val intent = Intent(this, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            handleFirebaseError(task.exception)
                        }
                    }
            }
        }

        // Google Sign-In button logic
        val googleSignInButton = findViewById<Button>(R.id.customGoogleSignInButton)
        googleSignInButton.setOnClickListener { signInWithGoogle() }
    }

    private fun validateInputs(name: String, email: String, password: String): Boolean {
        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return false
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.isEmpty() || password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun handleFirebaseError(exception: Exception?) {
        exception?.let { e ->
            when (e) {
                is FirebaseAuthUserCollisionException -> Toast.makeText(this, "Email already in use", Toast.LENGTH_SHORT).show()
                is FirebaseAuthWeakPasswordException -> Toast.makeText(this, "Weak password: ${e.reason}", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "Registration error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserToDatabase(userId: String, name: String, email: String, profilePictureUrl: String) {
        val user = hashMapOf(
            "name" to name,
            "email" to email,
            "profilePictureUrl" to profilePictureUrl
        )
        firestore.collection("users").document(userId).set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "User data saved successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun saveUserToSharedPreferences(name: String) {
        val editor = sharedPreferences.edit()
        editor.putString("userName", name)
        editor.apply()
    }
    private fun signInWithGoogle() {
        progressBar.visibility = View.VISIBLE
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(Exception::class.java)
            if (account != null) {
                firebaseAuthWithGoogle(account)
            } else {
                Toast.makeText(this, "Google Sign-In failed: Account is null", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    user?.let {
                        saveUserToDatabase(it.uid, it.displayName ?: "Unknown", it.email ?: "No Email", it.photoUrl.toString())
                    }

                    // Save user name in SharedPreferences
                    saveUserToSharedPreferences(account.displayName ?: "User")

                    // Navigate to MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}

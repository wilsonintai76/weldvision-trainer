package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: WeldVisionUiViewModel,
    onNavigateToRegister: () -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val activity = context as? FragmentActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("WELDVISION", color = Color.Cyan, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("T R A I N E R", color = Color.Gray, fontSize = 14.sp, letterSpacing = 8.sp)
        
        Spacer(modifier = Modifier.height(48.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = Color.Red, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Email, "Email", tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password", color = Color.Gray) },
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, "Password", tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter email and password"
                    return@Button
                }
                isLoading = true
                viewModel.loginUser(
                    email = email.trim(),
                    passwordHash = password, // Ideally hash this!
                    onSuccess = { isLoading = false },
                    onError = { err -> isLoading = false; errorMessage = err }
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
            } else {
                Text("LOGIN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (BiometricHelper.isBiometricAvailable(context)) {
            Button(
                onClick = {
                    activity?.let {
                        BiometricHelper.promptBiometricAuth(
                            activity = it,
                            onSuccess = {
                                viewModel.loginBiometric(
                                    onSuccess = {},
                                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                                )
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Fingerprint, "Biometric", modifier = Modifier.padding(end = 8.dp))
                Text("LOGIN WITH BIOMETRIC / FACE", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row {
            Text("Don't have an account? ", color = Color.Gray)
            Text(
                "Register",
                color = Color.Cyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onNavigateToRegister() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: WeldVisionUiViewModel,
    onNavigateToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var matricNo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CREATE ACCOUNT", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(32.dp))

        if (errorMessage != null) {
            Text(errorMessage!!, color = Color.Red, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; errorMessage = null },
            label = { Text("Full Name", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Person, "Name", tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = matricNo,
            onValueChange = { matricNo = it; errorMessage = null },
            label = { Text("Matric No.", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Person, "Matric No", tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Email, "Email", tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password", color = Color.Gray) },
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, "Password", tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (name.isBlank() || email.isBlank() || matricNo.isBlank() || password.isBlank()) {
                    errorMessage = "All fields are required"
                    return@Button
                }
                isLoading = true
                viewModel.registerUser(
                    email = email.trim(),
                    name = name.trim(),
                    matricNo = matricNo.trim(),
                    passwordHash = password, // Ideally hash this!
                    onSuccess = { isLoading = false },
                    onError = { err -> isLoading = false; errorMessage = err }
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
            } else {
                Text("REGISTER", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            Text("Already have an account? ", color = Color.Gray)
            Text(
                "Login",
                color = Color.Cyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onNavigateToLogin() }
            )
        }
    }
}

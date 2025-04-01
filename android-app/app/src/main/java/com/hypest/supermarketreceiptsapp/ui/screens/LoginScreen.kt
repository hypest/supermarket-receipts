package com.hypest.supermarketreceiptsapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.* // Import TextField, OutlinedTextField
import androidx.compose.runtime.* // Import remember, mutableStateOf, collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel // Re-add hiltViewModel
import androidx.navigation.NavController
import com.hypest.supermarketreceiptsapp.navigation.Screen
import com.hypest.supermarketreceiptsapp.viewmodel.AuthViewModel // Re-add AuthViewModel
import io.github.jan.supabase.auth.status.SessionStatus // Add SessionStatus import

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel() // Re-inject ViewModel
) {
    val sessionStatus by viewModel.sessionStatus.collectAsState() // Re-add session observation
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Re-add navigation based on auth state
    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.Authenticated) {
            navController.navigate(Screen.Main.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Login Screen")
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // TODO: Add loading indicator based on ViewModel state
            // TODO: Display error messages based on ViewModel state

            Button(
                onClick = { viewModel.signInWithEmail(email, password) }, // Call ViewModel
                enabled = email.isNotBlank() && password.isNotBlank()
            ) {
                Text("Login with Email")
            }
            // TODO: Add loading/error UI based on ViewModel state
        }
    }
}

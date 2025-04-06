package com.hypest.supermarketreceiptsapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.* // Import Box, fillMaxWidth, etc.
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource // For image resources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // For font size
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.hypest.supermarketreceiptsapp.R // Uncomment R class import
import com.hypest.supermarketreceiptsapp.navigation.Screen
import com.hypest.supermarketreceiptsapp.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel() // Re-inject ViewModel
) {
    // Observe session status (e.g., for showing errors from ViewModel, not for navigation)
    val sessionStatus by viewModel.sessionStatus.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Navigation is now handled by NavGraph based on initial auth state

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top, // Align content to the top
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp)) // Space from top

            // Use the actual Image composable with the created drawable
            Image(
                painter = painterResource(id = R.drawable.ic_receipt_logo), // Use the new logo
                contentDescription = "App Logo",
                modifier = Modifier.size(80.dp) // Adjust size as needed
            )
            // Box(modifier = Modifier.height(80.dp)) // Remove Placeholder box
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Receipt Tracker",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Track your household expenses",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant // Use a slightly muted color
            )
            Spacer(modifier = Modifier.height(40.dp)) // Space before form fields

            // Email Field
            Text(
                text = "Email",
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp), // Align label to start
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Enter your email") }, // Use placeholder
                singleLine = true,
                modifier = Modifier.fillMaxWidth() // Make field wide
            )
            Spacer(modifier = Modifier.height(16.dp)) // Space between fields

            // Password Field
            Text(
                text = "Password",
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp), // Align label to start
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Enter your password") }, // Use placeholder
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth() // Make field wide
            )
            Spacer(modifier = Modifier.height(32.dp)) // Space before button

            // TODO: Add loading indicator based on ViewModel state
            // TODO: Display error messages based on ViewModel state

            Button(
                onClick = { viewModel.signInWithEmail(email, password) },
                enabled = email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth() // Make button wide
                    .height(50.dp) // Set button height
            ) {
                Text("Sign In", fontSize = 16.sp) // Update button text
            }
            // TODO: Add loading/error UI based on ViewModel state
        }
    }
}

// Optional: Add a Preview function if you don't have one
// @Preview(showBackground = true)
// @Composable
// fun LoginScreenPreview() {
//     SupermarketReceiptsAppTheme { // Replace with your actual theme
//         LoginScreen(navController = rememberNavController()) // Use a dummy NavController
//     }
// }

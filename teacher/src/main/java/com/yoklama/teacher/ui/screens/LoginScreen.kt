package com.yoklama.teacher.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoklama.teacher.R
import com.yoklama.teacher.viewmodel.TeacherViewModel

@Composable
fun LoginScreen(viewModel: TeacherViewModel, onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }

    val loginResult  by viewModel.loginResult.observeAsState()
    val errorMessage by viewModel.errorMessage.observeAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            loginResult?.success == false -> Color(0xFFCF6679)
            loginResult?.success == true  -> Color(0xFF4CAF50)
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(300), label = "border"
    )

    LaunchedEffect(loginResult) {
        if (loginResult?.success == true) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.au_logo),
            contentDescription = null,
            modifier = Modifier.size(130.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text("Öğretmen Girişi", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username, onValueChange = { username = it; viewModel.clearLogin() },
            label = { Text("Öğretmen ID (T001 gibi)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor, unfocusedBorderColor = borderColor
            )
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; viewModel.clearLogin() },
            label = { Text("Şifre") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor, unfocusedBorderColor = borderColor
            )
        )
        Spacer(Modifier.height(12.dp))

        if (loginResult?.success == false) {
            Text("Giriş başarısız!", color = Color(0xFFCF6679), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.clearLogin(); viewModel.login(username, password) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Giriş Yap", fontSize = 16.sp) }
    }
}

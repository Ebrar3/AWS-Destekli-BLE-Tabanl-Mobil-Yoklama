package com.efecanseymen.b1.ui.screens

import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efecanseymen.b1.R
import com.efecanseymen.b1.viewmodel.HomeViewModel

@Composable
fun LoginScreen(
    onLoginClick: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val loginResult by viewModel.loginResult.observeAsState()
    val errorMessage by viewModel.errorMessage.observeAsState()

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            loginResult?.success == false -> Color(0xFFCF6679)
            loginResult?.success == true -> Color(0xFF4CAF50)
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(300),
        label = "borderColor"
    )

    val animatedLabelColor by animateColorAsState(
        targetValue = when {
            loginResult?.success == false -> Color(0xFFCF6679)
            loginResult?.success == true -> Color(0xFF4CAF50)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "labelColor"
    )

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = animatedBorderColor,
        unfocusedBorderColor = animatedBorderColor,
        focusedLabelColor = animatedLabelColor,
        unfocusedLabelColor = animatedLabelColor
    )

    LaunchedEffect(loginResult) {
        if (loginResult?.success == true) {
            onLoginClick()
        }
        else if(loginResult?.success == false){
            Toast.makeText(context, "Giriş başarısız! Lütfen bilgilerinizi kontrol edin.", Toast.LENGTH_SHORT).show()

        }
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
            modifier = Modifier.size(150.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                if (loginResult?.success == false) viewModel.clearLoginResult()
            },
            label = { Text("Öğrenci Numarası") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (loginResult?.success == false) viewModel.clearLoginResult()
            },
            label = { Text("Şifre") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = textFieldColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (loginResult?.success == false) {
            Text(
                text = "Giriş başarısız! Lütfen bilgilerinizi kontrol edin.",
                color = Color(0xFFCF6679),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.clearLoginResult()
                viewModel.login(username, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Giriş Yap", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { showCreateDialog = true }) {
            Text(
                text = "Hesabın yok mu? Hesap Oluştur",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
        }
    }

    val createUserResult by viewModel.createUserResult.observeAsState()

    LaunchedEffect(createUserResult) {
        if (createUserResult?.success == true) {
            Toast.makeText(context, "Hesap başarıyla oluşturuldu! Giriş yapabilirsiniz.", Toast.LENGTH_LONG).show()
            showCreateDialog = false
            viewModel.clearCreateUserResult()
        } else if (createUserResult?.success == false) {
            Toast.makeText(context, "Hesap oluşturulamadı. Lütfen tekrar deneyin.", Toast.LENGTH_SHORT).show()
            viewModel.clearCreateUserResult()
        }
    }

    if (showCreateDialog) {
        CreateAccountDialog(
            onDismiss = { showCreateDialog = false },
            onCreateAccount = { name, studentNo, email, pass ->
                viewModel.createUser(
                    userName = name,
                    password = pass,
                    role = "student",
                    userId = studentNo,
                    email = email
                )
            }
        )
    }
}

@Composable
fun CreateAccountDialog(
    onDismiss: () -> Unit,
    onCreateAccount: (name: String, studentNo: String, email: String, password: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var studentNo by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Hesap Oluştur",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Bilgilerinizi girerek yeni hesap oluşturun",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Ad Soyad") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = studentNo,
                    onValueChange = { studentNo = it; errorText = null },
                    label = { Text("Öğrenci Numarası") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorText = null },
                    label = { Text("E-posta") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    label = { Text("Şifre") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorText = null },
                    label = { Text("Şifre Tekrar") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                errorText?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = Color(0xFFCF6679),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        when {
                            name.isBlank() || studentNo.isBlank() || email.isBlank() || password.isBlank() ->
                                errorText = "Lütfen tüm alanları doldurun"
                            password != confirmPassword ->
                                errorText = "Şifreler eşleşmiyor"
                            password.length < 6 ->
                                errorText = "Şifre en az 6 karakter olmalı"
                            else ->
                                onCreateAccount(name, studentNo, email, password)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hesap Oluştur", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Vazgeç",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
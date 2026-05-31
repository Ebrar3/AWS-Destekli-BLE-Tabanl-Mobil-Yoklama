package com.yoklama.teacher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoklama.teacher.data.model.CourseItem
import com.yoklama.teacher.viewmodel.TeacherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TeacherViewModel,
    onStartSession: () -> Unit,
    onViewReport: (String) -> Unit,
    onLogout: () -> Unit
) {
    val teacherName     = viewModel.currentTeacherName ?: "Öğretmen"
    val courses         by viewModel.teacherCourses.observeAsState(emptyList())
    val isLoading       by viewModel.isLoadingCourses.observeAsState(false)
    val isStarting      by viewModel.isStartingSession.observeAsState(false)
    val sessionStarted  by viewModel.sessionStarted.observeAsState()
    val errorMessage    by viewModel.errorMessage.observeAsState()

    // Dersler ilk açılışta yüklenir
    LaunchedEffect(Unit) { viewModel.loadTeacherCourses() }

    // Session başarıyla başlatılınca navigate et (race condition düzeltildi)
    LaunchedEffect(sessionStarted) {
        if (sessionStarted?.success == true) onStartSession()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hoşgeldin, $teacherName", fontWeight = FontWeight.Bold)
                        Text("Derslerini seç", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Çıkış")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                courses.isEmpty() -> EmptyCoursesMessage(modifier = Modifier.align(Alignment.Center))
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        item {
                            Text("Derslerim (${courses.size})",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                        }
                        items(courses) { course ->
                            CourseCard(
                                course = course,
                                isStarting = isStarting,
                                onStartSession = {
                                    viewModel.startSession(course.course_code, course.course_name)
                                },
                                onViewReport = { onViewReport(course.course_code) }
                            )
                        }
                    }
                }
            }

            // Hata mesajı
            errorMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                ) { Text(msg) }
            }

            // Yükleniyor overlay
            if (isStarting) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Ders başlatılıyor...", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CourseCard(
    course: CourseItem,
    isStarting: Boolean,
    onStartSession: () -> Unit,
    onViewReport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.School, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(course.course_name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(course.course_code, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartSession,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isStarting
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Yoklama Başlat", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onViewReport,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.BarChart, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Rapor", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyCoursesMessage(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.School, null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(12.dp))
        Text("Henüz ders bulunamadı",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

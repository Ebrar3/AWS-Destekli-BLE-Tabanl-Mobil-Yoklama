package com.yoklama.teacher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
                        Icon(Icons.Filled.Logout, contentDescription = "Çıkış",
                            modifier = Modifier.size(28.dp))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                courses.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyCoursesMessage()
                    errorMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { viewModel.loadTeacherCourses() }) {
                        Text("Tekrar Dene")
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Verilen Dersler",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "Toplam ${courses.size} ders",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
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
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.School, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(course.course_name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(course.course_code, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onStartSession,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isStarting
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Yoklama Başlat", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = onViewReport,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.BarChart, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rapor", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun EmptyCoursesMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Icon(
                Icons.Filled.School, null,
                modifier = Modifier.padding(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Kayıtlı ders bulunamadı",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Üzerinize tanımlanmış bir ders programı görünmüyor.",
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

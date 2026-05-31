package com.yoklama.teacher.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yoklama.teacher.data.model.*
import com.yoklama.teacher.data.repository.TeacherRepository
import com.yoklama.teacher.service.BleAdvertiserService
import kotlinx.coroutines.*

class TeacherViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TeacherRepository()
    private val ctx get() = getApplication<Application>()

    // Auth
    val loginResult   = MutableLiveData<LoginResponse?>()
    val errorMessage  = MutableLiveData<String?>()
    var currentTeacherId: String? = null
    var currentTeacherName: String? = null

    // Kurslar
    val teacherCourses   = MutableLiveData<List<CourseItem>>(emptyList())
    val isLoadingCourses = MutableLiveData(false)

    // Session state
    val sessionStarted    = MutableLiveData<StartSessionResponse?>()
    val checkinTriggered  = MutableLiveData<TriggerCheckinResponse?>()
    val sessionEnded      = MutableLiveData<EndSessionResponse?>()
    val sessionReport     = MutableLiveData<SessionReportResponse?>()

    var currentSessionId: String?   = null
    var currentCheckinId: String?   = null
    var currentCourseCode: String?  = null
    var currentCourseName: String?  = null
    val checkinCount      = MutableLiveData(0)
    val isSessionActive   = MutableLiveData(false)
    val isStartingSession = MutableLiveData(false)

    private var autoCheckinJob: Job? = null

    // ---------- Auth ----------

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                val r = repo.login(username, password)
                if (r.isSuccessful) {
                    val body = r.body()
                    if (body?.success == true && body.role == "teacher") {
                        currentTeacherId  = body.userId
                        currentTeacherName = body.userName
                        loginResult.value = body
                    } else if (body?.role == "student") {
                        errorMessage.value = "Bu uygulama öğretmenler içindir."
                    } else {
                        loginResult.value = body
                    }
                } else {
                    errorMessage.value = "Giriş hatası: ${r.code()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Bağlantı hatası: ${e.message}"
            }
        }
    }

    fun clearLogin() {
        loginResult.value = null
        errorMessage.value = null
    }

    fun logout() {
        stopAutoCheckin(); stopBle()
        loginResult.value = null
        currentTeacherId = null; currentTeacherName = null; currentSessionId = null
        isSessionActive.value = false
        teacherCourses.value = emptyList()
    }

    // ---------- Kurslar ----------

    fun loadTeacherCourses() {
        val tid = currentTeacherId ?: return
        isLoadingCourses.value = true
        viewModelScope.launch {
            try {
                val r = repo.getCoursesByTeacher(tid)
                if (r.isSuccessful) teacherCourses.value = r.body()?.courses ?: emptyList()
                else errorMessage.value = "Dersler yüklenemedi"
            } catch (e: Exception) {
                errorMessage.value = "Bağlantı hatası: ${e.message}"
            } finally {
                isLoadingCourses.value = false
            }
        }
    }

    // ---------- Session ----------

    fun startSession(courseCode: String, courseName: String) {
        val teacherId = currentTeacherId ?: run {
            errorMessage.value = "Oturum hatası, tekrar giriş yapın."
            return
        }
        isStartingSession.value = true
        errorMessage.value = null
        viewModelScope.launch {
            try {
                val r = repo.startSession(teacherId, courseCode)
                if (r.isSuccessful && r.body()?.success == true) {
                    val body = r.body()!!
                    currentSessionId  = body.session_id
                    currentCourseCode = courseCode
                    currentCourseName = courseName
                    isSessionActive.value = true
                    sessionStarted.value  = body   // ← bu tetiklenince navigate olunacak
                    triggerCheckin()               // ilk yoklama
                } else {
                    errorMessage.value = r.body()?.message ?: "Ders başlatılamadı"
                }
            } catch (e: Exception) {
                errorMessage.value = "Bağlantı hatası: ${e.message}"
            } finally {
                isStartingSession.value = false
            }
        }
    }

    fun triggerCheckin() {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            try {
                val r = repo.triggerCheckin(sessionId)
                if (r.isSuccessful && r.body()?.success == true) {
                    val body = r.body()!!
                    currentCheckinId  = body.checkin_id
                    checkinCount.value = body.checkin_number ?: 0
                    checkinTriggered.value = body
                    updateBleCheckin(body.checkin_id ?: "")
                }
            } catch (e: Exception) {
                errorMessage.value = "Yoklama hatası: ${e.message}"
            }
        }
    }

    fun startBle(sessionId: String, checkinId: String) {
        ctx.startForegroundService(Intent(ctx, BleAdvertiserService::class.java).apply {
            putExtra(BleAdvertiserService.EXTRA_SESSION_ID, sessionId)
            putExtra(BleAdvertiserService.EXTRA_CHECKIN_ID, checkinId)
        })
    }

    private fun updateBleCheckin(checkinId: String) {
        ctx.startService(Intent(ctx, BleAdvertiserService::class.java).apply {
            action = BleAdvertiserService.ACTION_UPDATE_CHECKIN
            putExtra(BleAdvertiserService.EXTRA_CHECKIN_ID, checkinId)
        })
    }

    fun stopBle() = ctx.stopService(Intent(ctx, BleAdvertiserService::class.java))

    fun startAutoCheckin() {
        autoCheckinJob = viewModelScope.launch {
            while (isActive) {
                delay(8 * 60 * 1000L)
                if (isSessionActive.value == true) triggerCheckin()
            }
        }
    }

    fun stopAutoCheckin() { autoCheckinJob?.cancel(); autoCheckinJob = null }

    fun endSession() {
        val sessionId = currentSessionId ?: run {
            errorMessage.value = "Aktif ders bulunamadı."
            return
        }
        viewModelScope.launch {
            try {
                val r = repo.endSession(sessionId)
                if (r.isSuccessful) {
                    stopAutoCheckin(); stopBle()
                    isSessionActive.value = false
                    sessionEnded.value = r.body()
                } else {
                    errorMessage.value = "Ders bitirilemedi: ${r.code()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Bağlantı hatası: ${e.message}"
            }
        }
    }

    fun loadReport(courseCode: String) {
        val teacherId = currentTeacherId ?: return
        viewModelScope.launch {
            try {
                val r = repo.getSessionReport(teacherId, courseCode)
                if (r.isSuccessful) sessionReport.value = r.body()
            } catch (e: Exception) {
                errorMessage.value = "Rapor yüklenemedi: ${e.message}"
            }
        }
    }

    fun resetSession() {
        sessionStarted.value = null; sessionEnded.value = null
        currentSessionId = null; currentCourseCode = null; currentCourseName = null
        checkinCount.value = 0
    }
}

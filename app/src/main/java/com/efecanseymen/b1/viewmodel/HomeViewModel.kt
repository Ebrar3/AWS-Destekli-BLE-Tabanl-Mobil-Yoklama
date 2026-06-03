package com.efecanseymen.b1.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.efecanseymen.b1.data.model.*
import com.efecanseymen.b1.data.repository.AttendanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AttendanceRepository()

    val loginResult      = MutableLiveData<LoginResponse?>()
    val errorMessage     = MutableLiveData<String?>()
    val isScanning       = MutableStateFlow(false)
    val courses          = MutableLiveData<List<StudentCourseInfo>>(emptyList())
    val isLoadingCourses = MutableLiveData(false)
    val presenceReported = MutableLiveData<String?>()
    val createUserResult = MutableLiveData<CreateUserResponse?>()
    val nfcTagData       = MutableLiveData<String?>()
    val nfcAvailable     = MutableLiveData(false)   // NFC donanımı var mı?
    val nfcEnabled       = MutableLiveData(false)   // NFC açık mı? (ayarlardan)

    var currentUserId: String?   = null
    var currentUserName: String? = null
    var currentUserRole: String? = null

    private val reportedCheckins = mutableSetOf<String>()

    // ---------- Auth ----------

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                val response = repository.login(username, password)
                if (response.isSuccessful) {
                    val body = response.body()
                    currentUserId   = body?.userId
                    currentUserName = body?.userName
                    currentUserRole = body?.role
                    loginResult.value = body
                } else {
                    errorMessage.value = "Hata: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Bağlantı hatası: ${e.message}"
            }
        }
    }

    fun logout() {
        loginResult.value = null
        currentUserId = null; currentUserName = null; currentUserRole = null
        courses.value = emptyList()
        reportedCheckins.clear()
    }

    fun clearLoginResult() {
        loginResult.value = null
        errorMessage.value = null
    }

    fun createUser(userName: String, password: String, role: String, userId: String?, email: String?) {
        viewModelScope.launch {
            try {
                val result = repository.createUser(userName, password, role, userId, email)
                createUserResult.value = result
            } catch (e: Exception) {
                createUserResult.value = CreateUserResponse(false, e.message)
            }
        }
    }

    fun clearCreateUserResult() { createUserResult.value = null }

    // ---------- Dersler ----------

    fun loadCourses() {
        val uid = currentUserId ?: run {
            errorMessage.value = "Oturum bulunamadı, tekrar giriş yapın"
            return
        }
        isLoadingCourses.value = true
        viewModelScope.launch {
            try {
                val result = repository.getCourses(uid)
                if (result?.success == true) {
                    courses.value = result.courses ?: emptyList()
                    errorMessage.value = null
                } else {
                    errorMessage.value = "Dersler yüklenemedi"
                }
            } catch (e: Exception) {
                errorMessage.value = "Bağlantı hatası: ${e.message}"
            } finally {
                isLoadingCourses.value = false
            }
        }
    }

    // ---------- BLE Presence ----------

    fun reportPresence(sessionId: String, checkinId: String) {
        val uid = currentUserId ?: return
        if (checkinId in reportedCheckins) return
        reportedCheckins.add(checkinId)
        viewModelScope.launch {
            try {
                val result = repository.reportPresence(uid, checkinId, sessionId)
                if (result?.success == true) {
                    presenceReported.value = checkinId
                } else {
                    reportedCheckins.remove(checkinId)
                }
            } catch (e: Exception) {
                reportedCheckins.remove(checkinId)
            }
        }
    }

    fun startScan() { isScanning.value = true }
    fun stopScan()  { isScanning.value = false }

    // ---------- NFC ----------

    fun onNfcTagDetected(data: String) { nfcTagData.value = data }
    fun clearNfcTag()                  { nfcTagData.value = null }
}
package com.yoklama.teacher.data.repository

import com.yoklama.teacher.data.model.*
import com.yoklama.teacher.data.network.RetrofitInstance
import retrofit2.Response

class TeacherRepository {
    private val api = RetrofitInstance.api

    suspend fun login(username: String, password: String): Response<LoginResponse> {
        val hashed = hashSha256(password)
        return api.login(LoginRequest(username, hashed))
    }

    suspend fun startSession(teacherId: String, courseCode: String): Response<StartSessionResponse> =
        api.startSession(StartSessionRequest(teacherId, courseCode))

    suspend fun triggerCheckin(sessionId: String): Response<TriggerCheckinResponse> =
        api.triggerCheckin(TriggerCheckinRequest(sessionId))

    suspend fun endSession(sessionId: String): Response<EndSessionResponse> =
        api.endSession(EndSessionRequest(sessionId))

    suspend fun getSessionReport(teacherId: String, courseCode: String): Response<SessionReportResponse> =
        api.getSessionReport(SessionReportRequest(teacherId, courseCode))

    suspend fun getCoursesByTeacher(teacherId: String): Response<GetCoursesResponse> =
        api.getCourses(GetCoursesRequest(teacher_id = teacherId))

    private fun hashSha256(input: String): String {
        val bytes = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

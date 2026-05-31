package com.yoklama.teacher.data.model

data class LoginRequest(val username: String, val password: String)

data class LoginResponse(
    val statusCode: Int?,
    val body: String?
) {
    val success: Boolean get() = body?.contains("\"success\": true") == true
    val userId: String? get() = body?.substringAfter("\"user_id\": \"")?.substringBefore("\"")
    val userName: String? get() = body?.substringAfter("\"user_name\": \"")?.substringBefore("\"")
    val role: String? get() = body?.substringAfter("\"role\": \"")?.substringBefore("\"")
}

data class StartSessionRequest(val teacher_id: String, val course_code: String)

data class StartSessionResponse(
    val success: Boolean,
    val session_id: String?,
    val message: String?
)

data class TriggerCheckinRequest(val session_id: String)

data class TriggerCheckinResponse(
    val success: Boolean,
    val checkin_id: String?,
    val checkin_number: Int?,
    val message: String?
)

data class EndSessionRequest(val session_id: String)

data class StudentResult(
    val student_id: String,
    val student_name: String,
    val present_count: Int,
    val total_checkins: Int,
    val percentage: Double,
    val status: String
)

data class EndSessionResponse(
    val success: Boolean,
    val session_id: String?,
    val total_checkins: Int?,
    val results: List<StudentResult>?,
    val message: String?
)

data class SessionReportRequest(val teacher_id: String, val course_code: String)

data class StudentReportItem(
    val student_id: String,
    val student_name: String,
    val present_sessions: Int,
    val total_sessions: Int,
    val percentage: Double
)

data class SessionReportResponse(
    val success: Boolean,
    val course_code: String?,
    val total_sessions: Int?,
    val students: List<StudentReportItem>?
)

// Kurs modelleri
data class GetCoursesRequest(
    val teacher_id: String? = null,
    val student_id: String? = null
)

data class CourseItem(
    val course_code: String,
    val course_name: String,
    val teacher_id: String
)

data class GetCoursesResponse(
    val success: Boolean,
    val courses: List<CourseItem>?
)

package com.example.afinal.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==========================================
// 1. NEUTRAL COLORS (Giữ nguyên)
// ==========================================
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val Transparent = Color(0x00000000)

val Gray50 = Color(0xFFF8F9FA)
val Gray100 = Color(0xFFF5F5F5)
val Gray400 = Color(0xFFBDBDBD)
val Gray600 = Color(0xFF757575)
val Gray900 = Color(0xFF2D2D2D)

// ==========================================
// 2. BLUE PALETTE (Bảng màu Xanh Dương cơ bản)
// ==========================================
val BluePrimary = Color(0xFF2196F3)      // Màu chính chuẩn
val BlueLight   = Color(0xFF6EC6FF)      // Xanh sáng
val BlueDark    = Color(0xFF0069C0)      // Xanh đậm
val TealAccent  = Color(0xFF00BFA5)      // Màu nhấn xanh ngọc

// ==========================================
// 3. APP GRADIENTS (Đã cập nhật Theme Xanh)
// ==========================================
object AppGradients {
    val classicBlue = Brush.linearGradient(
        colors = listOf(Color(0xFF2193b0), Color(0xFF6dd5ed))
    )

    val oceanBlue = Brush.verticalGradient(
        colors = listOf(Color(0xFF2E3192), Color(0xFF1BFFFF))
    )

    val techBlue = Brush.linearGradient(
        colors = listOf(Color(0xFF36D1DC), Color(0xFF5B86E5))
    )

    val skyClean = Brush.verticalGradient(
        colors = listOf(Color(0xFF4facfe), Color(0xFF00f2fe))
    )

    val homeScreen = classicBlue

    val mapScreen = techBlue

    val audioScreen = skyClean

    val userScreen = oceanBlue

    val audioPlayer = techBlue
}
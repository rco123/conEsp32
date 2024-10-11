package com.example.conesp32

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.math.cos

class CustomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.RED // 막대 색상
        strokeWidth = 20f // 막대 두께
        isAntiAlias = true // 부드러운 선
    }

    var isBarVisible: Boolean = false // 막대 표시 여부
        private set

    private var angle: Float = 0f // 기울기 각도 -45 ~ 45 범위

    // 각도 설정 함수
    fun setAngle(newAngle: Float) {
        angle = newAngle
        invalidate() // 뷰 다시 그리기 요청
    }

    // 막대 표시 여부 설정 함수
    fun setBarVisible(visible: Boolean) {
        isBarVisible = visible
        invalidate() // 뷰 다시 그리기 요청
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isBarVisible) {
            return // 막대가 보이지 않으면 그리기 종료
        }
        // 막대의 고정 길이 설정
        val fixedLength = 300f

        // 뷰의 중앙 좌표 구하기 (막대가 아래 중앙에서 시작)
        val centerX = width / 2f
        val centerY = height - 100f // 필요에 따라 조정 가능

        // 각도를 라디안으로 변환
        val theta = Math.toRadians(angle.toDouble())

        // 트리곤메트리를 이용하여 막대의 끝 좌표 계산
        val endX = centerX + fixedLength * sin(theta).toFloat()
        val endY = centerY - fixedLength * cos(theta).toFloat()

        // 막대 그리기
        canvas.drawLine(centerX, centerY, endX, endY, paint)

    }

}

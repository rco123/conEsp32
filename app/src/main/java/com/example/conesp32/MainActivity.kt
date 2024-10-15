package com.example.conesp32

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.Spanned
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayInputStream

import android.webkit.WebView
import android.webkit.WebViewClient

import com.example.conesp32.loadFallbackImage
import com.example.conesp32.loadStream
import okio.ByteString


class MainActivity : AppCompatActivity(), SensorEventListener {

    private val PREFS_NAME = "com.example.conesp32.PREFS"
    private val IP_ADDRESS_KEY = "ip_address"
    private val DEFAULT_IP_ADDRESS = "192.168.5.1"

    private var streamUrl = "http://$DEFAULT_IP_ADDRESS:81/stream" // ESP32-CAM의 IP 주소를 입력하세요
    private var imageConnectSts = false

    private lateinit var button1: Button // 연결 버튼 (포트 81)
    private lateinit var button2: Button // 종료 버튼 (포트 81)

    private lateinit var button3: Button // 출발 버튼 (포트 91)
    private lateinit var button4: Button // 정지 버튼 (포트 91)
    private lateinit var button5: Button // 완전 종료 버튼


    private lateinit var customView: CustomView

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var commandWebSocket: WebSocket? = null // 포트 8766용 WebSocket
    private val handler = Handler(Looper.getMainLooper())

    // 초기 캘리브레이션을 위한 변수
    private var initialAxisX: Float? = null
    private val calibrationThreshold = 10 // 캘리브레이션을 위한 이벤트 수
    private var calibrationCount = 0
    private var sumAxisX = 0f

    private var currentAngle: Float = 0f // 현재 각도
    private val smoothingFactor: Float = 0.1f // 보간 정도 (0에 가까울수록 천천히, 1에 가까울수록 빠르게)

    private var webSocket: WebSocket? = null  // WebSocket 객체를 저장할 전역 변수

    private val sendDataRunnable = object : Runnable {

        // 주기적 데이터 전송을 위한 변수
        private val sendInterval: Long = 100L // 데이터 전송 간격 (밀리초)

        override fun run() {
            commandWebSocket?.let { ws ->
                if (customView.isBarVisible) { // 막대가 활성화된 상태일 때만 전송
                    sendCommand(ws)
                }
                handler.postDelayed(this, sendInterval)
            }
        }
    }

    private fun sendCommand(ws: WebSocket) {
        val json = JSONObject()
        json.put("cmd", "move")
        json.put("angle", currentAngle.toInt())
        ws.send(json.toString())
        Log.i("WebSocket", "전송된 메시지: ${json.toString()}")
    }

    private fun sendStop(ws: WebSocket) {
        val json = JSONObject()
        json.put("cmd", "stop")
        json.put("angle", currentAngle.toInt())
        json.put("speed", 0)
        ws.send(json.toString())
        Log.i("WebSocket", "전송된 메시지: ${json.toString()}")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // WindowInsets 처리
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // EditText 초기화 및 필터 설정
        val inputField = findViewById<EditText>(R.id.inputField)
        val ipAddressPattern = Regex("^\\d{1,3}(\\.\\d{1,3}){0,3}$")
        val ipFilter = object : InputFilter {
            override fun filter(
                source: CharSequence,
                start: Int,
                end: Int,
                dest: Spanned,
                dstart: Int,
                dend: Int
            ): CharSequence? {
                val result = StringBuilder(dest)
                    .replace(dstart, dend, source.subSequence(start, end).toString())
                    .toString()
                return if (ipAddressPattern.matches(result)) null else ""
            }
        }
        inputField.filters = arrayOf(ipFilter)

        // SharedPreferences에서 IP 주소 불러오기
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIpAddress = sharedPreferences.getString(IP_ADDRESS_KEY, null)
        if (savedIpAddress.isNullOrEmpty()) {
            inputField.setText(DEFAULT_IP_ADDRESS)
        } else {
            inputField.setText(savedIpAddress)
        }

        val webView = findViewById<WebView>(R.id.webView)
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        // 기본 이미지 로드
        loadFallbackImage(webView)
        customView = findViewById(R.id.customView)

        // 버튼 초기화
        button1 = findViewById(R.id.button1)
        button2 = findViewById(R.id.button2)
        button3 = findViewById(R.id.button3)
        button4 = findViewById(R.id.button4)
        button5 = findViewById(R.id.button5) // 완전 종료 버튼 초기화

        // SensorManager 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var imageView = findViewById<ImageView>(R.id.imageView)


        // 버튼 클릭 리스너 설정
        button1.setOnClickListener {
            if (imageConnectSts == true) {
                Log.d("sgkim", "button 1, imageConnectSts = $imageConnectSts")
                return@setOnClickListener
            }

            val ipAddress = inputField.text.toString()
            if (ipAddress.isNotEmpty()) {
                // IP 주소 저장
                val editor = sharedPreferences.edit()
                editor.putString(IP_ADDRESS_KEY, ipAddress)
                editor.apply()

                streamUrl = "ws://$ipAddress:81/ws1"
                connectWebSocket(streamUrl, imageView)
                Toast.makeText(this, "이미지 연결을 합니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "유효한 IP 주소를 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }

        button2.setOnClickListener {
            // 기본 이미지 로드
            loadFallbackImage(webView)
            Toast.makeText(this, "이미지 WebSocket 연결이 종료되었습니다.", Toast.LENGTH_SHORT).show()
            // 막대 숨기기 및 각도 초기화
            customView.setBarVisible(false)
            customView.setAngle(0f)

            // cam view socket End
            // 기본 이미지 또는 빈 화면으로 초기화
            webSocket?.let {
                it.close(1000, "User closed connection")  // 정상적인 종료를 위해 1000 코드 사용
                webSocket = null  // WebSocket 객체 초기화
            } ?: run {
                Toast.makeText(this, "이미 연결이 종료되었습니다.", Toast.LENGTH_SHORT).show()
            }
            imageView.setImageDrawable(null)  // 이미지 제거하여 화면을 청소

            disconnectCommandWebSocket()
        }

        button3.setOnClickListener {
            Log.d("sgkim", "click button3")
            // image connection check
            if (imageConnectSts == false) {
                Log.d("sgkim", "imageConnectSts = $imageConnectSts")
                return@setOnClickListener
            }

            if (accelerometer != null) {
                // 센서 업데이트 주기 조정 (필요 시 SENSOR_DELAY_GAME 또는 SENSOR_DELAY_NORMAL로 변경 가능)
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
                customView.setBarVisible(true) // 막대 표시
                Toast.makeText(this, "출발: 센서 활성화 및 WebSocket 연결", Toast.LENGTH_SHORT).show()

                // WebSocket 연결을 8766 포트로 변경하여 출발 시 연결
                val ipAddress = inputField.text.toString()
                if (ipAddress.isNotEmpty()) {
                    connectToCommandWebSocket(ipAddress)
                } else {
                    Toast.makeText(this, "유효한 IP 주소를 입력하세요.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "가속도 센서를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        button4.setOnClickListener {
            disconnectCommandWebSocket()
            customView.setBarVisible(false) // 막대 숨기기
            customView.setAngle(0f) // 막대 각도 초기화
            Toast.makeText(this, "정지: 센서 비활성화 및 WebSocket 연결 종료", Toast.LENGTH_SHORT).show()
        }

        button5.setOnClickListener {
            // 완전 종료 버튼 클릭 시 수행할 작업
            completeExit()
        }
    }

    // WebSocket 연결 함수
    private fun connectWebSocket(url: String, imageView: ImageView) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val webSocketListener = object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@MainActivity.webSocket = webSocket  // WebSocket 객체 저장
                imageConnectSts = true
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "WebSocket 연결 성공", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // 이미지를 byte 배열로 받음
                val byteArray = bytes.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

                // UI 스레드에서 ImageView 업데이트
                Handler(Looper.getMainLooper()).post {
                    imageView.setImageBitmap(bitmap)
                }
            }
            // WebSocket 연결 종료 처리
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                imageConnectSts = false
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "WebSocket 연결이 종료되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d("sgkim", "WebSocket 연결 실패: ${t.message}")
            }
        }

        // WebSocket 시작
        client.newWebSocket(request, webSocketListener)
        client.dispatcher.executorService.shutdown()
    }

    private fun connectToCommandWebSocket(ipAddress: String) {
        // 기존 Command WebSocket 연결이 있다면 종료
        if (commandWebSocket != null) {
            commandWebSocket?.close(1000, "Reconnecting")
            commandWebSocket = null
        }

        val url = "ws://$ipAddress:82/ws2"
        val client = OkHttpClient()

        val request = Request.Builder().url(url).build()
        commandWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                handler.post {
                    Toast.makeText(this@MainActivity, "Command WebSocket 연결 성공", Toast.LENGTH_SHORT)
                        .show()
                    Log.i("WebSocket", "Command WebSocket 연결 성공: $url")

                    // 출발 시 주기적 데이터 전송 시작
                    handler.post(sendDataRunnable)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // 서버로부터 메시지를 수신할 경우 처리 (필요 시 구현)
                Log.i("WebSocket", "Command WebSocket 수신된 메시지: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handler.post {
                    Toast.makeText(
                        this@MainActivity,
                        "Command WebSocket 연결 실패: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("WebSocket", "Command WebSocket 연결 실패: ${t.message}", t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handler.post {
                    Toast.makeText(
                        this@MainActivity,
                        "Command WebSocket 연결 종료: $reason",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.i("WebSocket", "Command WebSocket 연결 종료: $reason 코드: $code")

                    // 주기적 데이터 전송 중지
                    handler.removeCallbacks(sendDataRunnable)

                    commandWebSocket = null
                }
            }
        })

        // WebSocket을 유지하기 위해 ExecutorService를 종료하지 않음
        // client.dispatcher.executorService.shutdown()
    }

    // WebSocket 연결 해제 함수 (82용)
    private fun disconnectCommandWebSocket() {
        // 주기적 데이터 전송 중지
        handler.removeCallbacks(sendDataRunnable)
        commandWebSocket?.let{sendStop(it)}
        commandWebSocket?.close(1000, "Connection closed by user")
        commandWebSocket = null
    }

    // SensorEventListener 구현
    override fun onSensorChanged(event: SensorEvent) {
        if (initialAxisX == null) {
            // 초기 값을 설정하기 위해 센서 값을 누적
            sumAxisX += event.values[0]
            calibrationCount++

            if (calibrationCount >= calibrationThreshold) {
                initialAxisX = sumAxisX / calibrationThreshold
                Log.i("Calibration", "Initial AxisX set to $initialAxisX")
            }
            return // 초기 캘리브레이션 중에는 나머지 로직을 실행하지 않음
        }

        // 초기 값으로부터 조정된 값 계산
        val adjustedAxisX = event.values[0] - initialAxisX!!

        // 각도로 변환 (-45 ~ 45)
        val targetAngle = (adjustedAxisX * 10f).coerceIn(-45f, 45f) // 스케일링 값 조정

        // 부드럽게 값 변화를 보간하여 새로운 각도 계산
        currentAngle = currentAngle + smoothingFactor * (targetAngle - currentAngle)

        // CustomView에 부드럽게 변환된 각도 전달
        customView.setAngle(-currentAngle)

        // 로그 출력
        Log.i("Sensor", "Adjusted AxisX: $adjustedAxisX, Angle: $currentAngle")
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // 필요 시 센서 정확도 변화 처리
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onLoadSuccess() {
            Log.d("sgkim", "call on LoadSuccess");
            runOnUiThread {
                imageConnectSts = true
                Toast.makeText(this@MainActivity, "스트리밍 로드 성공!", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun onLoadError() {
            runOnUiThread {
                Log.d("sgkim", "call on onLoadError fail");
                imageConnectSts = false
                Toast.makeText(this@MainActivity, "스트리밍 로드 실패!", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun onNetworkDisconnect() {
            runOnUiThread {
                Log.d("sgkim", "Network disconnected")
                imageConnectSts = false
                Toast.makeText(this@MainActivity, "네트워크 연결이 끊어졌습니다.", Toast.LENGTH_SHORT).show()
                // 추가적인 처리 로직 (예: 재연결 시도 버튼 활성화 등)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        imageConnectSts = false
        disconnectCommandWebSocket()
        // 센서 리스너 해제
        sensorManager.unregisterListener(this)
    }

    // 속도 계산 함수 (필요에 따라 구현)
    private fun calculateSpeed(angle: Float): Int {
        // 예시: 각도에 비례하여 속도 설정
        return (angle * 2).toInt() // 각도 45도일 때 속도 90
    }

    // 완전 종료 함수
    private fun completeExit() {
        // 모든 WebSocket 연결 해제
        imageConnectSts = false
        disconnectCommandWebSocket()
        // 센서 리스너 해제
        sensorManager.unregisterListener(this)
        // 애플리케이션 종료
        Toast.makeText(this, "앱을 종료합니다.", Toast.LENGTH_SHORT).show()
        finishAffinity() // 현재 작업 스택의 모든 액티비티를 종료
        System.exit(0) // 프로세스 종료
    }

}

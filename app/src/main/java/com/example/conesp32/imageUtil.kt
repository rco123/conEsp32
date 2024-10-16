package com.example.conesp32

import android.webkit.WebView


fun loadStream(webView: WebView, streamUrl:String) {
    // 스트리밍 URL을 WebView에 로드
    val html = """
            <html>
            <body style="margin: 0; padding: 0;">
                <img id="streamImage" src="$streamUrl" style="width: 100%; height: 100%; object-fit: cover;"
                     onload="Android.onLoadSuccess()"
                     onerror="Android.onLoadError()" />
            </body>
            </html>
            
        """.trimIndent()
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
//
//fun loadFallbackImage(webView: WebView) {
//    val fallbackImageUrl = "file:///android_res/drawable/aicar.png" // 로컬 이미지 URL
//    // 로컬 drawable 이미지를 WebView에 로드
//    val html = """
//        <html>
//        <body style="margin: 0; padding: 0;">
//            <img src="$fallbackImageUrl"
//                style="height: 100%; width: 100%; object-fit: cover;" />
//
//            <script>
//            // 네트워크 연결이 끊어졌을 때 호출되는 함수
//            console.log('Network connection lost');
//            Android.onNetworkDisconnect();
//            </script>
//
//        </body>
//        </html>
//    """.trimIndent()
//    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
//}

fun loadFallbackImage(webView: WebView) {
    val fallbackImageUrl = "file:///android_res/drawable/aicar.png" // 로컬 이미지 URL
    // 로컬 drawable 이미지를 WebView에 로드
    val html = """
        <html>
        <body style="margin: 0; padding: 0;">
            <canvas id="imageCanvas" style="height: 100%; width: 100%; object-fit: cover;"></canvas>
          
            <script>
            // 이미지 로드 후, canvas에 그리기
            var img = new Image();
            img.src = "$fallbackImageUrl";
            
            img.onload = function() {
                var canvas = document.getElementById('imageCanvas');
                var ctx = canvas.getContext('2d');
                canvas.width = window.innerWidth;
                canvas.height = window.innerHeight;
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            };

            // 네트워크 연결이 끊어졌을 때 호출되는 함수
            console.log('Network connection lost');
            Android.onNetworkDisconnect();
            </script>
                
        </body>
        </html>
    """.trimIndent()
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}





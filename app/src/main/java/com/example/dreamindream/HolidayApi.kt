package com.example.dreamindream

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// 공휴일 데이터 클래스
data class Holiday(val date: LocalDate, val name: String)

object HolidayApi {

    private const val encodedApiKey =
        "VLCz2uoayEygXdz%2BGzd1zUKlyN%2Bod2vj95JMIm5oiHBQmKwTzcORGNJCS84JgGNkBLYqOyXpLS0Bu8GyG0RAYA%3D%3D"
    private val client = OkHttpClient()

    fun fetchHolidays(
        year: Int,
        onSuccess: (List<Holiday>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val url =
            "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo" +
                    "?solYear=$year&numOfRows=100&_type=json&ServiceKey=$encodedApiKey"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HolidayApi", "API 요청 실패", e)
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = mutableListOf<Holiday>()

                try {
                    val body = response.body?.string() ?: throw IOException("응답 없음")
                    val root = JSONObject(body)
                        .getJSONObject("response")
                        .getJSONObject("body")
                        .getJSONObject("items")

                    val items = root.optJSONArray("item")

                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            result.add(parseHoliday(item))
                        }
                    } else {
                        val singleItem = root.getJSONObject("item")
                        result.add(parseHoliday(singleItem))
                    }

                    onSuccess(result)

                } catch (e: Exception) {
                    Log.e("HolidayApi", "파싱 오류", e)
                    onError(e)
                }
            }
        })
    }

    private fun parseHoliday(item: JSONObject): Holiday {
        val dateStr = item.getString("locdate") // 예: "20250101"
        val name = item.getString("dateName")
        val date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"))
        return Holiday(date, name)
    }
}

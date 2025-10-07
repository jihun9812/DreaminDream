// file: app/src/main/java/com/example/dreamindream/HolidayApi.kt
package com.dreamindream.app

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object HolidayApi {

    // 퍼센트-인코딩된 서비스키 (그대로 사용)
    private const val encodedApiKey =
        "VLCz2uoayEygXdz%2BGzd1zUKlyN%2Bod2vj95JMIm5oiHBQmKwTzcORGNJCS84JgGNkBLYqOyXpLS0Bu8GyG0RAYA%3D%3D"

    private const val BASE =
        "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo"

    private val yyyyMMdd = DateTimeFormatter.ofPattern("yyyyMMdd")

    // OkHttp 4.x 클라이언트
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 단일 연도 공휴일 조회 */
    fun fetchHolidays(
        year: Int,
        onSuccess: (List<Holiday>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        try {
            val url = BASE.toHttpUrl().newBuilder()
                .addQueryParameter("solYear", year.toString())
                .addQueryParameter("numOfRows", "100")
                .addQueryParameter("_type", "json")
                // 이미 퍼센트-인코딩된 키이므로 Encoded API 사용(재인코딩 방지)
                .addEncodedQueryParameter("ServiceKey", encodedApiKey)
                .build()

            val req = Request.Builder().url(url).get().build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("HolidayApi", "API 요청 실패", e)
                    onError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        try {
                            if (!res.isSuccessful) {
                                onError(IllegalStateException("HTTP ${res.code}"))
                                return
                            }
                            val body = res.body?.string() ?: throw IOException("응답 없음")

                            val itemsObj = JSONObject(body)
                                .optJSONObject("response")
                                ?.optJSONObject("body")
                                ?.optJSONObject("items")

                            // 빈 응답 방어
                            if (itemsObj == null) {
                                onSuccess(emptyList()); return
                            }

                            val arr = itemsObj.optJSONArray("item")
                            val list = mutableListOf<Holiday>()

                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    list += parseHoliday(arr.getJSONObject(i))
                                }
                            } else {
                                // 단일 객체 케이스
                                itemsObj.optJSONObject("item")?.let { single ->
                                    list += parseHoliday(single)
                                }
                            }

                            onSuccess(list.sortedBy { it.date })
                        } catch (e: Exception) {
                            Log.e("HolidayApi", "파싱 오류", e)
                            onError(e)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            onError(e)
        }
    }

    /** 여러 해를 순차로 조회 (실패 연도는 빈 리스트로 이어서 진행) */
    fun fetchHolidaysRange(
        startYear: Int,
        endYear: Int,
        onSuccess: (Map<Int, List<Holiday>>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val result = LinkedHashMap<Int, List<Holiday>>()

        fun loop(y: Int) {
            if (y > endYear) {
                onSuccess(result); return
            }
            fetchHolidays(
                y,
                onSuccess = { list ->
                    result[y] = list
                    loop(y + 1)
                },
                onError = { e ->
                    Log.e("HolidayApi", "fetch $y failed", e)
                    result[y] = emptyList()
                    loop(y + 1)
                }
            )
        }
        loop(startYear)
    }

    private fun parseHoliday(item: JSONObject): Holiday {
        val dateStr = item.getString("locdate") // 예: "20250101"
        val name = item.getString("dateName")
        val date = LocalDate.parse(dateStr, yyyyMMdd)
        return Holiday(date, name)
    }
}

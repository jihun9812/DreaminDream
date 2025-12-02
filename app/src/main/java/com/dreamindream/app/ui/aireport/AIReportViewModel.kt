package com.dreamindream.app.ui.aireport

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dreamindream.app.BuildConfig
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException



class AIReportViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AIReportVM"
        private const val OKHTTP_TIMEOUT = 20L
        private const val PRO_WATCHDOG_MS = 30_000L
        private const val RELOAD_DEBOUNCE_MS = 300L
        private const val EMPTY_DIALOG_DELAY_MS = 1_000L
        private const val MIN_ENTRIES_FOR_REPORT = 2
    }

    private val app: Application get() = getApplication()
    private val res get() = app.resources

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val uid: String? get() = auth.currentUser?.uid

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _uiState = MutableStateFlow(AIReportUiState())
    val uiState: StateFlow<AIReportUiState> = _uiState.asStateFlow()

    private lateinit var prefs: SharedPreferences

    // Fragment 에 있던 State 들
    private var targetWeekKey: String = FirestoreManager.thisWeekKey()
    private var lastDreamCount: Int = 0

    private var lastFeeling: String = ""
    private var lastKeywords: List<String> = emptyList()
    private var lastEmoLabels: List<String> = emptyList()
    private var lastEmoDist: List<Float> = emptyList()
    private var lastThemeLabels: List<String> = emptyList()
    private var lastThemeDist: List<Float> = emptyList()

    private var adGateInProgress: Boolean = false
    private var proInFlight: Boolean = false
    private var proCompleted: Boolean = false
    private var proNeedRefresh: Boolean = false

    private var reloadScheduled: Boolean = false
    private var isReloading: Boolean = false
    private var emptyDialogScheduled: Boolean = false
    private var emptyDialogShown: Boolean = false
    private var emptyDialogRunnable: Runnable? = null
    private var autoSwitchedFrom: String? = null

    // PRO KPI
    private var proHasMetrics: Boolean = false
    private var proKpiPos: Float = 0f
    private var proKpiNeu: Float = 0f
    private var proKpiNeg: Float = 0f

    // 네트워크 (예전 prefetch 구조는 단순화해서 “광고 끝 → 바로 요청” 흐름으로 갈 수 있음)
    private var proCall: Call? = null
    private var proWatchdog: Runnable? = null
    init {
        val uidPart = uid ?: "guest"
        prefs = app.getSharedPreferences(
            "weekly_report_cache_$uidPart",
            Context.MODE_PRIVATE
        )

        _uiState.update {
            it.copy(
                targetWeekKey = targetWeekKey,
                analysisTitle = res.getString(R.string.ai_basic_title)
            )
        }
    }

    // ---------- 공용 헬퍼 ----------

    private fun s(@StringRes id: Int, vararg args: Any) =
        app.getString(id, *args)

    private fun setState(block: (AIReportUiState) -> AIReportUiState) {
        _uiState.update(block)
    }

    private fun isProPending(): Boolean {
        val until = prefs.getLong("pro_pending_until_${targetWeekKey}", 0L)
        return SystemClock.elapsedRealtime() < until
    }

    private fun setProPending(ttlMs: Long) {
        val until = SystemClock.elapsedRealtime() + ttlMs
        prefs.edit().putLong("pro_pending_until_${targetWeekKey}", until).apply()
    }

    private fun applyProButtonState() {
        val enabledBase =
            BuildConfig.OPENAI_API_KEY.isNotBlank() &&
                    !proInFlight &&
                    lastDreamCount >= MIN_ENTRIES_FOR_REPORT &&
                    !isProPending() &&
                    !adGateInProgress

        val text: String
        val title: String
        val enabled: Boolean
        val alpha: Float

        when {
            proCompleted && !proNeedRefresh -> {
                text = s(R.string.pro_completed)
                title = s(R.string.ai_pro_title)
                enabled = false
                alpha = 0.65f
            }

            proCompleted && proNeedRefresh -> {
                text = s(R.string.pro_refresh)
                title = s(R.string.ai_pro_title)
                enabled = enabledBase
                alpha = if (enabledBase) 1f else 0.65f
            }

            else -> {
                text = s(R.string.pro_cta)
                title = s(R.string.ai_basic_title)
                enabled = enabledBase
                alpha = if (enabledBase) 1f else 0.65f
            }
        }

        setState {
            it.copy(
                proButtonText = text,
                proButtonEnabled = enabled,
                proButtonAlpha = alpha,
                analysisTitle = title
            )
        }
    }

    private fun beginLoading() {
        setState { it.copy(isLoading = true) }
    }

    private fun endLoading() {
        setState { it.copy(isLoading = false) }
        applyProButtonState()
    }

    private fun formatAgo(ts: Long): String {
        if (ts <= 0) return ""
        val d = (System.currentTimeMillis() - ts) / 1000
        return when {
            d < 60    -> "${d}${s(R.string.time_seconds_suffix)}"
            d < 3600  -> "${d / 60}${s(R.string.time_minutes_suffix)}"
            d < 86400 -> "${d / 3600}${s(R.string.time_hours_suffix)}"
            else      -> "${d / 86400}${s(R.string.time_days_suffix)}"
        }
    }

    private fun computeKpis(labels: List<String>, dist: List<Float>): Triple<Float, Float, Float> {
        fun sumByLabels(targets: List<String>): Float {
            if (labels.size != dist.size || labels.isEmpty()) return 0f
            var s = 0f
            val used = HashSet<Int>()
            for (t in targets) {
                val idx = labels.indexOf(t)
                if (idx >= 0 && used.add(idx)) s += dist[idx].coerceAtLeast(0f)
            }
            return s
        }

        val pos = sumByLabels(
            listOf(
                s(R.string.emo_positive),
                s(R.string.emo_calm),
                s(R.string.emo_vitality),
                s(R.string.emo_flow)
            )
        )
        val neu = sumByLabels(listOf(s(R.string.emo_neutral)))
        val neg = sumByLabels(
            listOf(
                s(R.string.emo_confusion),
                s(R.string.emo_anxiety),
                s(R.string.emo_depression),
                s(R.string.emo_fatigue),
                s(R.string.emo_depression_fatigue)
            )
        )
        val sum = pos + neu + neg
        if (sum <= 0f) return Triple(0f, 0f, 0f)
        fun r1(x: Float) = kotlin.math.round(x * 10f) / 10f
        val scale = 100f / sum
        return Triple(r1(pos * scale), r1(neu * scale), r1(neg * scale))
    }

    private fun ensureTopNThemes(
        labels: List<String>,
        dist: List<Float>,
        n: Int
    ): Pair<List<String>, List<Float>> {
        if (labels.isEmpty() || dist.isEmpty()) {
            val def = res.getStringArray(R.array.theme_labels_default).toList().take(n)
            return def to List(def.size) { 0f }
        }
        val pairs = labels.zip(dist).sortedByDescending { it.second }.take(n).toMutableList()
        while (pairs.size < n) pairs += s(R.string.theme_other) to 0f
        return pairs.map { it.first } to pairs.map { it.second }
    }

    private fun wrapByWords(s0: String, maxPerLine: Int = 9): String {
        val words = s0.replace("-", " ").split(" ")
        val out = StringBuilder()
        var line = StringBuilder()
        for (w in words) {
            if (line.isNotEmpty() && line.length + 1 + w.length > maxPerLine) {
                out.append(line.toString().trim()).append('\n')
                line = StringBuilder()
            }
            line.append(w).append(' ')
        }
        out.append(line.toString().trim())
        return out.toString()
    }

    private fun updateHeader(
        sourceCount: Int? = null,
        rebuiltAt: Long? = null,
        tier: String? = null,
        proAt: Long? = null,
        stale: Boolean? = null
    ) {
        val baseTitle = if (autoSwitchedFrom != null)
            s(R.string.report_title_prev_week)
        else
            s(R.string.report_title_this_week)

        val metaLine = if (sourceCount != null && rebuiltAt != null && rebuiltAt > 0L) {
            s(R.string.report_meta_format, sourceCount, formatAgo(rebuiltAt))
        } else ""

        val labelText = when {
            autoSwitchedFrom != null ->
                baseTitle + "\n" + s(R.string.report_switched_due_to_lack)

            metaLine.isNotBlank() ->
                baseTitle + "\n" + metaLine

            else -> baseTitle
        }

        val needRefresh =
            (stale == true) || (tier == "pro" && (proAt ?: 0L) < (rebuiltAt ?: 0L))
        proNeedRefresh = needRefresh
        proCompleted =
            (tier == "pro" && !needRefresh) || prefs.getBoolean("pro_done_${targetWeekKey}", false)

        applyProButtonState()
        setState { it.copy(weekLabel = labelText) }
    }

    // ---------- 초기화 / 시작 ----------

    /**
     * Compose 진입 지점에서 호출.
     * weekKey 인자가 있으면 지정 주간, 없으면 이번 주.
     */
    fun onStart(weekKey: String?) {
        targetWeekKey = weekKey ?: FirestoreManager.thisWeekKey()
        setState { it.copy(targetWeekKey = targetWeekKey) }

        prefillFromCache(targetWeekKey)

        if (prefs.getBoolean("pro_done_${targetWeekKey}", false)) {
            proCompleted = true
        }
        applyProButtonState()
        scheduleReload(targetWeekKey)
    }

    // ---------- 캐시 프리필 ----------

    private fun prefillFromCache(weekKey: String) {
        prefs.getString("last_feeling_${weekKey}", null)?.let { cf ->
            val ck = prefs.getString("last_keywords_${weekKey}", null)
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val ca = prefs.getString("last_analysis_${weekKey}", null).orEmpty()
            val el = prefs.getString("last_emo_labels_${weekKey}", null)
                ?.split("|") ?: emptyList()
            val ed = prefs.getString("last_emo_dist_${weekKey}", null)
                ?.split(",")
                ?.mapNotNull { it.toFloatOrNull() } ?: emptyList()
            val tl = prefs.getString("last_theme_labels_${weekKey}", null)
                ?.split("|") ?: emptyList()
            val td = prefs.getString("last_theme_dist_${weekKey}", null)
                ?.split(",")
                ?.mapNotNull { it.toFloatOrNull() } ?: emptyList()

            // PRO KPI
            prefs.getString("pro_kpi_${weekKey}", null)?.let { line ->
                val parts = line.split(",")
                if (parts.size >= 3) {
                    proHasMetrics = true
                    proKpiPos = parts[0].toFloatOrNull() ?: 0f
                    proKpiNeu = parts[1].toFloatOrNull() ?: 0f
                    proKpiNeg = parts[2].toFloatOrNull() ?: 0f
                }
            }

            lastFeeling = cf
            lastKeywords = ck
            lastEmoLabels = el
            lastEmoDist = ed
            lastThemeLabels = tl
            lastThemeDist = td

            if ((lastKeywords.isNotEmpty() || ca.isNotBlank()) &&
                (el.isNotEmpty() || proHasMetrics)
            ) {
                bindUI(
                    weekKey = weekKey,
                    feeling = cf,
                    keywords = ck,
                    analysis = ca,
                    emoLabels = el,
                    emoDist = ed,
                    themeLabels = tl,
                    themeDist = td
                )
            }
        }
    }

    // ---------- 리로드(debounce) ----------

    private fun scheduleReload(weekKey: String) {
        targetWeekKey = weekKey
        autoSwitchedFrom = null
        if (reloadScheduled) return
        reloadScheduled = true
        mainHandler.postDelayed({
            reloadScheduled = false
            reloadForWeekInternal(weekKey)
        }, RELOAD_DEBOUNCE_MS)
    }

    private fun reloadForWeekInternal(weekKey: String) {
        if (isReloading) return
        isReloading = true
        beginLoading()
        cancelEmptyDialogSchedule()

        if (prefs.getBoolean("pro_done_$weekKey", false)) {
            proCompleted = true
        }

        val userId = uid
        if (userId == null) {
            // 로그인 안되어 있으면 그냥 비어있는 상태
            isReloading = false
            endLoading()
            showReport(false)
            return
        }

        FirestoreManager.countDreamEntriesForWeek(userId, weekKey) { dreamCount ->
            lastDreamCount = dreamCount
            applyProButtonState()

            if (dreamCount < MIN_ENTRIES_FOR_REPORT) {
                showReport(false)
                scheduleEmptyDialog()
                endLoading()
                isReloading = false
                return@countDreamEntriesForWeek
            }

            // Fragment 의 loadWeeklyReportFull 흐름 그대로 옮길 자리
            // 1) FirestoreManager.loadWeeklyReportFull(...)
            // 2) 필요하면 aggregateDreamsForWeek(...)
            // 3) updateHeader(...) + bindUI(...)
            //    전부 UI 대신 uiState 를 갱신하는 형태로.
            //
            // === 예시: 기본 골격 ===
            FirestoreManager.loadWeeklyReportFull(
                app,
                userId,
                weekKey
            ) { feeling, keywords, analysis,
                emoLabels, emoDist, themeLabels, themeDist,
                sourceCount, lastRebuiltAt, tier, proAt, stale ->

                // 여기 안쪽은 AIReportFragment 의 동일 콜백 부분을
                // uiState 업데이트 버전으로 그대로 옮기면 됨. :contentReference[oaicite:8]{index=8}

                lastFeeling = feeling
                lastKeywords = keywords
                lastEmoLabels = emoLabels
                lastEmoDist = emoDist
                lastThemeLabels = themeLabels
                lastThemeDist = themeDist

                updateHeader(sourceCount, lastRebuiltAt, tier, proAt, stale)
                bindUI(
                    weekKey,
                    feeling,
                    keywords,
                    analysis,
                    emoLabels,
                    emoDist,
                    themeLabels,
                    themeDist
                )
                endLoading()
                isReloading = false
            }
        }
    }

    // ---------- UI 바인딩 (TextView → uiState) ----------

    private fun bindUI(
        weekKey: String,
        feeling: String,
        keywords: List<String>,
        analysis: String,
        emoLabels: List<String>,
        emoDist: List<Float>,
        themeLabels: List<String>,
        themeDist: List<Float>
    ) {
        showReport(true)

        val feelingLocalized = feeling.trim()
        val kw1 = keywords.asSequence()
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .distinct()
            .take(1)
            .toList()
        val keywordsLine =
            s(R.string.keywords_format, feelingLocalized, kw1.joinToString(", "))

        val (pos, neu, neg) = if (proHasMetrics) {
            Triple(proKpiPos, proKpiNeu, proKpiNeg)
        } else {
            computeKpis(emoLabels, emoDist)
        }

        val (tL, tD) = ensureTopNThemes(themeLabels, themeDist, 5)

        // 캐시 저장
        prefs.edit()
            .putString("last_feeling_$weekKey", feeling)
            .putString("last_keywords_$weekKey", keywords.joinToString("|"))
            .putString("last_analysis_$weekKey", analysis.take(5000))
            .putString("last_emo_labels_$weekKey", emoLabels.joinToString("|"))
            .putString("last_emo_dist_$weekKey", emoDist.joinToString(","))
            .putString("last_theme_labels_$weekKey", tL.joinToString("|"))
            .putString("last_theme_dist_$weekKey", tD.joinToString(","))
            .apply()

        lastFeeling = feeling
        lastKeywords = keywords
        lastEmoLabels = emoLabels
        lastEmoDist = emoDist
        lastThemeLabels = tL
        lastThemeDist = tD

        setState {
            it.copy(
                showReportCard = true,
                showEmptyState = false,
                keywordsLine = keywordsLine,
                analysisHtml = analysis,
                emotionLabels = emoLabels,
                emotionDist = emoDist,
                themeLabels = tL.map { label -> wrapByWords(label, 9) },
                themeDist = tD,
                kpiPositiveText = String.format("%.1f%%", pos),
                kpiNeutralText = String.format("%.1f%%", neu),
                kpiNegativeText = String.format("%.1f%%", neg)
            )
        }

        applyProButtonState()
    }

    private fun showReport(has: Boolean) {
        setState {
            it.copy(
                showReportCard = has,
                showEmptyState = !has
            )
        }
        if (has) cancelEmptyDialogSchedule()
    }

    // ---------- 빈 다이얼로그 스케줄 ----------

    private fun scheduleEmptyDialog() {
        if (emptyDialogShown || emptyDialogScheduled) return
        emptyDialogScheduled = true
        emptyDialogRunnable = Runnable {
            emptyDialogScheduled = false
            emptyDialogShown = true
            setState { it.copy(showEmptyDialog = true) }
        }
        mainHandler.postDelayed(emptyDialogRunnable!!, EMPTY_DIALOG_DELAY_MS)
    }

    private fun cancelEmptyDialogSchedule() {
        emptyDialogRunnable?.let { mainHandler.removeCallbacks(it) }
        emptyDialogRunnable = null
        emptyDialogScheduled = false
    }

    fun onEmptyDialogDismissed() {
        setState { it.copy(showEmptyDialog = false) }
    }

    fun onEmptyDialogCta() {
        // 여기서 바로 네비게이션을 타지는 않고,
        // Compose Route 쪽에서 콜백으로 받아 사용.
        // 이 ViewModel 쪽에서는 단순히 다이얼로그 닫기만.
        setState { it.copy(showEmptyDialog = false) }
    }

    // ---------- 히스토리 버튼 ----------

    fun onHistoryClicked() {
        val hasReport = uiState.value.showReportCard &&
                lastDreamCount >= MIN_ENTRIES_FOR_REPORT

        if (hasReport) {
            // 바텀시트: 히스토리 리스트 로딩
            loadHistoryKeysAndShowSheet()
        } else {
            // 빈 상태 안내 다이얼로그
            setState { it.copy(showEmptyDialog = true) }
        }
    }

    private fun loadHistoryKeysAndShowSheet(maxItems: Int = 26) {
        val userId = uid ?: return
        FirestoreManager.listWeeklyReportKeys(userId, maxItems) { keys ->
            setState {
                it.copy(
                    showHistorySheet = true,
                    historyWeeks = keys,
                    historyTotalWeeksLabel = s(R.string.bs_meta_total_weeks_format, keys.size)
                )
            }
        }
    }

    fun onHistorySheetDismiss() {
        setState { it.copy(showHistorySheet = false) }
    }

    fun onHistoryWeekPicked(weekKey: String) {
        cancelEmptyDialogSchedule()
        setState { it.copy(showHistorySheet = false) }
        scheduleReload(weekKey)
    }

    // ---------- PRO 흐름 (광고 연동 지점) ----------

    /**
     * PRO 버튼 눌렀을 때 호출.
     * - 조건 체크 (로그인, API KEY, 최소 꿈 개수)
     * - 이상 없으면 호출 측(Composable)에서 광고 게이트 열도록 신호만 보냄.
     */
    fun onProButtonClicked(): Boolean /* openGate? */ {
        val userId = uid ?: run {
            pushSnack(s(R.string.login_required))
            return false
        }
        if (BuildConfig.OPENAI_API_KEY.isBlank()) {
            pushSnack(s(R.string.api_key_missing))
            return false
        }
        if (lastDreamCount < MIN_ENTRIES_FOR_REPORT) {
            pushSnack(s(R.string.pro_requires_min, MIN_ENTRIES_FOR_REPORT))
            applyProButtonState()
            return false
        }

        // 광고 게이트 열어도 된다는 의미
        setProPending(3_000L)
        applyProButtonState()
        return true
    }

    /**
     * 광고(리워드) 시청이 끝난 뒤 AdkitCompose 쪽에서 호출해주면 됨.
     * 여기서 실제 OpenAI PRO 분석 호출 시작.
     */
    fun onProGateUnlocked() {
        val userId = uid ?: run {
            pushSnack(s(R.string.login_required))
            return
        }

        // 이미 요청 중이면 중복 방지
        if (proInFlight || proCall != null) return

        proInFlight = true
        adGateInProgress = false
        setState { it.copy(isProSpinnerVisible = true) }
        applyProButtonState()

        FirestoreManager.collectWeekEntriesLimited(
            uid = userId,
            weekKey = targetWeekKey,
            limit = 4
        ) { entries, totalCount ->
            if (totalCount < MIN_ENTRIES_FOR_REPORT) {
                cancelPro("not-enough-entries")
                pushSnack(s(R.string.pro_requires_min, MIN_ENTRIES_FOR_REPORT))
                return@collectWeekEntriesLimited
            }

            val dreams = entries.mapNotNull { it.dream }.filter { it.isNotBlank() }
            val interps = entries.mapNotNull { it.interp }.filter { it.isNotBlank() }

            val sys = systemMessageForPro()
            val prompt = buildProPrompt(dreams, interps)

            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", sys))
                .put(JSONObject().put("role", "user").put("content", prompt))

            val body = JSONObject().apply {
                put("model", "gpt-4.1-mini")
                put("temperature", 0.5)
                put("messages", messages)
                put("max_tokens", 1800)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .build()

            // 타임아웃 watchdog
            proWatchdog?.let { mainHandler.removeCallbacks(it) }
            val watchdog = Runnable {
                if (proInFlight) {
                    cancelPro("timeout")
                    pushSnack(s(R.string.pro_timeout))
                }
            }
            proWatchdog = watchdog
            mainHandler.postDelayed(watchdog, PRO_WATCHDOG_MS)

            val call = httpClient.newCall(req)
            proCall = call

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (proCall !== call) return

                    proWatchdog?.let { mainHandler.removeCallbacks(it) }
                    proWatchdog = null
                    proCall = null

                    cancelPro("network-fail")
                    mainHandler.post {
                        pushSnack(s(R.string.network_error_pro))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (proCall !== call) {
                        response.close()
                        return
                    }

                    val bodyText = response.body?.string().orEmpty()
                    val isOk = response.isSuccessful
                    val code = response.code
                    response.close()

                    proWatchdog?.let { mainHandler.removeCallbacks(it) }
                    proWatchdog = null
                    proCall = null

                    if (!isOk) {
                        val errMsg = try {
                            JSONObject(bodyText)
                                .optJSONObject("error")
                                ?.optString("message")
                                .orEmpty()
                        } catch (_: Exception) {
                            ""
                        }

                        cancelPro("http-$code")
                        val shown = if (errMsg.isBlank())
                            s(R.string.please_try_again)
                        else
                            errMsg

                        mainHandler.post {
                            pushSnack(
                                s(
                                    R.string.server_response_error,
                                    code,
                                    shown
                                )
                            )
                        }
                        return
                    }

                    val raw = try {
                        JSONObject(bodyText)
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } catch (_: Exception) {
                        ""
                    }

                    // 메인 스레드에서 결과 반영
                    mainHandler.post {
                        onProResult(userId, raw)
                    }
                }
            })
        }
    }


    /**
     * PRO 결과 적용 (AIReportFragment 의 applyProResult 를 ViewModel 버전으로 옮긴 자리)
     */
    fun onProResult(userId: String, contentRaw: String) {
        proInFlight = false
        setState { it.copy(isProSpinnerVisible = false) }

        val content = contentRaw.ifBlank { "" }
        if (content.isBlank()) {
            pushSnack(s(R.string.pro_result_unparsable))
            applyProButtonState()
            return
        }

        // 1) 모델이 HTML 주석으로 내려준 KPI 파싱
        parseProMetricsFromHtml(content)?.let { m ->
            proHasMetrics = true
            proKpiPos = m.pos
            proKpiNeu = m.neu
            proKpiNeg = m.neg

            // KPIs 는 주간 데이터가 바뀔 때까지 재사용
            prefs.edit()
                .putString(
                    "pro_kpi_${targetWeekKey}",
                    "${m.pos},${m.neu},${m.neg}"
                )
                .apply()
        }

        // 2) HTML 정리 후 캐시에 저장
        val sanitized = sanitizeModelHtml(content)

        prefs.edit()
            .putBoolean("pro_done_${targetWeekKey}", true)
            .putString("last_analysis_${targetWeekKey}", sanitized.take(5000))
            .putString("last_feeling_${targetWeekKey}", lastFeeling)
            .putString("last_keywords_${targetWeekKey}", lastKeywords.joinToString("|"))
            .apply()

        // 3) Firestore 에 PRO 업그레이드 내용 기록
        try {
            FirestoreManager.saveProUpgrade(
                uid = userId,
                weekKey = targetWeekKey,
                feeling = lastFeeling,
                keywords = lastKeywords,
                analysis = sanitized,
                model = "gpt-4.1-mini"
            ) { /* no-op */ }
        } catch (_: Throwable) {
            // 저장 실패해도 UI 흐름은 유지
        }

        proCompleted = true
        proNeedRefresh = false
        applyProButtonState()

        // 4) 새 분석 내용 기준으로 다시 바인딩
        bindUI(
            weekKey = targetWeekKey,
            feeling = lastFeeling,
            keywords = lastKeywords,
            analysis = sanitized,
            emoLabels = lastEmoLabels,
            emoDist = lastEmoDist,
            themeLabels = lastThemeLabels,
            themeDist = lastThemeDist
        )
    }


    private fun pushSnack(msg: String) {
        setState { it.copy(snackbarMessage = msg) }
    }

    fun onSnackbarShown() {
        setState { it.copy(snackbarMessage = null) }
    }

    // ---------- 차트 info ----------
    private fun cancelPro(reason: String) {
        // 네트워크 콜 정리
        proCall?.cancel()
        proCall = null

        proWatchdog?.let { mainHandler.removeCallbacks(it) }
        proWatchdog = null

        proInFlight = false
        adGateInProgress = false

        // UI 상태 정리
        setState { it.copy(isProSpinnerVisible = false) }
        applyProButtonState()
    }
    /** e.g., locale-native name like العربية / हिन्दी / 中文 */
    private fun currentPromptLanguage(): String {
        val loc = res.configuration.locales[0]
        return loc.getDisplayLanguage(loc).trim().ifBlank { "English" }
    }

    /** system 메시지 (섹션 순서 + JSON 주석 강제) */
    private fun systemMessageForPro(): String {
        val intro = s(R.string.week_prompt_intro)
        val rules = s(R.string.week_prompt_rules)
        val langLine = try {
            s(R.string.week_prompt_lang_line, currentPromptLanguage())
        } catch (_: Exception) {
            "Answer in ${currentPromptLanguage()} only."
        }

        val hMain = s(R.string.section_title_weekly_deep_main)
        val hSummary = s(R.string.section_title_summary3)
        val hPattern = s(R.string.section_title_emotion_pattern)
        val hSymbols = s(R.string.section_title_symbols)
        val hOutlook = s(R.string.section_title_outlook)
        val hChecklist = s(R.string.section_title_checklist)
        val hQuant = s(R.string.section_title_quant)

        val jsonSpec = """
        At the very end, append ONE HTML comment with a compact JSON payload in ENGLISH keys:
        <!--JSON:{"kpi":{"positive":P,"neutral":N,"negative":M},"used":D}-->
        - P,N,M are numbers in percent (sum ~ 100).
        - D is the number of dreams actually analyzed (2-4).
        - Do NOT add code fences. Do NOT add extra comments.
    """.trimIndent()

        return buildString {
            appendLine(intro)
            appendLine()
            appendLine(langLine)
            appendLine()
            appendLine("Use EXACTLY these section headings in this order (no extra or missing):")
            appendLine("- $hMain")
            appendLine("- $hSummary")
            appendLine("- $hPattern")
            appendLine("- $hSymbols")
            appendLine("- $hOutlook")
            appendLine("- $hChecklist")
            appendLine("- $hQuant")
            appendLine()
            appendLine(rules)
            appendLine()
            appendLine("Write in HTML (<p>,<ul>,<li>), no Markdown code blocks.")
            appendLine(jsonSpec)
        }
    }

    private fun buildProPrompt(
        dreams: List<String>,
        interps: List<String>
    ): String {
        val intro = s(R.string.week_prompt_intro)
        val rules = s(R.string.week_prompt_rules)
        val langLine = try {
            s(R.string.week_prompt_lang_line, currentPromptLanguage())
        } catch (_: Exception) {
            "Answer in ${currentPromptLanguage()} only."
        }

        return buildString {
            appendLine(intro)
            appendLine(langLine)
            appendLine(rules)
            appendLine()
            dreams.forEachIndexed { i, text ->
                appendLine("[Dream ${i + 1}] $text")
            }
            interps.forEachIndexed { i, text ->
                appendLine("[Interpretation ${i + 1}] $text")
            }
            appendLine()
            appendLine("Use the above 2-4 items ONLY. Base KPIs strictly on these dreams.")
        }
    }

    // Fragment 의 sanitizeModelHtml 간단 버전
    private fun sanitizeModelHtml(raw: String): String =
        raw.trim()
            .replace(Regex("^\\s*```(?:\\w+)?\\s*"), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .replace(Regex("&nbsp;+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?is)</?xliff:g[^>]*>"), "")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")

    private data class ProMetrics(
        val pos: Float,
        val neu: Float,
        val neg: Float,
        val used: Int
    )

    /** <!--JSON:{...}--> 형식의 주석에서 KPI 추출 */
    private fun parseProMetricsFromHtml(html: String): ProMetrics? {
        val m = Regex(
            pattern = "<!--\\s*JSON\\s*:\\s*(\\{.*?\\})\\s*-->",
            option = RegexOption.IGNORE_CASE
        ).find(html) ?: return null

        return try {
            val obj = JSONObject(m.groupValues[1])
            val kpi = obj.getJSONObject("kpi")
            val p = kpi.getDouble("positive").toFloat()
            val n = kpi.getDouble("neutral").toFloat()
            val g = kpi.getDouble("negative").toFloat()
            val used = obj.optInt("used", 0)

            if (p < 0 || n < 0 || g < 0) null
            else ProMetrics(p, n, g, used)
        } catch (_: JSONException) {
            null
        }
    }

    fun onChartInfoClicked() {
        val msg = s(
            R.string.chart_info_message,
            MIN_ENTRIES_FOR_REPORT,
            MIN_ENTRIES_FOR_REPORT + 1,
            4
        ).trimIndent()

        setState {
            it.copy(
                showChartInfoDialog = true,
                chartInfoMessage = msg
            )
        }
    }

    fun onChartInfoDialogDismiss() {
        setState { it.copy(showChartInfoDialog = false) }
    }
}

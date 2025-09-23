package com.example.dreamindream

import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.example.dreamindream.databinding.ActivityTermsBinding
import com.google.android.material.color.MaterialColors

class TermsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTermsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTermsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        // 기본 탭
        binding.toggleGroup.check(binding.btnTermsTab.id)

        // 가독성 옵션
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // <-- 여기만 사용 (LineBreaker). O~P는 미설정으로 둠 → 컴파일/런타임 모두 안전
            binding.tvTerms.justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.tvTerms.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
        }

        setBody(isTerms = true)
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) setBody(isTerms = checkedId == binding.btnTermsTab.id)
        }
    }

    private fun setBody(isTerms: Boolean) {
        binding.tvTerms.text = if (isTerms) buildTerms() else buildPrivacy()
    }

    // ---------- 스타일 헬퍼 ----------
    private fun SpannableStringBuilder.h1(text: String) {
        inSpans(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.15f)) { append(text).append("\n") }
    }
    private fun SpannableStringBuilder.h2(text: String) {
        inSpans(StyleSpan(Typeface.BOLD)) { append(text).append("\n") }
    }
    private fun SpannableStringBuilder.p(text: String) { append(text).append("\n\n") }
    private fun SpannableStringBuilder.bullets(items: List<String>) {
        val color = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        items.forEach {
            val start = length
            append(it).append("\n")
            setSpan(BulletSpan(18, color, 8), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        append("\n")
    }

    // ---------- 내용 ----------
    private fun buildTerms() = buildSpannedString {
        h1("DreamInDream 이용약관")
        h2("1. 목적")
        p("본 약관은 DreamInDream(이하 “서비스”) 이용과 관련하여 사용자와 운영자 사이의 권리·의무를 규정합니다.")
        h2("2. 계정 및 이용")
        bullets(listOf(
            "전연령 이용 가능합니다.",
            "사용자는 계정 정보의 정확성을 유지해야 합니다.",
            "서비스는 예고 없이 기능을 변경·중단할 수 있습니다."
        ))
        h2("3. 유료 기능 및 광고")
        bullets(listOf(
            "프리미엄(광고 제거 등) 기능은 별도 정책을 따릅니다.",
            "광고 노출이 포함될 수 있습니다."
        ))
        h2("4. 금지 행위")
        bullets(listOf(
            "불법·유해 정보 게시 및 타인의 권리 침해",
            "자동화 수집, 크롤링, 역컴파일·변조 등 비정상 사용"
        ))
        h2("5. 지식재산권"); p("서비스 및 제공 콘텐츠에 대한 권리는 운영자 또는 정당한 권리자에게 있습니다.")
        h2("6. 면책"); p("꿈 해석/운세 결과의 정확성과 효용은 보장되지 않습니다.")
        h2("7. 약관 변경"); p("중요 변경 시 앱 내 고지합니다.")
    }

    private fun buildPrivacy() = buildSpannedString {
        h1("개인정보 처리방침")
        h2("1. 수집 항목")
        bullets(listOf(
            "필수: 이메일(또는 익명 식별자), 닉네임, 생년월일/성별, 사용 로그",
            "선택: MBTI, 출생시간, 프로필 이미지",
            "자동수집: 기기/앱 버전, 광고식별자, FCM 토큰"
        ))
        h2("2. 이용 목적")
        bullets(listOf("꿈 기록/분석 제공 및 알림 발송", "품질 개선, 고객문의 대응, 부정사용 방지"))
        h2("3. 보관 기간"); p("탈퇴 시 지체 없이 파기하며, 법령상 보관 예외를 준수합니다.")
        h2("4. 처리위탁/국외이전"); p("클라우드 사업자에 한정하여 위탁·국외 이전이 있을 수 있습니다.")
        h2("5. 이용자 권리"); p("개인정보 열람·정정·삭제·처리정지 요구가 가능합니다.")
        h2("6. 안전성 확보 조치"); bullets(listOf("HTTPS 통신, 접근권한 최소화, 접근기록 관리 등"))
        h2("문의"); p("dreamindream@dreamindream.app")
    }
}

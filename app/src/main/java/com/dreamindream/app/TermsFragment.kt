package com.dreamindream.app

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
import com.dreamindream.app.databinding.ActivityTermsBinding

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
        inSpans(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.15f)) {
            append(text).append("\n")
        }
    }
    private fun SpannableStringBuilder.h2(text: String) {
        inSpans(StyleSpan(Typeface.BOLD)) {
            append(text).append("\n")
        }
    }
    private fun SpannableStringBuilder.p(text: String) {
        append(text).append("\n\n")
    }

    private fun SpannableStringBuilder.bullets(items: List<String>) {
        // ✅ attr/Material R 전혀 사용 안 함 — 현재 텍스트 색으로 통일
        val bulletColor = binding.tvTerms.currentTextColor

        items.forEach {
            val start = length
            append(it).append("\n")
            setSpan(
                BulletSpan(18, bulletColor, 8),
                start,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        append("\n")
    }

    // ---------- 약관 ----------
    private fun buildTerms() = buildSpannedString {
        h1(getString(R.string.terms_title))
        h2(getString(R.string.terms_section1)); p(getString(R.string.terms_section1_body))
        h2(getString(R.string.terms_section2))
        bullets(resources.getStringArray(R.array.terms_section2_items).toList())
        h2(getString(R.string.terms_section3))
        bullets(resources.getStringArray(R.array.terms_section3_items).toList())
        h2(getString(R.string.terms_section4))
        bullets(resources.getStringArray(R.array.terms_section4_items).toList())
        h2(getString(R.string.terms_section5)); p(getString(R.string.terms_section5_body))
        h2(getString(R.string.terms_section6)); p(getString(R.string.terms_section6_body))
        h2(getString(R.string.terms_section7)); p(getString(R.string.terms_section7_body))
    }

    // ---------- 개인정보 처리방침 ----------
    private fun buildPrivacy() = buildSpannedString {
        h1(getString(R.string.privacy_title))
        p(getString(R.string.privacy_intro))
        h2(getString(R.string.privacy_section1))
        bullets(resources.getStringArray(R.array.privacy_section1_items).toList())
        h2(getString(R.string.privacy_section2))
        bullets(resources.getStringArray(R.array.privacy_section2_items).toList())
        h2(getString(R.string.privacy_section3)); p(getString(R.string.privacy_section3_body))
        h2(getString(R.string.privacy_section4)); p(getString(R.string.privacy_section4_body))
        h2(getString(R.string.privacy_section5)); p(getString(R.string.privacy_section5_body))
        h2(getString(R.string.privacy_section6))
        bullets(resources.getStringArray(R.array.privacy_section6_items).toList())
    }
}

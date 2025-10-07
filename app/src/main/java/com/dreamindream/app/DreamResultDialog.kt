package com.dreamindream.app

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView

class DreamResultDialog(
    context: Context,
    private val resultText: String
) : Dialog(context, R.style.TransparentDialog) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dream_result_dialog, null)
        setContentView(view)

        val resultView = view.findViewById<TextView>(R.id.resultTextView)
        val closeBtn = view.findViewById<ImageButton>(R.id.btn_close)

        resultView.text = resultText.replace("\n", "\n\n")
        closeBtn.setOnClickListener { dismiss() }

        // 창 크기 및 투명한 배경
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}

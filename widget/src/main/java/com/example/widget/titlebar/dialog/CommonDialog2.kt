package com.example.widget.titlebar.dialog

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.widget.databinding.CommonDialogFragmentBinding
import com.example.widget.titlebar.DensityUtils

class CommonDialog2 : DialogFragment() {
    private var negativeClick: (() -> (Unit))? = null
    private var positiveClick: (() -> (Unit))? = null

    private var title: CharSequence? = null
    private var content: CharSequence? = null
    private var negativeBtnText: String? = null
    private var positiveBtnText: String? = null
    private var binding: CommonDialogFragmentBinding? = null

    companion object {
        private const val TAG = "CommonDialog2"

        fun show(
            fragmentManager: FragmentManager,
            tag: String = "",
            title: String = "温馨提示",
            content: String = "弹窗内容",
            negativeBtnText: String = "取消",
            positionBtnText: String = "确定",
            negativeClick: (() -> (Unit))? = null,
            positiveClick: (() -> (Unit))? = null,
        ) {
            val dialog = CommonDialog2()
            dialog.negativeClick = negativeClick
            dialog.negativeBtnText = negativeBtnText
            dialog.positiveClick = positiveClick
            dialog.positiveBtnText = positionBtnText
            dialog.title = title
            dialog.content = content
            val ft = fragmentManager.beginTransaction()
            ft.add(dialog, tag)
            ft.commitAllowingStateLoss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            this.dismissAllowingStateLoss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.apply {
            AnimConfig.initShowAnim(AnimConfig.ANIM_TYPE_CENTER, this)
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = CommonDialogFragmentBinding.inflate(inflater)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.apply {
            tvTitle.text = title
            tvContent.visibility = if (content.isNullOrBlank()) View.GONE else View.VISIBLE
            tvContent.text = content
            btnCancel.text = negativeBtnText
            btnConfirm.text = positiveBtnText

            btnCancel.setOnClickListener {
                negativeClick?.invoke()
                dismissAllowingStateLoss()
            }

            btnConfirm.setOnClickListener {
                positiveClick?.invoke()
                dismissAllowingStateLoss()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding?.unbind()
        binding = null
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(
            DensityUtils.dp2px(requireContext(), 327f),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun dismiss() {
        super.dismiss()
        dialog?.let {
            it.window?.let { window ->
                AnimConfig.initHideAnim(AnimConfig.ANIM_TYPE_CENTER, window, this)
            }
        }
    }

    private fun showAllowingStateLoss(manager: FragmentManager?, tag: String) {
        manager?.let {
            if (tag.isNotBlank()) {
                kotlin.runCatching {
                    (manager.findFragmentByTag(tag) as? DialogFragment)?.dismissAllowingStateLoss()
                }
            }
            try {
                val dismissed = DialogFragment::class.java.getDeclaredField("mDismissed")
                dismissed.isAccessible = true
                val showByMe = DialogFragment::class.java.getDeclaredField("mShownByMe")
                showByMe.isAccessible = true
                val ft = manager.beginTransaction()
                ft.add(this, tag)
                ft.commitAllowingStateLoss()
            } catch (e: Exception) {
                Log.e(TAG, "${e.toString()}")
            }
        }
    }
}
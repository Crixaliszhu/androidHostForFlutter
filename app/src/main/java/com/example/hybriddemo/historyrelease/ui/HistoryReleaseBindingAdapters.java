package com.example.hybriddemo.historyrelease.ui;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.databinding.BindingAdapter;

import com.example.hybriddemo.R;

public final class HistoryReleaseBindingAdapters {

    private HistoryReleaseBindingAdapters() {
    }

    @BindingAdapter("visibleGone")
    public static void bindVisibleGone(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @BindingAdapter("historyReleaseStatusText")
    public static void bindHistoryReleaseStatusText(TextView view, CharSequence statusText) {
        String text = statusText == null ? "" : statusText.toString();
        view.setText(text);
        boolean valid = text.contains("招工中");
        view.setTextColor(
                ContextCompat.getColor(
                        view.getContext(),
                        valid ? R.color.history_release_status_green : R.color.history_release_status_gray
                )
        );
        view.setBackground(
                ContextCompat.getDrawable(
                        view.getContext(),
                        valid ? R.drawable.bg_history_release_status_valid : R.drawable.bg_history_release_status_invalid
                )
        );
    }
}

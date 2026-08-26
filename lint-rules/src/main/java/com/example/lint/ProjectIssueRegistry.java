package com.example.lint;

import static com.android.tools.lint.detector.api.ApiKt.CURRENT_API;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.Vendor;
import com.android.tools.lint.detector.api.Issue;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ProjectIssueRegistry extends IssueRegistry {
    @NotNull
    @Override
    public Vendor getVendor() {
        return new Vendor("FlutterHybridDemo", "com.example.lint.project");
    }

    @Override
    public int getApi() {
        return CURRENT_API;
    }

    @Override
    public int getMinApi() {
        return 8;
    }

    @NotNull
    @Override
    public List<Issue> getIssues() {
        return Collections.singletonList(DataClassKeepAndNullableDetector.ISSUE);
    }
}

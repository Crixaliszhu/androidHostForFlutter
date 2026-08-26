package com.example.lint;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtNullableType;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtPrimaryConstructor;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtTypeReference;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

public class DataClassKeepAndNullableDetector extends Detector implements SourceCodeScanner {
    private static final Implementation IMPLEMENTATION = new Implementation(
            DataClassKeepAndNullableDetector.class,
            Scope.JAVA_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "ProjectDataClassContract",
            "Data class must use nullable properties and @Keep",
            "Project data classes are often used as serialized models or cross-module contracts. " +
                    "Keep them stable for shrinkers and schema evolution by annotating each data class " +
                    "with @Keep and declaring every property as nullable.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                checkClass(context, node);
            }
        };
    }

    private static void checkClass(JavaContext context, UClass uClass) {
        if (!(uClass.getSourcePsi() instanceof KtClass)) {
            return;
        }

        KtClass ktClass = (KtClass) uClass.getSourcePsi();
        if (!ktClass.isData()) {
            return;
        }

        if (!hasKeepAnnotation(uClass, ktClass)) {
            context.report(
                    ISSUE,
                    uClass,
                    context.getNameLocation(uClass),
                    "Data class must be annotated with @Keep."
            );
        }

        KtPrimaryConstructor primaryConstructor = ktClass.getPrimaryConstructor();
        if (primaryConstructor != null) {
            for (KtParameter parameter : primaryConstructor.getValueParameters()) {
                if (parameter.hasValOrVar() && !hasNullableType(parameter.getTypeReference())) {
                    context.report(
                            ISSUE,
                            uClass,
                            context.getNameLocation(uClass),
                            "Data class property `" + parameter.getName() + "` must be nullable."
                    );
                }
            }
        }

        for (KtDeclaration declaration : ktClass.getDeclarations()) {
            if (declaration instanceof KtProperty) {
                KtProperty property = (KtProperty) declaration;
                if (!hasNullableType(property.getTypeReference())) {
                    context.report(
                            ISSUE,
                            uClass,
                            context.getNameLocation(uClass),
                            "Data class property `" + property.getName() + "` must be nullable."
                    );
                }
            }
        }
    }

    private static boolean hasNullableType(KtTypeReference typeReference) {
        return typeReference != null && typeReference.getTypeElement() instanceof KtNullableType;
    }

    private static boolean hasKeepAnnotation(UClass uClass, KtClass ktClass) {
        for (UAnnotation annotation : uClass.getUAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if ("androidx.annotation.Keep".equals(qualifiedName)
                    || "android.support.annotation.Keep".equals(qualifiedName)) {
                return true;
            }
        }

        for (KtAnnotationEntry annotationEntry : ktClass.getAnnotationEntries()) {
            if (annotationEntry.getShortName() != null
                    && "Keep".equals(annotationEntry.getShortName().asString())) {
                return true;
            }

            KtTypeReference typeReference = annotationEntry.getTypeReference();
            if (typeReference != null) {
                String annotationText = typeReference.getText();
                if ("Keep".equals(annotationText)
                        || "androidx.annotation.Keep".equals(annotationText)
                        || "android.support.annotation.Keep".equals(annotationText)) {
                    return true;
                }
            }
        }

        return false;
    }
}

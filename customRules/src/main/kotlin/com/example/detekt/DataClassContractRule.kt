package com.example.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference

class DataClassContractRule(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "DataClassContract",
        severity = Severity.Defect,
        description = "Data class properties must be nullable and data classes must be annotated with @Keep.",
        debt = Debt.TWENTY_MINS
    )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        if (!klass.isData()) {
            return
        }

        if (!klass.hasKeepAnnotation()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass.getClassKeyword() ?: klass),
                    "Data class must be annotated with @Keep."
                )
            )
        }

        klass.primaryConstructor?.valueParameters
            ?.filter { it.hasValOrVar() }
            ?.forEach { parameter ->
                if (!parameter.typeReference.isNullable()) {
                    reportNonNullableProperty(parameter, parameter.name)
                }
            }

        klass.getBody()?.properties?.forEach { property ->
            if (!property.typeReference.isNullable()) {
                reportNonNullableProperty(property, property.name)
            }
        }
    }

    private fun reportNonNullableProperty(entity: KtParameter, propertyName: String?) {
        report(
            CodeSmell(
                issue,
                Entity.from(entity.typeReference ?: entity),
                "Data class property `${propertyName ?: entity.text}` must be nullable."
            )
        )
    }

    private fun reportNonNullableProperty(entity: KtProperty, propertyName: String?) {
        report(
            CodeSmell(
                issue,
                Entity.from(entity.typeReference ?: entity),
                "Data class property `${propertyName ?: entity.text}` must be nullable."
            )
        )
    }

    private fun KtTypeReference?.isNullable(): Boolean {
        return this?.typeElement is KtNullableType
    }

    private fun KtClass.hasKeepAnnotation(): Boolean {
        return annotationEntries.any { it.isKeepAnnotation() }
    }

    private fun KtAnnotationEntry.isKeepAnnotation(): Boolean {
        val shortName = shortName?.asString()
        val typeText = typeReference?.text
        return shortName == "Keep" ||
            typeText == "Keep" ||
            typeText == "androidx.annotation.Keep" ||
            typeText == "android.support.annotation.Keep"
    }
}

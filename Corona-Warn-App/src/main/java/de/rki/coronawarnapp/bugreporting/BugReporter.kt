package de.rki.coronawarnapp.bugreporting

import de.rki.coronawarnapp.util.CWADebug
import de.rki.coronawarnapp.util.di.AppInjector

interface BugReporter {
    fun report(throwable: Throwable, tag: String? = null, info: String? = null)
}

fun Throwable.reportProblem(tag: String? = null, info: String? = null) {
    if (CWADebug.isAUnitTest) return
    val reporter = AppInjector.component.bugReporter
    reporter.report(this, tag, info)
}

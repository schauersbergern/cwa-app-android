package de.rki.coronawarnapp.eventregistration.checkins

import de.rki.coronawarnapp.server.protocols.internal.pt.CheckInOuterClass

interface CheckInsTransformer {
    fun transform(checkIns: List<CheckIn>): List<CheckInOuterClass.CheckIn>
}
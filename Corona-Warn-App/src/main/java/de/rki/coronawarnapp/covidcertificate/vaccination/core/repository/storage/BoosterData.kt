package de.rki.coronawarnapp.covidcertificate.vaccination.core.repository.storage

import com.google.gson.annotations.SerializedName
import de.rki.coronawarnapp.covidcertificate.common.certificate.CertificatePersonIdentifier
import org.joda.time.Instant

data class BoosterData(

    @SerializedName("personIdentifierCode")
    val personIdentifierCode: String? = null,

    @SerializedName("lastSeenBoosterRuleIdentifier")
    val lastSeenBoosterRuleIdentifier: String? = null,

    @SerializedName("lastBoosterNotifiedAt")
    val lastBoosterNotifiedAt: Instant? = null,
)

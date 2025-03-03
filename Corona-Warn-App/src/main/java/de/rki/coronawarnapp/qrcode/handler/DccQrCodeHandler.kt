package de.rki.coronawarnapp.qrcode.handler

import de.rki.coronawarnapp.covidcertificate.common.exception.InvalidHealthCertificateException
import de.rki.coronawarnapp.covidcertificate.common.qrcode.DccQrCode
import de.rki.coronawarnapp.covidcertificate.common.repository.CertificateContainerId
import de.rki.coronawarnapp.covidcertificate.recovery.core.RecoveryCertificateRepository
import de.rki.coronawarnapp.covidcertificate.recovery.core.qrcode.RecoveryCertificateQRCode
import de.rki.coronawarnapp.covidcertificate.signature.core.DscSignatureValidator
import de.rki.coronawarnapp.covidcertificate.test.core.TestCertificateRepository
import de.rki.coronawarnapp.covidcertificate.test.core.qrcode.TestCertificateQRCode
import de.rki.coronawarnapp.covidcertificate.vaccination.core.qrcode.VaccinationCertificateQRCode
import de.rki.coronawarnapp.covidcertificate.vaccination.core.repository.VaccinationCertificateRepository
import de.rki.coronawarnapp.qrcode.scanner.UnsupportedQrCodeException
import javax.inject.Inject

class DccQrCodeHandler @Inject constructor(
    private val vaccinationCertificateRepository: VaccinationCertificateRepository,
    private val testCertificateRepository: TestCertificateRepository,
    private val recoveryCertificateRepository: RecoveryCertificateRepository,
    private val dscSignatureValidator: DscSignatureValidator,
) {

    /**
     * Saves [DccQrCode] in the respective repository after validating the signature
     * throws [InvalidHealthCertificateException]
     */
    suspend fun handleQrCode(dccQrCode: DccQrCode): CertificateContainerId {
        dscSignatureValidator.validateSignature(dccData = dccQrCode.data)
        return when (dccQrCode) {
            is RecoveryCertificateQRCode ->
                recoveryCertificateRepository.registerCertificate(dccQrCode).containerId
            is VaccinationCertificateQRCode ->
                vaccinationCertificateRepository.registerCertificate(dccQrCode).containerId
            is TestCertificateQRCode ->
                testCertificateRepository.registerCertificate(dccQrCode).containerId
            else -> throw UnsupportedQrCodeException()
        }
    }
}

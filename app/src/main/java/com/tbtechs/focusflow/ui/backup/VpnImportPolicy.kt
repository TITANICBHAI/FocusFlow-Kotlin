package com.tbtechs.focusflow.ui.backup

internal enum class VpnImportConsentDecision {
    NOT_REQUIRED,
    REQUEST_CONSENT,
    PERMISSION_ALREADY_GRANTED,
}

internal object VpnImportPolicy {
    fun consentDecision(
        restoreSettings: Boolean,
        importedVpnPackageCount: Int,
        networkBlockEnabled: Boolean,
        vpnPermissionGranted: Boolean,
    ): VpnImportConsentDecision {
        if (!restoreSettings || importedVpnPackageCount <= 0 || networkBlockEnabled) {
            return VpnImportConsentDecision.NOT_REQUIRED
        }
        return if (vpnPermissionGranted) {
            VpnImportConsentDecision.PERMISSION_ALREADY_GRANTED
        } else {
            VpnImportConsentDecision.REQUEST_CONSENT
        }
    }

    fun shouldActivateAfterConsent(
        decision: VpnImportConsentDecision,
        vpnPermissionGranted: Boolean,
    ): Boolean = when (decision) {
        VpnImportConsentDecision.NOT_REQUIRED -> false
        VpnImportConsentDecision.REQUEST_CONSENT -> vpnPermissionGranted
        VpnImportConsentDecision.PERMISSION_ALREADY_GRANTED -> true
    }

    fun shouldActivateImportedVpnBlock(
        restoreSettings: Boolean,
        decision: VpnImportConsentDecision,
        vpnPermissionGranted: Boolean,
    ): Boolean =
        restoreSettings && shouldActivateAfterConsent(decision, vpnPermissionGranted)

    fun shouldActivateImportedVpnBlock(
        restoreSettings: Boolean,
        activationRequested: Boolean,
    ): Boolean = restoreSettings && activationRequested
}

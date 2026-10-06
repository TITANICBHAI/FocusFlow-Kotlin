package com.tbtechs.focusflow.ui.backup

internal enum class VpnImportConsentDecision {
    NOT_REQUIRED,
    REQUEST_CONSENT,
    PERMISSION_ALREADY_GRANTED,
}

internal data class VpnImportNotice(
    val importedAppCount: Int,
    val permissionGranted: Boolean,
)

internal object VpnImportPolicy {
    /**
     * What the review screen tells the user before they tap Import. It is derived from
     * [consentDecision], so the notice and the real activation can never disagree.
     */
    fun notice(
        restoreSettings: Boolean,
        importedVpnPackageCount: Int,
        networkBlockEnabled: Boolean,
        vpnPermissionGranted: Boolean,
    ): VpnImportNotice? {
        val decision = consentDecision(
            restoreSettings = restoreSettings,
            importedVpnPackageCount = importedVpnPackageCount,
            networkBlockEnabled = networkBlockEnabled,
            vpnPermissionGranted = vpnPermissionGranted,
        )
        return if (decision == VpnImportConsentDecision.NOT_REQUIRED) {
            null
        } else {
            VpnImportNotice(importedVpnPackageCount, vpnPermissionGranted)
        }
    }

    fun noticeText(notice: VpnImportNotice): String {
        val apps = if (notice.importedAppCount == 1) "1 VPN-blocked app" else "${notice.importedAppCount} VPN-blocked apps"
        val base = "This backup includes $apps. Importing will turn on Network Blocking for " +
            if (notice.importedAppCount == 1) "it." else "them."
        return if (notice.permissionGranted) base else "$base Android will ask you to allow the VPN connection."
    }

    /** Result-dialog text for the "VPN list" protection card when it is not active. */
    fun inactiveDetails(count: Int, featureEnabled: Boolean, permissionAvailable: Boolean): String {
        val apps = if (count == 1) "1 app was" else "$count apps were"
        return when {
            count <= 0 -> "No entries were imported."
            !featureEnabled -> "$apps imported, but Network Blocking is off. Turn it on in Defense."
            !permissionAvailable ->
                "$apps imported, but Android VPN permission isn't granted. Allow it from Defense."
            else -> "$apps imported; enforcement is inactive."
        }
    }

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

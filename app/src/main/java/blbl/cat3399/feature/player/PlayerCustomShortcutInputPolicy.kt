package blbl.cat3399.feature.player

internal object PlayerCustomShortcutInputPolicy {
    /**
     * Interactive overlays own the input context. Custom shortcuts only run when the
     * player surface itself is the active target.
     */
    fun canDispatchInVod(
        hasInteractiveOsd: Boolean,
        hasSidePanel: Boolean,
        hasBottomCardPanel: Boolean,
    ): Boolean {
        return !hasInteractiveOsd && !hasSidePanel && !hasBottomCardPanel
    }

    fun canDispatchInLive(
        hasInteractiveOsd: Boolean,
        hasSettingsPanel: Boolean,
    ): Boolean {
        return !hasInteractiveOsd && !hasSettingsPanel
    }
}

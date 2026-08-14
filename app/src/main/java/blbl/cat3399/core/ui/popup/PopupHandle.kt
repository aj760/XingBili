package blbl.cat3399.core.ui.popup

interface PopupHandle {
    val isShowing: Boolean

    fun dismiss()
}

enum class PopupActionRole {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
}

data class PopupAction(
    val role: PopupActionRole,
    val text: CharSequence,
    val dismissOnClick: Boolean = true,
    val onClick: (() -> Unit)? = null,
)


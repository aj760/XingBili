package blbl.cat3399.ui

/**
 * Host ability for main page fragments to request entering the left sidebar.
 *
 * Why an interface:
 * - Keeps feature fragments decoupled from a concrete [MainActivity] type.
 * - Avoids exposing internal/private focus helpers as public APIs.
 */
interface SidebarFocusHost {
    /**
     * Request focus to move to the currently selected sidebar navigation item.
     *
     * The host may post the focus request (async) to wait for layout.
     */
    fun requestFocusSidebarSelectedNav(): Boolean
}


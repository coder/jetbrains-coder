package com.coder.gateway.views

import com.jetbrains.toolbox.api.core.ui.icons.SvgIcon
import com.jetbrains.toolbox.api.ui.actions.RunnableActionDescription
import com.jetbrains.toolbox.api.ui.components.UiField
import com.jetbrains.toolbox.api.ui.components.UiPage
import com.jetbrains.toolbox.api.ui.components.ValidationErrorField
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/**
 * Base page that handles the icon, displaying error notifications, and
 * getting field values.
 *
 * Note that it seems only the first page displays the icon, even if we
 * return an icon for every page.
 *
 * TODO: Any way to get the return key working for fields?  Right now you have
 *       to use the mouse.
 */
abstract class CoderPage(
    private val showIcon: Boolean = true,
) : UiPage {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * An error to display on the page.
     *
     * The current assumption is you only have one field per page.
     */
    protected var errorField: ValidationErrorField? = null

    /** Toolbox uses this to show notifications on the page. */
    private var notifier: Consumer<Throwable>? = null

    /** Let Toolbox know the fields should be updated. */
    protected var listener: Consumer<UiField?>? = null

    /** Stores errors until the notifier is attached. */
    private var errorBuffer: MutableList<Throwable> = mutableListOf()

    /**
     * Return the icon, if showing one.
     *
     * This seems to only work on the first page.
     */
    override fun getSvgIcon(): SvgIcon {
        return if (showIcon) {
            SvgIcon(this::class.java.getResourceAsStream("/icon.svg")?.readAllBytes() ?: byteArrayOf())
        } else {
            SvgIcon(byteArrayOf())
        }
    }

    /**
     * Show an error as a popup on this page.
     */
    fun notify(logPrefix: String, ex: Throwable) {
        logger.error(logPrefix, ex)
        // It is possible the error listener is not attached yet.
        notifier?.accept(ex) ?: errorBuffer.add(ex)
    }

    /**
     * Immediately notify any pending errors and store for later errors.
     */
    override fun setActionErrorNotifier(notifier: Consumer<Throwable>?) {
        this.notifier = notifier
        notifier?.let {
            errorBuffer.forEach {
                notifier.accept(it)
            }
            errorBuffer.clear()
        }
    }

    /**
     * Set/unset the field error and update the form.
     */
    protected fun updateError(error: String?) {
        errorField = error?.let { ValidationErrorField(error) }
        listener?.accept(null) // Make Toolbox get the fields again.
    }
}

/**
 * An action that simply runs the provided callback.
 */
class Action(
    private val label: String,
    private val closesPage: Boolean = false,
    private val enabled: () -> Boolean = { true },
    private val cb: () -> Unit,
) : RunnableActionDescription {
    override fun getLabel(): String = label
    override fun getShouldClosePage(): Boolean = closesPage
    override fun isEnabled(): Boolean = enabled()
    override fun run() {
        cb()
    }
}

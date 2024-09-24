package com.coder.gateway.views

import com.jetbrains.toolbox.gateway.ui.RunnableActionDescription
import com.jetbrains.toolbox.gateway.ui.UiField
import com.jetbrains.toolbox.gateway.ui.UiPage
import com.jetbrains.toolbox.gateway.ui.ValidationErrorField
import org.slf4j.LoggerFactory
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

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

    /** Used to get field values. */
    private var getter: Function<UiField, *>? = null

    /** Let Toolbox know the fields should be updated. */
    protected var listener: Consumer<UiField?>? = null

    /** Stores errors until the notifier is attached. */
    private var errorBuffer: MutableList<Throwable> = mutableListOf()

    /**
     * Return the icon, if showing one.
     *
     * This seems to only work on the first page.
     */
    override fun getSvgIcon(): ByteArray =
        if (showIcon) {
            this::class.java.getResourceAsStream("/icon.svg")?.readAllBytes() ?: byteArrayOf()
        } else {
            byteArrayOf()
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
     * Get the value for a field.
     *
     * TODO@JB: Is this really meant to be used with casting?  I kind of expected
     *          to be able to do `myField.value`.
     */
    fun get(field: UiField): Any {
        val getter = getter ?: throw Exception("Page is not being displayed")
        return getter.apply(field)
    }

    /**
     * Used to update fields when they change (like validation fields).
     */
    override fun setPageChangedListener(listener: Consumer<UiField?>) {
        this.listener = listener
    }

    /**
     * The setter is unused but the getter is used to get field values.
     */
    override fun setStateAccessor(setter: BiConsumer<UiField, Any>?, getter: Function<UiField, *>?) {
        this.getter = getter
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

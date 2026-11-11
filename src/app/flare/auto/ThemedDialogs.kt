package app.flare.auto

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

internal object ThemedDialogs {
    class Builder(private val activity: Activity) : AlertDialog.Builder(activity) {
        private var titleText: CharSequence? = null
        private var choices: Array<out CharSequence>? = null
        private var selected = -1
        private var choose: DialogInterface.OnClickListener? = null
        private val buttons = LinkedHashMap<Int, Pair<CharSequence?, DialogInterface.OnClickListener?>>()
        private var cancelable = true
        private var cancelListener: DialogInterface.OnCancelListener? = null
        private var dismissListener: DialogInterface.OnDismissListener? = null

        override fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder {
            buttons[DialogInterface.BUTTON_POSITIVE] = text to listener
            super.setPositiveButton(text, listener); return this
        }
        override fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder {
            buttons[DialogInterface.BUTTON_NEGATIVE] = text to listener
            super.setNegativeButton(text, listener); return this
        }
        override fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder {
            buttons[DialogInterface.BUTTON_NEUTRAL] = text to listener
            super.setNeutralButton(text, listener); return this
        }
        override fun setPositiveButton(textId: Int, listener: DialogInterface.OnClickListener?): Builder = setPositiveButton(activity.getText(textId), listener)
        override fun setNegativeButton(textId: Int, listener: DialogInterface.OnClickListener?): Builder = setNegativeButton(activity.getText(textId), listener)
        override fun setNeutralButton(textId: Int, listener: DialogInterface.OnClickListener?): Builder = setNeutralButton(activity.getText(textId), listener)
        override fun setCancelable(value: Boolean): Builder { cancelable = value; super.setCancelable(value); return this }
        override fun setOnCancelListener(listener: DialogInterface.OnCancelListener?): Builder { cancelListener = listener; super.setOnCancelListener(listener); return this }
        override fun setOnDismissListener(listener: DialogInterface.OnDismissListener?): Builder { dismissListener = listener; super.setOnDismissListener(listener); return this }

        override fun setTitle(title: CharSequence?): Builder {
            titleText = title
            super.setTitle(title)
            return this
        }

        override fun setItems(
            items: Array<out CharSequence>?,
            listener: DialogInterface.OnClickListener?
        ): Builder {
            choices = items
            choose = listener
            return this
        }

        override fun setSingleChoiceItems(
            items: Array<out CharSequence>?,
            checkedItem: Int,
            listener: DialogInterface.OnClickListener?
        ): Builder {
            choices = items
            selected = checkedItem
            choose = listener
            return this
        }

        override fun create(): AlertDialog {
            val dialog = if (choices != null) choiceDialog() else super.create()
            dialog.setOnShowListener { style(dialog) }
            return dialog
        }

        override fun show(): AlertDialog {
            val dialog = create()
            dialog.show()
            if (Motion.enabled(activity)) {
                dialog.window?.decorView?.let { view ->
                    view.alpha = 0f
                    view.scaleX = .95f
                    view.scaleY = .95f
                    view.translationY = activity.dp(10).toFloat()
                    view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(300)
                        .setInterpolator(android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f)).start()
                }
            }
            return dialog
        }

        private fun choiceDialog(): AlertDialog {
            val ui = UiKit(activity)
            val root = ui.column().apply {
                setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(12))
                setBackground(GlassDrawable(ui.t.background, ui.dp(16).toFloat(), ui.t.cardBorder))
            }
            val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            val back = ui.text("‹", 28, ui.t.textPrimary).apply { gravity = Gravity.CENTER }
            header.addView(back, LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)))
            val heading = ui.text(titleText?.toString() ?: "Select an option", 20, ui.t.textPrimary).apply {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            header.addView(heading, LinearLayout.LayoutParams(0, ui.dp(56), 1f))
            root.addView(header)
            val list = ListView(activity).apply { divider = null }
            val optionList = choices ?: emptyArray()
            val available = (activity.resources.displayMetrics.heightPixels /
                activity.resources.displayMetrics.density).toInt() - 120 - if (buttons.isEmpty()) 0 else 56
            root.addView(
                list,
                LinearLayout.LayoutParams(MATCH, ui.dp(minOf(maxOf(56, available), optionList.size * 64)))
            )
            val builder = AlertDialog.Builder(activity).setView(root).setCancelable(cancelable)
                .setOnCancelListener(cancelListener).setOnDismissListener(dismissListener)
            buttons.forEach { (id, option) -> when (id) {
                DialogInterface.BUTTON_POSITIVE -> builder.setPositiveButton(option.first, option.second)
                DialogInterface.BUTTON_NEGATIVE -> builder.setNegativeButton(option.first, option.second)
                DialogInterface.BUTTON_NEUTRAL -> builder.setNeutralButton(option.first, option.second)
            } }
            val dialog = builder.create()
            back.setOnClickListener { dialog.dismiss() }
            list.adapter = object : BaseAdapter() {
                override fun getCount() = optionList.size
                override fun getItem(position: Int): Any = optionList[position]
                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                    val row = recycled as? UiKit.ValueRow ?: ui.valueRow("", "", null)
                    row.title.text = optionList[position]
                    val mark = row.value
                    mark.text = if (position == selected) "✓" else ""
                    mark.setTextColor(ui.t.accent)
                    mark.tag = UiKit.Role.ACCENT
                    row.setBackground(
                        GlassDrawable(
                            ui.t.card,
                            ui.dp(10).toFloat(),
                            if (position == selected) ui.t.accent else ui.t.cardBorder
                        )
                    )
                    row.layoutParams = AbsListView.LayoutParams(MATCH, ui.dp(64))
                    return row
                }
            }
            list.setOnItemClickListener { _, _, position, _ ->
                dialog.dismiss()
                choose?.onClick(dialog, position)
            }
            return dialog
        }

        private fun style(dialog: AlertDialog) {
            val theme = ThemeTokens.get(activity)
            val window: Window = dialog.window ?: return
            val background = GradientDrawable().apply {
                setColor(theme.card)
                cornerRadius = activity.dp(16).toFloat()
            }
            window.setBackgroundDrawable(background)
            window.immersive()
            val width = minOf(activity.resources.displayMetrics.widthPixels - activity.dp(40), activity.dp(680))
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            tint(window.decorView, theme)
            for (id in intArrayOf(DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL)) {
                dialog.getButton(id)?.let {
                    it.setTextColor(theme.accent)
                    it.minHeight = activity.dp(56)
                }
            }
        }

        private fun tint(view: View, theme: ThemeTokens) {
            if (view is TextView) {
                view.setTextColor(
                    when (view.tag) {
                        UiKit.Role.ACCENT -> theme.accent
                        UiKit.Role.SECONDARY -> theme.textSecondary
                        else -> theme.textPrimary
                    }
                )
                if (view is EditText) {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(theme.accent)
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) tint(view.getChildAt(index), theme)
            }
        }
    }
}

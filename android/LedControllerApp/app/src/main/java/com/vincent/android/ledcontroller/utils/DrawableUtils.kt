package com.vincent.android.ledcontroller.utils

import android.R
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Drawable工具类
 * 用于动态设置View的drawableLeft、drawableRight、drawableTop、drawableBottom
 */
object DrawableUtils {

    const val DEFAULT_SIZE = 12
    /**
     * 设置View的drawableLeft
     * @param view 目标View
     * @param drawable Drawable对象
     * @param size 图标大小，单位dp
     * @param tintColor 着色颜色，null表示不着色
     */
    fun setDrawableLeft(
        view: View,
        drawable: Drawable?,
        size: Int = DEFAULT_SIZE,
        tintColor: Int? = null
    ) {
        setDrawable(view, drawable, size, tintColor, 0, 0, 0, 0, isLeft = true)
    }

    /**
     * 设置View的drawableRight
     * @param view 目标View
     * @param drawable Drawable对象
     * @param size 图标大小，单位dp
     * @param tintColor 着色颜色，null表示不着色
     */
    fun setDrawableRight(
        view: View,
        drawable: Drawable?,
        size: Int = DEFAULT_SIZE,
        tintColor: Int? = null
    ) {
        setDrawable(view, drawable, size, tintColor, 0, 0, 0, 0, isRight = true)
    }

    /**
     * 设置View的drawableTop
     * @param view 目标View
     * @param drawable Drawable对象
     * @param size 图标大小，单位dp
     * @param tintColor 着色颜色，null表示不着色
     */
    fun setDrawableTop(
        view: View,
        drawable: Drawable?,
        size: Int = DEFAULT_SIZE,
        tintColor: Int? = null
    ) {
        setDrawable(view, drawable, size, tintColor, 0, 0, 0, 0, isTop = true)
    }

    /**
     * 设置View的drawableBottom
     * @param view 目标View
     * @param drawable Drawable对象
     * @param size 图标大小，单位dp
     * @param tintColor 着色颜色，null表示不着色
     */
    fun setDrawableBottom(
        view: View,
        drawable: Drawable?,
        size: Int = DEFAULT_SIZE,
        tintColor: Int? = null
    ) {
        setDrawable(view, drawable, size, tintColor, 0, 0, 0, 0, isBottom = true)
    }

    /**
     * 通过资源ID设置drawableLeft
     * @param view 目标View
     * @param context Context
     * @param drawableRes 资源ID
     * @param size 图标大小，单位dp
     * @param tintColor 着色颜色，null表示不着色
     */
    fun setDrawableLeft(
        view: View,
        context: Context,
        @DrawableRes drawableRes: Int,
        size: Int = DEFAULT_SIZE,
        tintColor: Int? = null
    ) {
        val drawable = ContextCompat.getDrawable(context, drawableRes)
        setDrawableLeft(view, drawable, size, tintColor)
    }

    /**
     * 通过资源ID设置drawableRight
     * @param view 目标View
     * @param context Context
     * @param drawableRes 资源ID
     * @param size 图标大小，单位dp
     * @param tintColor 着色颜色，null表示不着色
     */
    fun setDrawableRight(
        view: View,
        context: Context,
        @DrawableRes drawableRes: Int,
        size: Int = DEFAULT_SIZE,
        tintColor: Int? = null
    ) {
        val drawable = ContextCompat.getDrawable(context, drawableRes)
        setDrawableRight(view, drawable, size, tintColor)
    }

    /**
     * 通过资源ID设置drawableTop
     * @param view 目标View
     * @param context Context
     * @param drawableRes 资源ID
     * @param size 图标大小，单位dp
     * @param tintColor 着色颜色，null表示不着色
     */
    fun setDrawableTop(
        view: View,
        context: Context,
        @DrawableRes drawableRes: Int,
        size: Int = DEFAULT_SIZE,
        tintColor: Int? = null
    ) {
        val drawable = ContextCompat.getDrawable(context, drawableRes)
        setDrawableTop(view, drawable, size, tintColor)
    }

    /**
     * 通过资源ID设置drawableBottom
     * @param view 目标View
     * @param context Context
     * @param drawableRes 资源ID
     * @param size 图标大小，单位dp
     * @param tintColor 着色颜色，null表示不着色
     */
    fun setDrawableBottom(
        view: View,
        context: Context,
        @DrawableRes drawableRes: Int,
        size: Int = DEFAULT_SIZE,
        tintColor: Int? = null
    ) {
        val drawable = ContextCompat.getDrawable(context, drawableRes)
        setDrawableBottom(view, drawable, size, tintColor)
    }

    /**
     * 创建圆形drawable
     * @param context Context
     * @param color 颜色
     * @param size 大小，单位dp
     * @return Drawable对象
     */
    fun createCircleDrawable(
        context: Context,
        @ColorRes color: Int,
        size: Int = DEFAULT_SIZE
    ): Drawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(ContextCompat.getColor(context, color))
        val sizePx = dpToPx(context, size)
        drawable.setSize(sizePx, sizePx)
        return drawable
    }

    /**
     * 创建圆角矩形drawable
     * @param context Context
     * @param color 颜色
     * @param cornerRadius 圆角半径，单位dp
     * @param width 宽度，单位dp
     * @param height 高度，单位dp
     * @return Drawable对象
     */
    fun createRoundRectDrawable(
        context: Context,
        @ColorRes color: Int,
        cornerRadius: Float = 8f,
        width: Int = DEFAULT_SIZE,
        height: Int = DEFAULT_SIZE
    ): Drawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.setColor(ContextCompat.getColor(context, color))
        drawable.cornerRadius = dpToPx(context, cornerRadius).toFloat()
        drawable.setSize(dpToPx(context, width), dpToPx(context, height))
        return drawable
    }

    /**
     * 创建状态选择器drawable
     * @param normalDrawable 正常状态drawable
     * @param pressedDrawable 按下状态drawable
     * @param selectedDrawable 选中状态drawable
     * @return StateListDrawable对象
     */
    fun createStateListDrawable(
        normalDrawable: Drawable?,
        pressedDrawable: Drawable? = null,
        selectedDrawable: Drawable? = null
    ): StateListDrawable {
        val stateListDrawable = StateListDrawable()

        normalDrawable?.let { stateListDrawable.addState(intArrayOf(), it) }
        pressedDrawable?.let { stateListDrawable.addState(intArrayOf(R.attr.state_pressed), it) }
        selectedDrawable?.let { stateListDrawable.addState(intArrayOf(R.attr.state_selected), it) }

        return stateListDrawable
    }

    /**
     * 清除View的所有drawable
     * @param view 目标View
     */
    fun clearDrawables(view: View) {
        when (view) {
            is TextView -> {
                view.setCompoundDrawables(null, null, null, null)
            }
            is Button -> {
                view.setCompoundDrawables(null, null, null, null)
            }
            is ImageButton -> {
                view.setImageDrawable(null)
            }
            is ImageView -> {
                view.setImageDrawable(null)
            }
        }
    }

    /**
     * 设置drawable的着色
     * @param drawable 目标drawable
     * @param color 颜色
     * @return 着色后的drawable
     */
    fun tintDrawable(drawable: Drawable, color: Int): Drawable {
        val wrappedDrawable = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(wrappedDrawable, color)
        return wrappedDrawable
    }

    /**
     * 核心方法：设置View的drawable
     * @param view 目标View
     * @param drawable Drawable对象
     * @param size 图标大小，单位dp
     * @param tintColor 着色颜色，null表示不着色
     * @param leftMargin 左边距，单位dp
     * @param topMargin 上边距，单位dp
     * @param rightMargin 右边距，单位dp
     * @param bottomMargin 下边距，单位dp
     * @param isLeft 是否设置左边
     * @param isRight 是否设置右边
     * @param isTop 是否设置上边
     * @param isBottom 是否设置下边
     */
    private fun setDrawable(
        view: View,
        drawable: Drawable?,
        size: Int,
        tintColor: Int?,
        leftMargin: Int,
        topMargin: Int,
        rightMargin: Int,
        bottomMargin: Int,
        isLeft: Boolean = false,
        isRight: Boolean = false,
        isTop: Boolean = false,
        isBottom: Boolean = false
    ) {
        if (drawable == null) return

        val context = view.context
        val sizePx = dpToPx(context, size)

        // 设置drawable大小
        drawable.setBounds(0, 0, sizePx, sizePx)

        // 应用着色
        val finalDrawable = if (tintColor != null) {
            tintDrawable(drawable, ContextCompat.getColor(context, tintColor))
        } else {
            drawable
        }

        when (view) {
            is TextView -> {
                val currentDrawables = view.compoundDrawables
                val leftDrawable = if (isLeft) finalDrawable else currentDrawables[0]
                val topDrawable = if (isTop) finalDrawable else currentDrawables[1]
                val rightDrawable = if (isRight) finalDrawable else currentDrawables[2]
                val bottomDrawable = if (isBottom) finalDrawable else currentDrawables[3]

                view.setCompoundDrawables(leftDrawable, topDrawable, rightDrawable, bottomDrawable)
            }
            is Button -> {
                val currentDrawables = view.compoundDrawables
                val leftDrawable = if (isLeft) finalDrawable else currentDrawables[0]
                val topDrawable = if (isTop) finalDrawable else currentDrawables[1]
                val rightDrawable = if (isRight) finalDrawable else currentDrawables[2]
                val bottomDrawable = if (isBottom) finalDrawable else currentDrawables[3]

                view.setCompoundDrawables(leftDrawable, topDrawable, rightDrawable, bottomDrawable)
            }
            is ImageButton -> {
                if (isLeft || isRight || isTop || isBottom) {
                    view.setImageDrawable(finalDrawable)
                }
            }
            is ImageView -> {
                if (isLeft || isRight || isTop || isBottom) {
                    view.setImageDrawable(finalDrawable)
                }
            }
        }
    }

    /**
     * dp转px
     * @param context Context
     * @param dp dp值
     * @return px值
     */
    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * dp转px
     * @param context Context
     * @param dp dp值
     * @return px值
     */
    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
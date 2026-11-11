package com.example.hrrepayment.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * The app's motion vocabulary.
 *
 * Material 3 Expressive's `MotionScheme` sets the same three families this build
 * uses: a springy *spatial* spec for anything that moves, a faster *effects* spec
 * for colour/alpha, and slow variants for large surfaces. The library wrapper is
 * published behind an opt-in marker that this toolchain cannot consume offline, so
 * the equivalent specs are declared here and used explicitly — screens stay on the
 * expressive feel without depending on the gated API.
 *
 * Every spec is bounded: nothing here runs longer than [Tokens.SLOT_MS] (350ms), and
 * the springs are damped so they do not overshoot distractingly.
 */
object AppMotion {

    /** Spatial default: used for entry/exit of content. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Spatial, snappier: selection changes, slot collapse. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Effects default: colour, alpha, elevation. */
    fun <T> effects(): FiniteAnimationSpec<T> = tween(Tokens.SELECT_MS)

    /** Slide offset spec for vertical movement. */
    fun offset(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )
}

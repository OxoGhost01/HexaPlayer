package com.oxoghost.hexaplayer.ui.material.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.nav.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Material 3 motion for the material skin: easings, durations and the screen transitions. */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val SHORT = 200
    const val MEDIUM = 350
    const val LONG = 450

    private enum class Kind { Tab, Overlay, Page }

    private fun kind(route: String?) = when (route) {
        Routes.HOME, Routes.LIBRARY, Routes.SEARCH, Routes.DOWNLOAD -> Kind.Tab
        Routes.PLAYER, Routes.LYRICS, Routes.QUEUE -> Kind.Overlay
        else -> Kind.Page
    }

    // Tabs fade through each other, pages slide along the x axis, the player and its
    // screens rise from the bottom over what was there.

    fun enter(from: String?, to: String?): EnterTransition = when {
        kind(to) == Kind.Overlay -> slideInVertically(tween(LONG, easing = EmphasizedDecelerate)) { it } + fadeIn(tween(SHORT))
        kind(from) == Kind.Tab && kind(to) == Kind.Tab -> fadeThroughIn()
        else -> slideInHorizontally(tween(MEDIUM, easing = Emphasized)) { it / 8 } + fadeIn(tween(SHORT, delayMillis = 60))
    }

    fun exit(from: String?, to: String?): ExitTransition = when {
        // The screen below stays put until the overlay has covered it.
        kind(to) == Kind.Overlay -> fadeOut(tween(1, delayMillis = LONG))
        kind(from) == Kind.Tab && kind(to) == Kind.Tab -> fadeOut(tween(90))
        else -> slideOutHorizontally(tween(MEDIUM, easing = Emphasized)) { -it / 8 } + fadeOut(tween(90))
    }

    /** [from] is the screen going away, [to] the one coming back. */
    fun popEnter(from: String?, to: String?): EnterTransition = when {
        kind(from) == Kind.Overlay -> EnterTransition.None
        kind(from) == Kind.Tab && kind(to) == Kind.Tab -> fadeThroughIn()
        else -> slideInHorizontally(tween(MEDIUM, easing = Emphasized)) { -it / 8 } + fadeIn(tween(SHORT, delayMillis = 60))
    }

    fun popExit(from: String?, to: String?): ExitTransition = when {
        kind(from) == Kind.Overlay -> slideOutVertically(tween(MEDIUM, easing = EmphasizedAccelerate)) { it } + fadeOut(tween(MEDIUM))
        kind(from) == Kind.Tab && kind(to) == Kind.Tab -> fadeOut(tween(90))
        else -> slideOutHorizontally(tween(MEDIUM, easing = Emphasized)) { it / 8 } + fadeOut(tween(90))
    }

    private fun fadeThroughIn() =
        fadeIn(tween(210, delayMillis = 90, easing = EmphasizedDecelerate)) +
            scaleIn(tween(210, delayMillis = 90, easing = EmphasizedDecelerate), initialScale = 0.94f)
}

/** Set by the navigation host so covers can fly from a grid into their page; null in the terminal skin. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The same cover on two screens ([key] like "album:12") moves between them instead of cutting. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(key: String): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedCover.sharedElement(
            rememberSharedContentState("cover:$key"),
            scope,
            boundsTransform = { _, _ -> tween(Motion.LONG, easing = Motion.Emphasized) },
        )
    }
}

/** Shrinks a little while pressed, springing back on release. */
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.95f): Modifier = composed {
    val down by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (down) pressed else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Offsets a swipeable surface follows while dragged ([swipeGestures]). */
@Stable
class SwipeState internal constructor(internal val scope: CoroutineScope) {
    val x = Animatable(0f)
    val y = Animatable(0f)

    /** Back to rest, e.g. once the screen it closed has come back. */
    fun reset() {
        scope.launch { x.snapTo(0f) }
        scope.launch { y.snapTo(0f) }
    }
}

@Composable
fun rememberSwipeState(): SwipeState {
    val scope = rememberCoroutineScope()
    return remember { SwipeState(scope) }
}

/**
 * Drag that follows the finger on one axis. Sideways past the threshold the surface flies out,
 * [onLeft] or [onRight] runs, and it comes back in from the other side. Up or down past the
 * threshold calls [onUp] or [onDown]; a direction without an action only gives a little, and
 * anything short of the threshold springs back.
 */
fun Modifier.swipeGestures(
    state: SwipeState,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
): Modifier = composed {
    val left by rememberUpdatedState(onLeft)
    val right by rememberUpdatedState(onRight)
    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    pointerInput(state) {
        val threshold = 96.dp.toPx()
        val back = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
        var horizontal: Boolean? = null
        detectDragGestures(
            onDragStart = { horizontal = null },
            onDragEnd = {
                val scope = state.scope
                if (horizontal == true) {
                    val x = state.x.value
                    val action = if (x < -threshold) left else if (x > threshold) right else null
                    scope.launch {
                        if (action == null) {
                            state.x.animateTo(0f, back)
                            return@launch
                        }
                        val width = size.width.toFloat()
                        state.x.animateTo(if (x < 0) -width else width, tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate))
                        action()
                        state.x.snapTo(if (x < 0) width / 3 else -width / 3)
                        state.x.animateTo(0f, tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate))
                    }
                } else if (horizontal == false) {
                    val y = state.y.value
                    when {
                        y > threshold && down != null -> down?.invoke()
                        y < -threshold && up != null -> {
                            up?.invoke()
                            scope.launch { state.y.animateTo(0f, back) }
                        }
                        else -> scope.launch { state.y.animateTo(0f, back) }
                    }
                }
            },
            onDragCancel = {
                state.scope.launch { state.x.animateTo(0f) }
                state.scope.launch { state.y.animateTo(0f) }
            },
        ) { change, drag ->
            if (horizontal == null) {
                if (abs(drag.x) == abs(drag.y)) return@detectDragGestures
                horizontal = abs(drag.x) > abs(drag.y)
            }
            change.consume()
            state.scope.launch {
                if (horizontal == true) {
                    val free = (drag.x < 0 && left != null) || (drag.x > 0 && right != null)
                    state.x.snapTo(state.x.value + drag.x * if (free) 1f else 0.25f)
                } else {
                    val free = (drag.y > 0 && down != null) || (drag.y < 0 && up != null)
                    state.y.snapTo(state.y.value + drag.y * if (free) 1f else 0.25f)
                }
            }
        }
    }
}

package app.wild.android.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * SZKM-67 §4.2 共享元素基础设施：
 * `SharedTransitionLayout` 包 NavHost，作用域经这两个 Local 下沉到各屏；
 * `LocalNavAnimatedVisibilityScope` 由每个 `composable` 的 AnimatedContentScope 提供
 * （navigation-compose 2.9.4 无 sharedElement 参数，走作用域注入）。
 * 双栏/预览等无转场作用域的场景两个 Local 均为 null，修饰符自动退化为 no-op。
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 每屏一份的封面共享元素认领集（NavScope 提供）：
 * 同屏出现同一本书两次（推荐区块重复）时，仅首个实例挂 `cover-{aid}`，
 * 避免 sharedElement 重复 key 在转场期抛 IllegalStateException。
 */
val LocalCoverClaims = compositionLocalOf<MutableSet<Int>?> { null }

/** 共享元素统一的 bounds 过渡：位移 spring 无 overshoot（§4.1/§4.2）。 */
private val SharedBoundsTransform = BoundsTransform { _, _ ->
    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
}

/** 封面图共享元素：`"cover-{aid}"`，列表网格 → 详情头图。 */
@Composable
fun Modifier.novelCoverSharedElement(aid: Int): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val claims = LocalCoverClaims.current
    // 未提供认领集的旧路径保持原行为；提供时仅首个同 aid 实例参与共享元素
    val claimed = claims == null || remember(claims, aid) { claims.add(aid) }
    if (!claimed) return this
    return with(shared) {
        sharedElement(
            rememberSharedContentState("cover-$aid"),
            animatedVisibilityScope = animScope,
            boundsTransform = SharedBoundsTransform,
        )
    }
}

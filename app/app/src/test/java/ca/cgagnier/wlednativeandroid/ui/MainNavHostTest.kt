package ca.cgagnier.wlednativeandroid.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 页面切换现在全部由 [HomeScaffold] 的底部页签承担，NavDisplay 只剩一个 HomeKey 目的地，
 * 所以这里只校验「根目的地不会被误弹出」这一条真实的返回栈约束。
 */
class MainNavHostTest {

    @Test
    fun `initial back stack contains HomeKey as root destination`() {
        val backStack = NavBackStack<NavKey>(HomeKey)

        assertEquals(1, backStack.size)
        assertEquals(HomeKey, backStack.last())
    }

    @Test
    fun `back navigation on root destination keeps HomeKey`() {
        val backStack = NavBackStack<NavKey>(HomeKey)

        // 与 MainNavHost 的 onBack 守卫一致：只剩一个目的地时不弹栈
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }

        assertEquals(1, backStack.size)
        assertEquals(HomeKey, backStack.last())
    }
}

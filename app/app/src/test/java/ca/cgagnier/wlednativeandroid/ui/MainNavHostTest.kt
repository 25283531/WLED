package ca.cgagnier.wlednativeandroid.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainNavHostTest {

    @Test
    fun `initial back stack contains HomeKey as root destination`() {
        val backStack = NavBackStack<NavKey>(HomeKey)

        assertEquals(1, backStack.size)
        assertEquals(HomeKey, backStack.last())
    }

    @Test
    fun `navigating to settings pushes SettingsKey on top of back stack`() {
        val backStack = NavBackStack<NavKey>(HomeKey)

        backStack.add(SettingsKey)

        assertEquals(2, backStack.size)
        assertEquals(SettingsKey, backStack.last())
        assertEquals(HomeKey, backStack.first())
    }

    @Test
    fun `navigating back from settings restores HomeKey`() {
        val backStack = NavBackStack<NavKey>(HomeKey)
        backStack.add(SettingsKey)

        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }

        assertEquals(1, backStack.size)
        assertEquals(HomeKey, backStack.last())
    }

    @Test
    fun `back navigation on root destination does not remove last destination`() {
        val backStack = NavBackStack<NavKey>(HomeKey)

        // Simulating the onBack guard condition from MainNavHost:
        // if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }

        assertEquals(1, backStack.size)
        assertEquals(HomeKey, backStack.last())
    }

    @Test
    fun `navigation keys are distinct and identify destinations correctly`() {
        val homeKey: NavKey = HomeKey
        val settingsKey: NavKey = SettingsKey

        assertTrue(homeKey is HomeKey)
        assertTrue(settingsKey is SettingsKey)
        assertEquals("HomeKey", homeKey.toString())
        assertEquals("SettingsKey", settingsKey.toString())
    }
}

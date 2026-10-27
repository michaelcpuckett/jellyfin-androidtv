package org.jellyfin.androidtv.ui.browsing

import android.view.View
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.ViewHolderTask
import androidx.recyclerview.widget.RecyclerView
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class LibraryGridFocusTests : FunSpec({
	test("Down can select an off-screen poster instead of using spatial focus search into the alphabet") {
		val grid = mockk<BaseGridView>(relaxed = true)
		val card = mockk<View>(relaxed = true)
		val task = slot<ViewHolderTask>()
		every { grid.findViewHolderForAdapterPosition(13) } returns null
		every { grid.setSelectedPosition(13, capture(task)) } returns Unit
		every { grid.hasFocus() } returns true
		LibraryGridFocus.move(grid, 13)
		task.captured.run(object : RecyclerView.ViewHolder(card) {})
		verify(exactly = 1) { card.requestFocus() }
	}
	test("an off-screen navigation request does not take focus back after leaving the grid") {
		val grid = mockk<BaseGridView>(relaxed = true)
		val card = mockk<View>(relaxed = true)
		val task = slot<ViewHolderTask>()
		every { grid.findViewHolderForAdapterPosition(13) } returns null
		every { grid.setSelectedPosition(13, capture(task)) } returns Unit
		every { grid.hasFocus() } returns false
		LibraryGridFocus.move(grid, 13)
		task.captured.run(object : RecyclerView.ViewHolder(card) {})
		verify(exactly = 0) { card.requestFocus() }
	}
	test("Left from the rail focuses the selected poster even when the grid container lacks focus") {
		val grid = mockk<BaseGridView>(relaxed = true)
		val card = mockk<View>(relaxed = true)
		val holder = object : RecyclerView.ViewHolder(card) {}
		every { grid.selectedPosition } returns 7
		every { grid.hasFocus() } returns false
		every { grid.findViewHolderForAdapterPosition(7) } returns holder
		every { card.requestFocus() } returns true
		LibraryGridFocus.focus(grid, 30) { true } shouldBe true
		verify(exactly = 1) { card.requestFocus() }
		verify(exactly = 0) { grid.requestFocus() }
	}
	test("a newly filtered poster receives focus after layout without requiring the grid to already have focus") {
		val grid = mockk<BaseGridView>(relaxed = true)
		val card = mockk<View>(relaxed = true)
		val task = slot<ViewHolderTask>()
		every { grid.selectedPosition } returns -1
		every { grid.hasFocus() } returns false
		every { grid.findViewHolderForAdapterPosition(0) } returns null
		every { grid.setSelectedPosition(0, capture(task)) } returns Unit
		every { card.requestFocus() } returns true
		LibraryGridFocus.focus(grid, 30) { true } shouldBe true
		task.captured.run(object : RecyclerView.ViewHolder(card) {})
		verify(exactly = 1) { card.requestFocus() }
		verify(exactly = 0) { grid.hasFocus() }
	}
	test("a deferred focus request cannot steal focus after the viewer leaves the results and rail") {
		val grid = mockk<BaseGridView>(relaxed = true)
		val card = mockk<View>(relaxed = true)
		val task = slot<ViewHolderTask>()
		var allowed = true
		every { grid.selectedPosition } returns 0
		every { grid.findViewHolderForAdapterPosition(0) } returns null
		every { grid.setSelectedPosition(0, capture(task)) } returns Unit
		LibraryGridFocus.focus(grid, 10) { allowed } shouldBe true
		allowed = false
		task.captured.run(object : RecyclerView.ViewHolder(card) {})
		verify(exactly = 0) { card.requestFocus() }
	}
	test("an empty result can fall back to the toolbar without requesting a nonexistent poster") {
		val grid = mockk<BaseGridView>(relaxed = true)
		LibraryGridFocus.focus(grid, 0) { true } shouldBe false
		verify(exactly = 0) { grid.findViewHolderForAdapterPosition(any()) }
	}
	test("a late result cannot start focusing posters after focus has moved to the sidebar") {
		val grid = mockk<BaseGridView>(relaxed = true)
		LibraryGridFocus.focus(grid, 30) { false } shouldBe false
		verify(exactly = 0) { grid.findViewHolderForAdapterPosition(any()) }
		verify(exactly = 0) { grid.setSelectedPosition(any<Int>(), any<ViewHolderTask>()) }
	}
	test("an old selection is clamped to the new filtered result") {
		val grid = mockk<BaseGridView>(relaxed = true)
		val card = mockk<View>(relaxed = true)
		every { grid.selectedPosition } returns 100
		every { grid.findViewHolderForAdapterPosition(2) } returns object : RecyclerView.ViewHolder(card) {}
		every { card.requestFocus() } returns true
		LibraryGridFocus.focus(grid, 3) { true } shouldBe true
		verify(exactly = 1) { grid.setSelectedPosition(2) }
	}
})

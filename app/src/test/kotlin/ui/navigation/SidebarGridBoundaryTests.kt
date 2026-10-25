package org.jellyfin.androidtv.ui.navigation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.VerticalGridView

class SidebarGridBoundaryTests : FunSpec({
	test("a six-column poster grid goes left within its row and opens the rail only at the first column") {
		val xs = (0..5).map { 32 + it * 138 }
		for (row in listOf(0, 1, 20)) {
			for (column in 0..5) {
				val index = row * 6 + column
				val boundary = isSidebarGridBoundary(index, xs[column], null, xs, false, false)
				boundary shouldBe (column == 0)
				previousGridPosition(index, false, 6, boundary) shouldBe if (column == 0) null else index - 1
			}
		}
	}
	test("vertical grids keep row-major navigation even when Leanback supports secondary horizontal scrolling") {
		isHorizontalSidebarGrid(HorizontalGridView::class.java) shouldBe true
		isHorizontalSidebarGrid(VerticalGridView::class.java) shouldBe false
	}
	test("middle posters keep Left inside a horizontal row") {
		for (position in 1..8) {
			isSidebarGridBoundary(position, 24 + position * 140, 24, (0..8).map { 24 + it * 140 }, true, false) shouldBe false
		}
		isSidebarGridBoundary(0, 24, 24, listOf(24, 164, 304), true, false) shouldBe true
	}
	test("a scrolled horizontal row never mistakes the visible left edge for the first column") {
		isSidebarGridBoundary(20, 24, null, listOf(24, 164, 304), true, false) shouldBe false
		isSidebarGridBoundary(2, 24, -256, listOf(-256, -116, 24), true, false) shouldBe false
	}
	test("all cards in the first column of a multi-row horizontal grid can enter the rail") {
		for (position in 0..2) isSidebarGridBoundary(position, 24, 24, listOf(24, 24, 24, 164, 164, 164), true, false) shouldBe true
		for (position in 3..5) isSidebarGridBoundary(position, 164, 24, listOf(24, 24, 24, 164, 164, 164), true, false) shouldBe false
	}
	test("vertical grids exit only from the leading column even after scrolling down") {
		isSidebarGridBoundary(25, 24, null, listOf(24, 164, 304, 24, 164), false, false) shouldBe true
		isSidebarGridBoundary(26, 164, null, listOf(24, 164, 304, 24, 164), false, false) shouldBe false
	}
	test("RTL mirrors the leading column without redirecting middle cards") {
		isSidebarGridBoundary(0, 600, 600, listOf(600, 460, 320), true, true) shouldBe true
		isSidebarGridBoundary(1, 460, 600, listOf(600, 460, 320), true, true) shouldBe false
		isSidebarGridBoundary(25, 600, null, listOf(320, 460, 600), false, true) shouldBe true
		isSidebarGridBoundary(26, 460, null, listOf(320, 460, 600), false, true) shouldBe false
	}
	test("empty and removed cards cannot redirect focus") {
		isSidebarGridBoundary(-1, 24, 24, listOf(24), true, false) shouldBe false
		isSidebarGridBoundary(0, 24, null, emptyList(), false, false) shouldBe false
	}
	test("successive Left presses visit preceding posters all the way back from an off-screen position") {
		var position = 40
		for (expected in 39 downTo 0) {
			val previous = previousGridPosition(position, horizontal = true, spanCount = 1, atBoundary = false)
			previous shouldBe expected
			position = previous!!
		}
		previousGridPosition(0, true, 1, true) shouldBe null
	}
	test("horizontal multi-row grids move to the previous column in the same row") {
		previousGridPosition(8, true, 3, false) shouldBe 5
		previousGridPosition(5, true, 3, false) shouldBe 2
		previousGridPosition(2, true, 3, true) shouldBe null
	}
	test("vertical grid navigation moves one item without wrapping past its first column") {
		previousGridPosition(27, false, 5, false) shouldBe 26
		previousGridPosition(26, false, 5, false) shouldBe 25
		previousGridPosition(25, false, 5, true) shouldBe null
	}
})

package org.jellyfin.androidtv.ui.itemdetail

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

class EpisodeDetailsSectionsTests : FunSpec({
	test("late Cast and Comments responses preserve Next Cast Guest Comments order") {
		for (arrivalOrder in listOf(listOf(30L, 50L), listOf(50L, 30L))) {
			val rows = mutableListOf<Long?>(null, 20L, 40L, 60L, 70L)
			for (id in arrivalOrder) rows.add(episodeSectionIndex(rows, id), id)
			rows shouldBe listOf(null, 20L, 30L, 40L, 50L, 60L, 70L)
		}
	}

	test("missing next episode and guest stars do not displace comments below extras") {
		episodeSectionIndex(listOf(null, 60L, 70L), 50L) shouldBe 1
		episodeSectionIndex(listOf(null), 50L) shouldBe 1
	}

	test("regular Cast includes series actors while keeping guest stars separate and deduplicated") {
		val regularId = UUID.randomUUID()
		val guestId = UUID.randomUUID()
		val series = listOf(
			BaseItemPerson(id = regularId, name = "Regular", type = PersonKind.ACTOR, role = "Lead"),
			BaseItemPerson(id = guestId, name = "Guest", type = PersonKind.ACTOR),
			BaseItemPerson(id = UUID.randomUUID(), name = "Director", type = PersonKind.DIRECTOR),
		)
		val episode = listOf(
			BaseItemPerson(id = regularId, name = "Regular", type = PersonKind.ACTOR),
			BaseItemPerson(id = guestId, name = "Guest", type = PersonKind.GUEST_STAR),
		)
		regularEpisodeCast(series, episode).map { it.id } shouldBe listOf(regularId)
		regularEpisodeCast(series, episode).single().role shouldBe "Lead"
	}

	test("episode actor metadata is usable when the parent series request fails") {
		val actor = BaseItemPerson(id = UUID.randomUUID(), name = "Actor", type = PersonKind.ACTOR)
		regularEpisodeCast(emptyList(), listOf(actor)) shouldBe listOf(actor)
	}
})

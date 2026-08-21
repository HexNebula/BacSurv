package ma.bacsurv.web;

import ma.bacsurv.web.service.CenterAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Setting a centre up is the first thing anybody does and the part that has to
 * survive a person who is not an IT person: name it, say how many rooms, name
 * the exceptions, declare the session.
 */
@SpringBootTest
class CenterSetupTest {

    @Autowired CenterAdminService admin;

    @Test
    void thirteenIdenticalRoomsAreOneEntry() {
        long centre = admin.createCenter("Lycée Ibn Batouta " + System.nanoTime());

        assertEquals(13, admin.addRooms(centre, 13, null));

        var rooms = admin.detail(centre).rooms();
        assertEquals(13, rooms.size());
        assertEquals("Salle 1", rooms.getFirst().label());
        assertEquals("R1", rooms.getFirst().reference());
        assertTrue(rooms.stream().anyMatch(r -> r.label().equals("Salle 13")));
    }

    @Test
    void roomsAddedLaterContinueTheNumbering() {
        long centre = admin.createCenter("Lycée Al Massira " + System.nanoTime());
        admin.addRooms(centre, 3, null);
        admin.addRooms(centre, 2, null);

        var references = admin.detail(centre).rooms().stream()
                .map(CenterAdminService.RoomView::reference).toList();
        assertEquals(5, references.size());
        assertTrue(references.contains("R4") && references.contains("R5"),
                "a second batch must not restart at R1: " + references);
    }

    @Test
    void aHallKeepsItsOwnNameAndItsOwnStaffing() {
        long centre = admin.createCenter("Lycée Chaouki " + System.nanoTime());
        admin.addRooms(centre, 2, null);
        var room = admin.detail(centre).rooms().getFirst();

        admin.renameRoom(room.id(), "Bibliothèque", 3);

        var saved = admin.detail(centre).rooms().stream()
                .filter(r -> r.id().equals(room.id())).findFirst().orElseThrow();
        assertEquals("Bibliothèque", saved.label());
        assertEquals(3, saved.surveillants());
    }

    @Test
    void aRoomCannotBeStaffedBelowTheOfficialFloor() {
        long centre = admin.createCenter("Lycée Zerktouni " + System.nanoTime());
        admin.addRooms(centre, 1, null);
        var room = admin.detail(centre).rooms().getFirst();

        var refused = assertThrows(IllegalArgumentException.class,
                () -> admin.renameRoom(room.id(), "Salle 1", 1));
        assertEquals("room.surveillants.tooFew", refused.getMessage());
    }

    @Test
    void aSessionKnowsItsDaysBeforeAnyExamIsEntered() {
        long centre = admin.createCenter("Lycée Moulay Youssef " + System.nanoTime());
        admin.createSession(centre, "Régional 1BAC 2026", "REGIONAL_1BAC",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

        var session = admin.detail(centre).sessions().getFirst();
        assertEquals("Régional 1BAC 2026", session.reference());
        assertEquals(LocalDate.of(2026, 6, 1), session.startsOn());
        assertEquals(0, session.slotCount(), "no épreuve entered yet");
    }

    @Test
    void datesInTheWrongOrderAreRefused() {
        long centre = admin.createCenter("Lycée Al Khansaa " + System.nanoTime());

        var refused = assertThrows(IllegalArgumentException.class,
                () -> admin.createSession(centre, "Session", "NATIONAL_2BAC",
                        LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 4)));
        assertEquals("session.dates.reversed", refused.getMessage());
    }

    /**
     * The Maghreb writes numbers 0-9. Plain Arabic formatting would print a
     * pool of 45 as ٤٥, which is not what an administration here puts on
     * paper — so the Arabic pages run on ar-MA.
     */
    @Test
    void arabicPagesUseWesternDigits() {
        String counted = new java.text.MessageFormat("{0}",
                ma.bacsurv.web.config.LocaleConfig.ARABIC).format(new Object[]{45});

        assertEquals("45", counted, "Moroccan Arabic uses the digits 0-9");
        assertEquals("ar", ma.bacsurv.web.config.LocaleConfig.ARABIC.getLanguage(),
                "?lang=ar and the Arabic bundle both key off the language");
    }

    @Test
    void twoCentresCannotShareAName() {
        String name = "Lycée Ibn Sina " + System.nanoTime();
        admin.createCenter(name);

        var refused = assertThrows(IllegalArgumentException.class,
                () -> admin.createCenter(name));
        assertEquals("center.exists", refused.getMessage());
    }
}

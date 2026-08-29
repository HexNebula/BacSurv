package ma.bacsurv.web;

import ma.bacsurv.domain.OperationType;
import ma.bacsurv.web.persistence.CenterStreamEntity;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.service.CatalogueService;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.TimetableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Candidats libres sit the régionale and the nationale in the same year, so
 * when they fail they sit both rattrapages — the regional one first, on its
 * own days, then the national one alongside the scolarisés.
 *
 * <p>That is a first-year session held in the middle of a second-year season,
 * and it is what the old rule could not express. « Régional means first year,
 * everything else means second » was true of three session types and false of
 * four, and the way it failed was silent: the filière picker simply offered
 * the wrong list, and nothing anywhere said so.
 */
@SpringBootTest
class CandidatsLibresTest {

    @Autowired CenterAdminService centers;
    @Autowired TimetableService timetable;
    @Autowired CatalogueService catalogue;
    @Autowired OperationRepository operations;

    private static final LocalDate JUNE = LocalDate.of(2027, 6, 10);
    private static final LocalDate JULY = LocalDate.of(2027, 7, 8);

    private long centre() {
        return centers.createCenter("ثانوية المترشحين الأحرار " + System.nanoTime());
    }

    /** It has to be creatable at all: the enum is what validates a type. */
    @Test
    void aRegionalRattrapageIsASessionACentreCanRun() {
        long centre = centre();
        long session = centers.createSession(centre, "الاستدراكية الجهوية",
                "REGIONAL_1BAC_RATTRAPAGE", JULY, JULY);

        assertTrue(session > 0);
        var view = centers.detail(centre).sessions().stream()
                .filter(s -> s.id() == session).findFirst().orElseThrow();
        assertEquals("REGIONAL_1BAC_RATTRAPAGE", view.type());
        assertEquals("BAC1", view.level(), "a rattrapage of the régionale is still first year");
    }

    @Test
    void allFourSessionsOfAYearCoexist() {
        long centre = centre();
        centers.createSession(centre, "الجهوي", "REGIONAL_1BAC", JUNE, JUNE);
        centers.createSession(centre, "الوطني", "NATIONAL_2BAC", JUNE.plusDays(4),
                JUNE.plusDays(4));
        centers.createSession(centre, "الاستدراكية الجهوية", "REGIONAL_1BAC_RATTRAPAGE",
                JULY, JULY);
        centers.createSession(centre, "الاستدراكية الوطنية", "NATIONAL_2BAC_RATTRAPAGE",
                JULY.plusDays(3), JULY.plusDays(3));

        var sessions = centers.detail(centre).sessions();
        assertEquals(4, sessions.size(), "nothing makes a session type unique within a year");
        // by type rather than by position: the list is not in creation order
        var levelByType = sessions.stream().collect(java.util.stream.Collectors.toMap(
                CenterAdminService.SessionView::type, CenterAdminService.SessionView::level));
        assertEquals(java.util.Map.of(
                "REGIONAL_1BAC", "BAC1",
                "REGIONAL_1BAC_RATTRAPAGE", "BAC1",
                "NATIONAL_2BAC", "BAC2",
                "NATIONAL_2BAC_RATTRAPAGE", "BAC2"), levelByType);
    }

    /**
     * The actual damage the old rule would have done: a filière declared in the
     * regional rattrapage was recorded in the centre's catalogue at the wrong
     * level, creating a second row for a filière already listed — the same name
     * at two levels, one of them fiction.
     */
    @Test
    void aFiliereOfTheRegionalRattrapageIsListedAtFirstYear() {
        long centre = centre();
        long session = centers.createSession(centre, "الاستدراكية الجهوية",
                "REGIONAL_1BAC_RATTRAPAGE", JULY, JULY);
        centers.addRooms(centre, 2, "قاعة");
        List<Long> rooms = centers.detail(centre).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();

        timetable.addStream(session, "العلوم الرياضية", rooms);

        var listed = catalogue.streamsOf(centre).stream()
                .filter(entry -> entry.name().equals("العلوم الرياضية"))
                .toList();
        assertEquals(1, listed.size(), "one filière, not one per level");
        assertEquals("BAC1", listed.getFirst().level());
    }

    /** The rule now lives in one place, and the enum is that place. */
    @Test
    void everyTypeKnowsItsOwnLevel() {
        assertEquals("BAC1", OperationType.REGIONAL_1BAC.level());
        assertEquals("BAC1", OperationType.REGIONAL_1BAC_RATTRAPAGE.level());
        assertEquals("BAC2", OperationType.NATIONAL_2BAC.level());
        assertEquals("BAC2", OperationType.NATIONAL_2BAC_RATTRAPAGE.level());
        // the persistence-side helper is now a way in, not a second copy
        assertEquals("BAC1", CenterStreamEntity.levelOf("REGIONAL_1BAC_RATTRAPAGE"));
    }

    /** A session created before the level was held keeps the one it implied. */
    @Test
    void anExistingSessionCarriesTheLevelItsTypeImplied() {
        long centre = centre();
        long session = centers.createSession(centre, "الوطني", "NATIONAL_2BAC", JUNE, JUNE);
        assertEquals("BAC2", operations.findById(session).orElseThrow().getLevel());
    }
}

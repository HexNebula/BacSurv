package ma.bacsurv.web;

import ma.bacsurv.web.service.CatalogueService;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.TeacherAdminService;
import ma.bacsurv.web.service.TeacherAdminService.Details;
import ma.bacsurv.web.service.TeacherImportService;
import ma.bacsurv.web.service.TimetableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two lists a centre owns. Neither is derivable: a centre may examine a
 * subject nobody there teaches, and runs different filières from one session to
 * the next. Two centres in one city differ in both.
 */
@SpringBootTest
class CatalogueTest {

    @Autowired CatalogueService catalogue;
    @Autowired CenterAdminService centers;
    @Autowired TeacherAdminService teacherAdmin;
    @Autowired TeacherImportService teacherImport;
    @Autowired TimetableService timetable;

    private long centre() {
        return centers.createCenter("Lycée Catalogue " + System.nanoTime());
    }

    private static List<String> names(List<CatalogueService.Entry> entries) {
        return entries.stream().map(CatalogueService.Entry::name).toList();
    }

    /**
     * 1BAC and 2BAC run different filières, and a centre runs both. The name
     * alone therefore cannot identify one: Sciences expérimentales exists at
     * each level, sitting different papers with different candidates, and a
     * list that held it once would offer the wrong one when planning.
     */
    @Test
    void oneNameBelongsToEachLevelSeparately() {
        long centre = centre();

        catalogue.addStream(centre, "Sciences expérimentales", "BAC1");
        catalogue.addStream(centre, "Sciences expérimentales", "BAC2");

        var levels = catalogue.streamsOf(centre).stream()
                .map(CatalogueService.Entry::level).sorted().toList();
        assertEquals(List.of("BAC1", "BAC2"), levels);

        assertThrows(IllegalArgumentException.class,
                () -> catalogue.addStream(centre, "Sciences expérimentales", "BAC2"),
                "the same filière at the same level is still one filière");
    }

    /**
     * The level is what the whole list is for, so a filière cannot be recorded
     * without one — a filière belonging to neither year would be offered when
     * planning both.
     */
    @Test
    void aFiliereCannotBeLevelless() {
        long centre = centre();

        assertThrows(IllegalArgumentException.class,
                () -> catalogue.addStream(centre, "Lettres", null));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.addStream(centre, "Lettres", "TRONC_COMMUN"));
    }

    /** A filière listed at the wrong year is corrected without renaming it. */
    @Test
    void aFiliereMovesBetweenLevels() {
        long centre = centre();
        long lettres = catalogue.addStream(centre, "Lettres", "BAC1");

        catalogue.renameStream(lettres, "Lettres", "BAC2");

        var entry = catalogue.streamsOf(centre).getFirst();
        assertEquals("Lettres", entry.name());
        assertEquals("BAC2", entry.level());
    }

    @Test
    void aCentreKeepsItsOwnLists() {
        long first = centre();
        long second = centre();

        catalogue.addSubject(first, "Philosophie");
        catalogue.addStream(first, "Lettres", "BAC2");
        catalogue.addSubject(second, "Informatique");

        assertEquals(List.of("Philosophie"), names(catalogue.subjectsOf(first)));
        assertEquals(List.of("Lettres"), names(catalogue.streamsOf(first)));
        assertEquals(List.of("Informatique"), names(catalogue.subjectsOf(second)),
                "one centre's list must not leak into another's");
        assertTrue(catalogue.streamsOf(second).isEmpty());
    }

    @Test
    void theSameNameIsRefusedTwiceInOneCentre() {
        long centre = centre();
        catalogue.addSubject(centre, "Arabe");

        assertEquals("subject.exists", assertThrows(IllegalArgumentException.class,
                () -> catalogue.addSubject(centre, "Arabe")).getMessage());
        assertEquals("subject.name.required", assertThrows(IllegalArgumentException.class,
                () -> catalogue.addSubject(centre, "  ")).getMessage());
    }

    /**
     * The solver matches a teacher's subject to an épreuve's by exact string,
     * so a renamed entry has to carry both with it. Leaving them behind would
     * silently stop the own-subject rule from firing.
     */
    @Test
    void renamingASubjectCarriesTheTeachersAndTheEpreuves() {
        long centre = centre();
        centers.addRooms(centre, 2, "Salle");
        long sessionId = centers.createSession(centre, "Bac", "NATIONAL_2BAC",
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 4));
        long stream = timetable.addStream(sessionId, "Lettres",
                centers.detail(centre).rooms().stream()
                        .map(CenterAdminService.RoomView::id).toList());

        teacherAdmin.add(centre, new Details("D600001", "Amina", "Mathematiques", null, "F"));
        timetable.setExam(sessionId, stream, "Mathematiques", LocalDate.of(2026, 6, 4),
                LocalTime.of(8, 0), LocalTime.of(11, 0));

        long subjectId = catalogue.subjectsOf(centre).stream()
                .filter(entry -> entry.name().equals("Mathematiques"))
                .findFirst().orElseThrow().id();
        catalogue.renameSubject(subjectId, "Mathématiques");

        assertEquals("Mathématiques", teacherImport.pool(centre).getFirst().subject());
        assertEquals("Mathématiques", timetable.timetable(sessionId).exams().getFirst().subject());
        assertTrue(names(catalogue.subjectsOf(centre)).contains("Mathématiques"));
        assertFalse(names(catalogue.subjectsOf(centre)).contains("Mathematiques"));
    }

    @Test
    void anEntryStillInUseCannotBeRemoved() {
        long centre = centre();
        teacherAdmin.add(centre, new Details("D600002", "Youssef", "Anglais", null, "M"));

        long subjectId = catalogue.subjectsOf(centre).stream()
                .filter(entry -> entry.name().equals("Anglais"))
                .findFirst().orElseThrow().id();

        assertEquals("subject.inUse", assertThrows(IllegalArgumentException.class,
                () -> catalogue.removeSubject(subjectId)).getMessage());
    }

    @Test
    void anUnusedEntryCanBeRemoved() {
        long centre = centre();
        long subjectId = catalogue.addSubject(centre, "Éducation sportive");

        catalogue.removeSubject(subjectId);
        assertTrue(catalogue.subjectsOf(centre).isEmpty());
    }

    /** The list says how much depends on each entry, so the screen can show it. */
    @Test
    void eachEntryCarriesItsUsage() {
        long centre = centre();
        teacherAdmin.add(centre, new Details("D600003", "Karima", "SVT", null, "F"));
        teacherAdmin.add(centre, new Details("D600004", "Nadia", "SVT", null, "F"));

        CatalogueService.Entry svt = catalogue.subjectsOf(centre).stream()
                .filter(entry -> entry.name().equals("SVT")).findFirst().orElseThrow();
        assertEquals(2, svt.usedByTeachers());
        assertEquals(0, svt.usedByExams());
        assertTrue(svt.isUsed());
    }

    /**
     * A subject named while adding a teacher or an épreuve joins the list on
     * its own. An import must not fail because the spreadsheet mentions
     * something the catalogue was missing.
     */
    @Test
    void namingASubjectAnywhereAddsItToTheList() {
        long centre = centre();
        assertTrue(catalogue.subjectsOf(centre).isEmpty());

        teacherAdmin.add(centre, new Details("D600005", "Hakim", "Histoire et géographie",
                null, "M"));

        assertEquals(List.of("Histoire et géographie"), names(catalogue.subjectsOf(centre)));
    }

    @Test
    void declaringAFiliereInASessionAddsItToTheCentresList() {
        long centre = centre();
        centers.addRooms(centre, 3, "Salle");
        long sessionId = centers.createSession(centre, "Bac", "REGIONAL_1BAC",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

        timetable.addStream(sessionId, "Sciences Mathématiques",
                centers.detail(centre).rooms().stream()
                        .map(CenterAdminService.RoomView::id).toList());

        assertEquals(List.of("Sciences Mathématiques"), names(catalogue.streamsOf(centre)));
    }
}

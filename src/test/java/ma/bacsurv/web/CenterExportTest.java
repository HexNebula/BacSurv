package ma.bacsurv.web;

import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.CenterExportService;
import ma.bacsurv.web.service.TeacherAdminService;
import ma.bacsurv.web.service.TeacherAdminService.Absence;
import ma.bacsurv.web.service.TeacherAdminService.Details;
import ma.bacsurv.web.service.TimetableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A centre lives in one file on one machine, with nothing behind it. The
 * export is the only copy anybody can hold, so what it leaves out is lost.
 */
@SpringBootTest
class CenterExportTest {

    @Autowired CenterExportService exports;
    @Autowired CenterAdminService centers;
    @Autowired TeacherAdminService teacherAdmin;
    @Autowired TimetableService timetable;

    private static final LocalDate DAY = LocalDate.of(2026, 6, 4);

    @Test
    void carriesTheWholeCentre() {
        long centre = centers.createCenter("Lycée Export " + System.nanoTime());
        centers.editCenter(centre, "Lycée Export",
                new CenterAdminService.CenterIdentity("AREF Drâa-Tafilalet",
                        "Ouarzazate", "Ouarzazate", "REF-2026"));
        centers.addRooms(centre, 3, "Salle");
        long session = centers.createSession(centre, "Bac 2026", "NATIONAL_2BAC", DAY, DAY);

        teacherAdmin.add(centre, new Details("X100001", "Amina El Fassi", "Mathématiques",
                "Lycée Ibn Batouta", "F"));
        teacherAdmin.replaceAbsences(centre, "X100001",
                List.of(new Absence(null, DAY, null, null)));

        List<Long> rooms = centers.detail(centre).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();
        long stream = timetable.addStream(session, "Sciences", rooms);
        timetable.setExam(session, stream, "Mathématiques", DAY,
                LocalTime.of(8, 0), LocalTime.of(10, 0));

        var export = exports.of(centre);

        assertEquals("Lycée Export", export.centerName());
        assertEquals("AREF Drâa-Tafilalet", export.identity().academy(),
                "the paper identity is part of the record");
        assertEquals(3, export.rooms().size());

        var teacher = export.teachers().getFirst();
        assertEquals("X100001", teacher.matricule());
        assertEquals(1, teacher.absences().size(), "an absence nobody wrote down twice is lost");
        assertEquals(DAY, teacher.absences().getFirst().date());

        var written = export.sessions().getFirst();
        assertEquals("Bac 2026", written.reference());
        assertEquals(1, written.timetable().exams().size(), "the timetable is the afternoon's work");
        assertEquals(1, written.timetable().streams().size());
        assertEquals(2, written.rules().defaultSurveillantsPerRoom(),
                "the session's rules travel with it");
    }

    /** The counts behind the fairness rule are the part nobody can retype. */
    @Test
    void carriesWhatEachTeacherIsOwed() {
        long centre = centers.createCenter("Lycée Historique " + System.nanoTime());
        teacherAdmin.add(centre, new Details("X200001", "Sans passé", "Anglais", null, "M"));

        var teacher = exports.of(centre).teachers().getFirst();

        assertEquals(0, teacher.priorSurveillance());
        assertEquals(0, teacher.priorPrivileges());
    }

    @Test
    void isNamedAfterTheCentreAndTheDay() {
        long centre = centers.createCenter("Lycée Al Massira");
        var export = exports.of(centre);

        String name = exports.fileNameFor(export);

        assertTrue(name.startsWith("bacsurv-Lycée-Al-Massira-"), name);
        assertTrue(name.endsWith(LocalDate.now() + ".json"), name);
    }
}

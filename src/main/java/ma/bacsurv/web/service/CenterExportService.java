package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.CenterEntity;
import ma.bacsurv.web.persistence.CenterRepository;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.RoomRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import ma.bacsurv.web.persistence.UnavailabilityEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Everything a centre has, in one file it can keep.
 *
 * <p>A centre's whole record lives in a single database file on a single
 * machine — no snapshot behind it, nothing in the repository. Losing that file
 * loses the pool, the years of turns behind the fairness rule, and every
 * timetable ever entered. That is not a hypothetical: it happened here.
 *
 * <p>Written as plain JSON rather than a database dump, so it survives the move
 * to another engine and can be read by somebody who does not have this
 * application at all. It is a record, not a restore point — reading one back is
 * a separate job, and a harder one, because merging somebody's export into a
 * centre that has moved on is a question only they can answer.
 */
@Service
public class CenterExportService {

    /** A teacher as the pool holds them, absences included. */
    public record TeacherRecord(String matricule, String reference, String name, String nameFr,
                                String subject, String establishment, String corps, String gender,
                                List<AbsenceRecord> absences,
                                int priorSurveillance, int priorPrivileges) {}

    public record AbsenceRecord(LocalDate date, LocalTime startTime, LocalTime endTime) {}

    public record RoomRecord(String reference, String label, Integer surveillants) {}

    public record CatalogueRecord(List<SubjectRecord> subjects, List<StreamRecord> streams) {}

    public record SubjectRecord(String name, String nameFr) {}

    public record StreamRecord(String name, String nameFr, String level) {}

    /** A session with the timetable that was entered for it. */
    public record SessionRecord(String reference, String type, LocalDate startsOn,
                                LocalDate endsOn, TimetableService.Timetable timetable,
                                OperationConfigService.Settings rules) {}

    public record Export(String exportedAt, String application, int version,
                         String centerName, CenterAdminService.CenterIdentity identity,
                         List<RoomRecord> rooms, CatalogueRecord catalogue,
                         List<TeacherRecord> teachers, List<SessionRecord> sessions) {}

    private final CenterRepository centers;
    private final ma.bacsurv.web.persistence.AssignmentRepository assignments;
    private final RoomRepository rooms;
    private final TeacherRepository teachers;
    private final OperationRepository operations;
    private final CenterAdminService centerAdmin;
    private final CatalogueService catalogue;
    private final TimetableService timetables;
    private final OperationConfigService configs;

    public CenterExportService(CenterRepository centers,
                               ma.bacsurv.web.persistence.AssignmentRepository assignments,
                               RoomRepository rooms,
                               TeacherRepository teachers, OperationRepository operations,
                               CenterAdminService centerAdmin, CatalogueService catalogue,
                               TimetableService timetables, OperationConfigService configs) {
        this.centers = centers;
        this.assignments = assignments;
        this.rooms = rooms;
        this.teachers = teachers;
        this.operations = operations;
        this.centerAdmin = centerAdmin;
        this.catalogue = catalogue;
        this.timetables = timetables;
        this.configs = configs;
    }

    @Transactional(readOnly = true)
    public Export of(long centerId) {
        CenterEntity center = centers.findById(centerId)
                .orElseThrow(() -> new IllegalArgumentException("center.unknown"));

        CenterAdminService.CenterDetail detail = centerAdmin.detail(centerId);

        List<RoomRecord> roomRecords = rooms.findByCenterIdOrderByReferenceAsc(centerId).stream()
                .map(room -> new RoomRecord(room.getReference(), room.getLabel(),
                        room.getSurveillantsOverride()))
                .toList();

        CatalogueRecord catalogueRecord = new CatalogueRecord(
                catalogue.subjectsOf(centerId).stream()
                        .map(entry -> new SubjectRecord(entry.name(), entry.nameFr()))
                        .toList(),
                catalogue.streamsOf(centerId).stream()
                        .map(entry -> new StreamRecord(entry.name(), entry.nameFr(), entry.level()))
                        .toList());

        // the counts each teacher carries into the next session: what the
        // fairness rule is built on, and the part of the file that cannot be
        // typed again from paper
        var priorWork = priorWorkOf(centerId);

        List<TeacherRecord> teacherRecords = teachers.findPoolOfCenter(centerId).stream()
                .map(teacher -> teacherRecord(teacher, priorWork))
                .toList();

        List<SessionRecord> sessionRecords = operations.findAll().stream()
                .filter(operation -> operation.getCenter() != null
                        && operation.getCenter().getId().equals(centerId))
                .sorted(java.util.Comparator.comparing(operation -> operation.getId()))
                .map(operation -> new SessionRecord(
                        operation.getReference(),
                        operation.getType(),
                        operation.getStartsOn(),
                        operation.getEndsOn(),
                        timetables.timetable(operation.getId()),
                        configs.settings(operation.getId())))
                .toList();

        // 2: teachers carry a French name and a corps, and the catalogue's
        // entries carry a French label. Version 1 files hold none of them, and
        // a restore reads them as absent rather than as empty strings.
        return new Export(java.time.Instant.now().toString(), "BacSurv", 2,
                center.getName(), detail.identity(), roomRecords, catalogueRecord,
                teacherRecords, sessionRecords);
    }

    /**
     * Surveillances, and réserve plus permanence together, from every finished
     * session of this centre. Read straight from the assignments rather than
     * from a stored counter, because that is where the application itself
     * reads them: an export that disagreed with the fairness rule would be
     * worse than no export.
     */
    private java.util.Map<Long, int[]> priorWorkOf(long centerId) {
        java.util.Map<Long, int[]> counts = new java.util.HashMap<>();
        // every year, not only the current one: fairness stops at September but
        // a backup that forgot last year would be no backup at all
        for (Object[] row : assignments.lifetimeWorkOfCenter(centerId)) {
            Long teacherId = (Long) row[0];
            ma.bacsurv.domain.DutyRole role = (ma.bacsurv.domain.DutyRole) row[2];
            int count = ((Number) row[3]).intValue();
            int[] tally = counts.computeIfAbsent(teacherId, id -> new int[2]);
            if (role == ma.bacsurv.domain.DutyRole.SURVEILLANCE) tally[0] += count;
            else tally[1] += count;
        }
        return counts;
    }

    private TeacherRecord teacherRecord(TeacherEntity teacher,
                                        java.util.Map<Long, int[]> priorWork) {
        int[] prior = priorWork.getOrDefault(teacher.getId(), new int[]{0, 0});
        return new TeacherRecord(
                teacher.getMatricule(), teacher.getReference(), teacher.getName(),
                teacher.getNameFr(), teacher.getSubject(), teacher.getEstablishment(),
                teacher.getCorps(), teacher.getGender(),
                teacher.getUnavailabilities().stream()
                        .sorted(java.util.Comparator.comparing(UnavailabilityEntity::getDate))
                        .map(absence -> new AbsenceRecord(absence.getDate(),
                                absence.getStartTime(), absence.getEndTime()))
                        .toList(),
                prior[0], prior[1]);
    }

    /** A file name a person can recognise a year later. */
    public String fileNameFor(Export export) {
        String centre = export.centerName().replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-|-$)", "");
        return "bacsurv-" + (centre.isBlank() ? "centre" : centre)
                + "-" + LocalDate.now() + ".json";
    }
}

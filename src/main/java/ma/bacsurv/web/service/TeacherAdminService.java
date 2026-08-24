package ma.bacsurv.web.service;

import ma.bacsurv.io.TeacherCsv;
import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.CenterEntity;
import ma.bacsurv.web.persistence.CenterRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.UnavailabilityEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;

/**
 * Changing the pool one person at a time.
 *
 * <p>The spreadsheet is how a pool arrives ({@link TeacherImportService}), but
 * it is not how it is kept: a teacher changes subject in October, somebody was
 * entered with the wrong établissement, a name is misspelt. Re-importing forty
 * five rows to fix one of them is the sort of thing that made the previous
 * application unusable.
 *
 * <p>The matricule (رقم التأجير) is deliberately not editable. It is the
 * identity every past session was recorded against, so changing it would
 * silently detach a teacher from their own history and, with it, from the
 * privilege queue. A matricule entered wrongly is corrected by removing the
 * teacher and adding them again — which the history check below only permits
 * while there is no history to lose.
 */
@Service
public class TeacherAdminService {

    private final CenterRepository centers;
    private final TeacherRepository teachers;
    private final AssignmentRepository assignments;
    private final CatalogueService catalogue;

    public TeacherAdminService(CenterRepository centers, TeacherRepository teachers,
                               AssignmentRepository assignments, CatalogueService catalogue) {
        this.centers = centers;
        this.teachers = teachers;
        this.assignments = assignments;
        this.catalogue = catalogue;
    }

    /** What an administrator can state about a teacher. */
    public record Details(String matricule, String name, String subject,
                          String establishment, String gender) {}

    @Transactional
    public void add(long centerId, Details details) {
        CenterEntity center = center(centerId);
        String matricule = required(details.matricule(), "teacher.matricule");
        String name = required(details.name(), "teacher.name");
        String subject = required(details.subject(), "teacher.subject");

        teachers.findByCenterIdAndMatricule(centerId, matricule).ifPresent(existing -> {
            throw new IllegalArgumentException("teacher.matricule.exists");
        });

        // reference mirrors the matricule, as the import does: one identity,
        // not two to keep in step
        catalogue.rememberSubject(centerId, subject);
        teachers.save(new TeacherEntity(center, matricule, matricule, name, subject,
                blankToNull(details.establishment()), gender(details.gender())));
    }

    /** Everything but the matricule, which is the identity rather than a field. */
    @Transactional
    public void edit(long centerId, String matricule, Details details) {
        TeacherEntity teacher = teacher(centerId, matricule);
        catalogue.rememberSubject(centerId, details.subject());
        teacher.update(teacher.getReference(),
                required(details.name(), "teacher.name"),
                required(details.subject(), "teacher.subject"),
                blankToNull(details.establishment()),
                gender(details.gender()));
    }

    /**
     * A day, or a half of one, on which a teacher cannot be given a duty.
     *
     * <p>Null times mean the whole day. A séance is expressed as its hours
     * rather than as "matin" or "après-midi", because a centre's morning is a
     * convention and an épreuve's hours are a fact.
     */
    public record Absence(Long id, LocalDate date, LocalTime startTime, LocalTime endTime) {}

    @Transactional(readOnly = true)
    public List<Absence> absencesOf(long centerId, String matricule) {
        return teacher(centerId, matricule).getUnavailabilities().stream()
                .map(row -> new Absence(row.getId(), row.getDate(),
                        row.getStartTime(), row.getEndTime()))
                .sorted(Comparator.comparing(Absence::date)
                        .thenComparing(absence -> absence.startTime() == null
                                ? LocalTime.MIN : absence.startTime()))
                .toList();
    }

    /**
     * Replaces everything known about when a teacher is away.
     *
     * <p>The whole list is sent rather than one addition at a time: an
     * administrator correcting a date thinks of it as "these are the days he is
     * away", and a screen that has to remember which rows it removed is a screen
     * that eventually removes the wrong one.
     *
     * <p>These are absences known in advance — a teacher who says in May that he
     * is away on 5 June. Somebody who fails to turn up on the morning is not
     * this: that is a distribution to repair on the spot, not a fact to record.
     */
    @Transactional
    public void replaceAbsences(long centerId, String matricule, List<Absence> absences) {
        TeacherEntity teacher = teacher(centerId, matricule);
        List<UnavailabilityEntity> rows = new ArrayList<>();
        for (Absence absence : absences == null ? List.<Absence>of() : absences) {
            if (absence == null || absence.date() == null) {
                throw new IllegalArgumentException("absence.date.required");
            }
            LocalTime start = absence.startTime();
            LocalTime end = absence.endTime();
            if ((start == null) != (end == null)) {
                throw new IllegalArgumentException("absence.hours.incomplete");
            }
            if (start != null && !start.isBefore(end)) {
                throw new IllegalArgumentException("absence.hours.backwards");
            }
            rows.add(new UnavailabilityEntity(teacher, absence.date(), start, end));
        }
        teacher.replaceUnavailabilities(rows);
    }

    /**
     * Removing is refused once the teacher has served, because the record of
     * who has already had a réserve or a permanence is what keeps the next
     * session fair. Somebody who has left the establishment still has to be
     * counted for the sessions they worked.
     */
    @Transactional
    public void remove(long centerId, String matricule) {
        TeacherEntity teacher = teacher(centerId, matricule);
        if (assignments.countByTeacherId(teacher.getId()) > 0) {
            throw new IllegalArgumentException("teacher.hasHistory");
        }
        teachers.delete(teacher);
    }

    private CenterEntity center(long centerId) {
        return centers.findById(centerId)
                .orElseThrow(() -> new IllegalArgumentException("center.unknown"));
    }

    private TeacherEntity teacher(long centerId, String matricule) {
        return teachers.findByCenterIdAndMatricule(centerId, String.valueOf(matricule).trim())
                .orElseThrow(() -> new IllegalArgumentException("teacher.unknown"));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + ".required");
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** "F" becomes FEMALE here rather than an exception at solve time. */
    private static String gender(String raw) {
        return TeacherCsv.normaliseGender(raw);
    }
}

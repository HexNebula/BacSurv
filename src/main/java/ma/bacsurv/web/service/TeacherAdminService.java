package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.AssignmentRepository;
import ma.bacsurv.web.persistence.CenterEntity;
import ma.bacsurv.web.persistence.CenterRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public TeacherAdminService(CenterRepository centers, TeacherRepository teachers,
                               AssignmentRepository assignments) {
        this.centers = centers;
        this.teachers = teachers;
        this.assignments = assignments;
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
        teachers.save(new TeacherEntity(center, matricule, matricule, name, subject,
                blankToNull(details.establishment()), blankToNull(details.gender())));
    }

    /** Everything but the matricule, which is the identity rather than a field. */
    @Transactional
    public void edit(long centerId, String matricule, Details details) {
        TeacherEntity teacher = teacher(centerId, matricule);
        teacher.update(teacher.getReference(),
                required(details.name(), "teacher.name"),
                required(details.subject(), "teacher.subject"),
                blankToNull(details.establishment()),
                blankToNull(details.gender()));
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
}

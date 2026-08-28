package ma.bacsurv.web.service;

import ma.bacsurv.web.persistence.CenterEntity;
import ma.bacsurv.web.persistence.CenterRepository;
import ma.bacsurv.web.persistence.OperationRepository;
import ma.bacsurv.web.persistence.SchoolYearEntity;
import ma.bacsurv.web.persistence.SchoolYearRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * The school years of a centre, and who is in the pool of each.
 *
 * <p>A year is what a pool and a set of sessions belong to. It is also what
 * makes a departure sayable: a teacher who moves to another school in July is
 * not deleted — that would take the turns he is owed out of the year he served
 * — he simply is not a member of the years that follow.
 *
 * <p>September is one editing session and no retyping. The new year starts from
 * the previous year's list, the administrator takes out those who left and adds
 * those who arrived, and everybody else is untouched.
 */
@Service
public class SchoolYearService {

    private final SchoolYearRepository years;
    private final CenterRepository centers;
    private final TeacherRepository teachers;
    private final OperationRepository operations;

    public SchoolYearService(SchoolYearRepository years, CenterRepository centers,
                             TeacherRepository teachers, OperationRepository operations) {
        this.years = years;
        this.centers = centers;
        this.teachers = teachers;
        this.operations = operations;
    }

    /** A year of the centre, with what it holds. */
    public record YearView(Long id, String label, int teacherCount, int sessionCount,
                           boolean current) {}

    @Transactional(readOnly = true)
    public List<YearView> yearsOf(long centerId) {
        List<SchoolYearEntity> all = years.ofCenter(centerId);
        Long currentId = all.isEmpty() ? null : all.getFirst().getId();

        return all.stream().map(year -> new YearView(year.getId(), year.getLabel(),
                teachers.findPoolOfYear(year.getId()).size(),
                (int) operations.findAllWithCenter().stream()
                        .filter(o -> o.getSchoolYear().getId().equals(year.getId())).count(),
                year.getId().equals(currentId))).toList();
    }

    /**
     * The year the centre is working in: the most recent one it has.
     *
     * <p>The label sorts as text and 2027-2028 follows 2026-2027, so "most
     * recent" needs no flag to be kept in step — one less thing to forget to
     * update in September.
     */
    @Transactional(readOnly = true)
    public SchoolYearEntity current(long centerId) {
        return years.ofCenter(centerId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("year.none"));
    }

    /**
     * Opens a school year, carrying the previous year's pool into it.
     *
     * <p>Carrying the list is the point: a centre has forty-five teachers and
     * three or four of them change in a summer. Starting from a blank page
     * every September would mean retyping forty-one people to express four
     * departures.
     */
    @Transactional
    public Long open(long centerId, String label) {
        CenterEntity center = centers.findById(centerId).orElseThrow(
                () -> new IllegalArgumentException("center.unknown"));
        String cleaned = requireLabel(label);

        years.findByCenterIdAndLabel(centerId, cleaned).ifPresent(existing -> {
            throw new RefusedException("year.exists", cleaned);
        });

        List<SchoolYearEntity> existing = years.ofCenter(centerId);
        SchoolYearEntity opened = years.save(new SchoolYearEntity(center, cleaned));

        // the newest year before this one is the list worth carrying: an older
        // one would bring back people who have already left
        existing.stream()
                .filter(year -> year.getLabel().compareTo(cleaned) < 0)
                .findFirst()
                .ifPresent(previous -> {
                    for (TeacherEntity teacher : teachers.findPoolOfYear(previous.getId())) {
                        teacher.joinYear(opened);
                    }
                });
        return opened.getId();
    }

    /**
     * Takes a teacher out of a year's pool — he has left the establishment.
     *
     * <p>Not a deletion. His row, his matricule and every duty he was given
     * stay where they are, because the year he served is a record of what
     * happened. He is only absent from the pool of this year onwards.
     */
    @Transactional
    public void removeFromYear(long schoolYearId, String matricule) {
        SchoolYearEntity year = year(schoolYearId);
        TeacherEntity teacher = teacher(year.getCenter().getId(), matricule);
        if (!teacher.isInYear(year)) throw new IllegalArgumentException("teacher.notInYear");
        teacher.leaveYear(year);
        teacher.getCenter().touch();
    }

    /** Puts a teacher into a year's pool — he has arrived, or he is back. */
    @Transactional
    public void addToYear(long schoolYearId, String matricule) {
        SchoolYearEntity year = year(schoolYearId);
        TeacherEntity teacher = teacher(year.getCenter().getId(), matricule);
        teacher.joinYear(year);
        teacher.getCenter().touch();
    }

    /** The school year a date falls in — it turns over in September. */
    public static String labelOf(LocalDate date) {
        return SchoolYearEntity.labelOf(date);
    }

    /**
     * Finds the year a session's dates fall in, opening it if the centre has
     * not reached it yet. A session entered for June 2028 should not have to be
     * preceded by a separate act of housekeeping.
     */
    @Transactional
    public SchoolYearEntity forDate(CenterEntity center, LocalDate date) {
        String label = labelOf(date);
        return years.findByCenterIdAndLabel(center.getId(), label)
                .orElseGet(() -> years.save(new SchoolYearEntity(center, label)));
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("year.label.required");
        String cleaned = label.trim();
        // 2026-2027, and the second half must follow the first: a typo here
        // files a whole year of work under a name nobody will look under
        if (!cleaned.matches("\\d{4}-\\d{4}")) throw new IllegalArgumentException("year.label.format");
        int from = Integer.parseInt(cleaned.substring(0, 4));
        int to = Integer.parseInt(cleaned.substring(5));
        if (to != from + 1) throw new IllegalArgumentException("year.label.span");
        return cleaned;
    }

    private SchoolYearEntity year(long id) {
        return years.findById(id).orElseThrow(() -> new IllegalArgumentException("year.unknown"));
    }

    /**
     * The matricule is the key everywhere else a teacher is addressed, and it
     * is what a screen holds. Looking him up by it also confines the search to
     * the centre that owns the year, so a matricule of another centre is simply
     * not found rather than quietly enrolled.
     */
    private TeacherEntity teacher(Long centerId, String matricule) {
        if (matricule == null || matricule.isBlank()) {
            throw new IllegalArgumentException("teacher.matricule.required");
        }
        return teachers.findByCenterIdAndMatricule(centerId, matricule.trim())
                .orElseThrow(() -> new IllegalArgumentException("teacher.unknown"));
    }
}

package ma.bacsurv.web.service;

import ma.bacsurv.io.TeacherCsv;
import ma.bacsurv.web.persistence.CenterEntity;
import ma.bacsurv.web.persistence.CenterRepository;
import ma.bacsurv.web.persistence.TeacherEntity;
import ma.bacsurv.web.persistence.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Imports a teacher pool from a spreadsheet export.
 *
 * Nothing is written until the administrator has seen what will change: the
 * preview says which teachers are new, which ones change and how, which rows
 * are already correct, and which rows could not be read.
 */
@Service
public class TeacherImportService {

    /** What importing this file would do to one teacher. */
    /**
     * A teacher as a screen reads them. {@code absences} is how many days the
     * teacher is known to be away — the pool has to show who cannot be given a
     * duty, and a file being previewed states nothing about that, so a row that
     * has not been saved yet reports none.
     */
    public record Change(String matricule, String name, String nameFr, String subject,
                         String establishment, String corps, String gender,
                         String was, int absences) {}

    public record Preview(long centerId, String centerName,
                          List<Change> created, List<Change> updated, List<Change> unchanged,
                          List<TeacherCsv.RowError> errors) {

        public int total() {
            return created.size() + updated.size() + unchanged.size();
        }

        public boolean hasChanges() {
            return !created.isEmpty() || !updated.isEmpty();
        }
    }

    private final CenterRepository centers;
    private final TeacherRepository teachers;
    private final CatalogueService catalogue;
    private final SchoolYearService schoolYears;

    public TeacherImportService(CenterRepository centers, TeacherRepository teachers,
                                CatalogueService catalogue, SchoolYearService schoolYears) {
        this.centers = centers;
        this.teachers = teachers;
        this.catalogue = catalogue;
        this.schoolYears = schoolYears;
    }

    @Transactional(readOnly = true)
    public Preview preview(long centerId, String csv) {
        CenterEntity center = center(centerId);
        TeacherCsv.Parsed parsed = new TeacherCsv().parse(csv);

        List<Change> created = new ArrayList<>();
        List<Change> updated = new ArrayList<>();
        List<Change> unchanged = new ArrayList<>();

        for (TeacherCsv.Row row : parsed.rows()) {
            var existing = teachers.findByCenterIdAndMatricule(centerId, row.matricule());
            Change change = new Change(row.matricule(), row.name(), row.nameFr(), row.subject(),
                    row.establishment(), row.corps(), row.gender(),
                    existing.map(this::describe).orElse(null),
                    existing.map(t -> t.getUnavailabilities().size()).orElse(0));
            if (existing.isEmpty()) {
                created.add(change);
            } else if (differs(existing.get(), row)) {
                updated.add(change);
            } else {
                unchanged.add(change);
            }
        }
        return new Preview(center.getId(), center.getName(),
                List.copyOf(created), List.copyOf(updated), List.copyOf(unchanged),
                parsed.errors());
    }

    /** Applies the same file the preview described; returns that description. */
    @Transactional
    public Preview apply(long centerId, String csv) {
        CenterEntity center = center(centerId);
        Preview preview = preview(centerId, csv);

        // the file describes who is here this year, so its rows join this year
        var year = schoolYears.current(centerId);

        for (TeacherCsv.Row row : new TeacherCsv().parse(csv).rows()) {
            // a spreadsheet may name a subject the centre has not listed yet;
            // recording it keeps the import from failing over a missing entry
            catalogue.rememberSubject(centerId, row.subject());
            TeacherEntity teacher = teachers.findByCenterIdAndMatricule(centerId, row.matricule())
                    .map(existing -> {
                        // a file that does not carry the French name or the
                        // corps says nothing about them; it must not erase what
                        // somebody typed by hand between two imports
                        existing.update(existing.getReference(), row.name(),
                                row.nameFr() == null ? existing.getNameFr() : row.nameFr(),
                                row.subject(), blankToNull(row.establishment()),
                                row.corps() == null ? existing.getCorps() : row.corps(),
                                row.gender());
                        return existing;
                    })
                    .orElseGet(() -> teachers.save(new TeacherEntity(center, row.matricule(),
                            row.matricule(), row.name(), row.nameFr(), row.subject(),
                            blankToNull(row.establishment()), row.corps(), row.gender())));
            // a matricule already in the register is the same person coming
            // back, not a new one: he rejoins, keeping everything he did before
            teacher.joinYear(year);
        }
        if (preview.hasChanges()) center.touch();
        return preview;
    }

    /**
     * Existing pool of a centre, for the page that lists it: the teachers of
     * the year in progress. Somebody who left last summer is still in the
     * centre's register and in every archive of the years he served, but he is
     * not on the list of people to give work to.
     */
    @Transactional(readOnly = true)
    public List<Change> pool(long centerId) {
        return teachers.findPoolOfYear(schoolYears.current(centerId).getId()).stream()
                .map(t -> new Change(t.getMatricule(), t.getName(), t.getNameFr(),
                        t.getSubject(), t.getEstablishment(), t.getCorps(), t.getGender(),
                        null, t.getUnavailabilities().size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CenterView> centers() {
        return centers.findAllByOrderByNameAsc().stream()
                .map(c -> new CenterView(c.getId(), c.getName(),
                        teachers.findPoolOfYear(schoolYears.current(c.getId()).getId()).size()))
                .toList();
    }

    private CenterEntity center(long centerId) {
        return centers.findById(centerId)
                .orElseThrow(() -> new IllegalArgumentException("no center with id " + centerId));
    }

    /**
     * A column the file does not have is not a difference. {@code nameFr} and
     * {@code corps} arrive null when the sheet omits them, and apply() leaves
     * what is stored alone in that case — so the preview must not promise a
     * change that will not happen.
     */
    private boolean differs(TeacherEntity existing, TeacherCsv.Row row) {
        return !equal(existing.getName(), row.name())
                || !equal(existing.getSubject(), row.subject())
                || !equal(existing.getEstablishment(), blankToNull(row.establishment()))
                || !equal(existing.getGender(), row.gender())
                || (row.nameFr() != null && !equal(existing.getNameFr(), row.nameFr()))
                || (row.corps() != null && !equal(existing.getCorps(), row.corps()));
    }

    private String describe(TeacherEntity teacher) {
        return String.join(" · ", teacher.getName(), teacher.getSubject(),
                teacher.getEstablishment() == null ? "—" : teacher.getEstablishment());
    }

    private static boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

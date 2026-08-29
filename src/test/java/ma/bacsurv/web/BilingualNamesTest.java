package ma.bacsurv.web;

import ma.bacsurv.web.persistence.CenterRepository;
import ma.bacsurv.web.persistence.TeacherRepository;
import ma.bacsurv.web.service.CatalogueService;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.CenterExportService;
import ma.bacsurv.web.service.TeacherAdminService;
import ma.bacsurv.web.service.TeacherAdminService.Details;
import ma.bacsurv.web.service.TeacherImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A centre works from Arabic paperwork and sometimes has to produce a French
 * document. So everything that has a name has two, and the ministry's own list
 * has one column this application did not hold: السلك, the corps.
 *
 * <p>What these tests are really guarding is the division between the two
 * names. The Arabic one is what teachers and épreuves store and what the
 * solver matches on; the French one is a label and must never be able to
 * reach a comparison. A second name that anything joined on would let a
 * teacher stored as « Mathématiques » stop matching an épreuve stored as
 * « الرياضيات », and the own-subject rule would quietly stop applying — with
 * no error anywhere, just a distribution that is wrong.
 */
@SpringBootTest
class BilingualNamesTest {

    @Autowired TeacherAdminService admin;
    @Autowired TeacherImportService imports;
    @Autowired CatalogueService catalogue;
    @Autowired CenterAdminService centers;
    @Autowired CenterExportService exports;
    @Autowired TeacherRepository teachers;
    @Autowired CenterRepository centreRepo;

    private long centre() {
        return centers.createCenter("ثانوية الاختبار " + System.nanoTime());
    }

    // ------------------------------------------------------------- the teacher

    @Test
    void aTeacherCarriesBothNamesAndACorps() {
        long centre = centre();
        admin.add(centre, new Details("D712001", "عبد الله بنعلي", "Abdellah Benali",
                "الرياضيات", "ثانوية ابن سينا", "ثانوي تأهيلي", "M"));

        var stored = teachers.findByCenterIdAndMatricule(centre, "D712001").orElseThrow();
        assertEquals("عبد الله بنعلي", stored.getName());
        assertEquals("Abdellah Benali", stored.getNameFr());
        assertEquals("ثانوي تأهيلي", stored.getCorps());
        // the corps says the level of the school, the établissement says which
        // school: borrowing from another lycée is a real case and neither
        // field stands in for the other
        assertEquals("ثانوية ابن سينا", stored.getEstablishment());
    }

    @Test
    void theFrenchNameAndTheCorpsMayBeAbsent() {
        long centre = centre();
        admin.add(centre, new Details("D712002", "فاطمة العلوي", "الرياضيات", null, "F"));

        var stored = teachers.findByCenterIdAndMatricule(centre, "D712002").orElseThrow();
        assertEquals("فاطمة العلوي", stored.getName());
        assertNull(stored.getNameFr());
        assertNull(stored.getCorps());
    }

    /**
     * The point of the whole change, and the thing most likely to be undone by
     * accident later: filling in paperwork is not a change to the problem.
     *
     * <p>Before this, editing a teacher touched the centre unconditionally, so
     * typing a French name told every session its distribution was out of date.
     * An administrator who is told that forty times while transcribing a list
     * learns to ignore the warning — and then ignores the one that matters.
     */
    @Test
    void typingAFrenchNameDoesNotMakeDistributionsStale() {
        long centre = centre();
        admin.add(centre, new Details("D712003", "سعيد الإدريسي", "الفيزياء والكيمياء", null, "M"));
        Instant before = centreRepo.findById(centre).orElseThrow().getChangedAt();

        admin.edit(centre, "D712003", new Details("D712003", "سعيد الإدريسي", "Said El Idrissi",
                "الفيزياء والكيمياء", "ثانوية النهضة", "ثانوي إعدادي", "M"));

        assertEquals(before, centreRepo.findById(centre).orElseThrow().getChangedAt(),
                "a French name, an établissement and a corps are printed, never compared");
    }

    /** The other half of it: a subject does reach the solver, so it still does. */
    @Test
    void changingTheSubjectStillMakesDistributionsStale() {
        long centre = centre();
        admin.add(centre, new Details("D712004", "كريمة الزهراوي", "الفيزياء والكيمياء", null, "F"));
        Instant before = centreRepo.findById(centre).orElseThrow().getChangedAt();

        admin.edit(centre, "D712004", new Details("D712004", "كريمة الزهراوي", null,
                "الرياضيات", null, null, "F"));

        assertTrue(centreRepo.findById(centre).orElseThrow().getChangedAt().isAfter(before),
                "the subject is matched against an épreuve, so every distribution is out of date");
    }

    @Test
    void changingTheGenderStillMakesDistributionsStale() {
        long centre = centre();
        admin.add(centre, new Details("D712005", "حسن الوردي", "الرياضيات", null, "M"));
        Instant before = centreRepo.findById(centre).orElseThrow().getChangedAt();

        admin.edit(centre, "D712005", new Details("D712005", "حسن الوردي", null,
                "الرياضيات", null, null, "F"));

        assertTrue(centreRepo.findById(centre).orElseThrow().getChangedAt().isAfter(before),
                "the mixed-pair preference reads the gender");
    }

    // -------------------------------------------------------------- the import

    @Test
    void theImportReadsTheMinistryColumns() {
        long centre = centre();
        String csv = """
                رقم التأجير;الاسم الكامل;الاسم بالفرنسية;مادة التخصص;السلك;الجنس
                D713001;محمد أمين الفاسي;Mohamed Amine El Fassi;الرياضيات;ثانوي تأهيلي;ذكر
                """;
        imports.apply(centre, csv);

        var stored = teachers.findByCenterIdAndMatricule(centre, "D713001").orElseThrow();
        assertEquals("محمد أمين الفاسي", stored.getName());
        assertEquals("Mohamed Amine El Fassi", stored.getNameFr());
        assertEquals("الرياضيات", stored.getSubject());
        assertEquals("ثانوي تأهيلي", stored.getCorps());
        assertEquals("MALE", stored.getGender());
    }

    /**
     * « الاسم » is the full name and « الاسم بالفرنسية » is the French one, and
     * the first is a prefix of the second. Exact spellings are tried before
     * containment for precisely this reason; without that the French column
     * would be read as the name and the Arabic name would be lost.
     */
    @Test
    void theFrenchColumnIsNotMistakenForTheArabicOne() {
        long centre = centre();
        imports.apply(centre, """
                رقم التأجير;الاسم بالفرنسية;الاسم;المادة
                D713002;Karim Tazi;كريم التازي;الإنجليزية
                """);

        var stored = teachers.findByCenterIdAndMatricule(centre, "D713002").orElseThrow();
        assertEquals("كريم التازي", stored.getName());
        assertEquals("Karim Tazi", stored.getNameFr());
    }

    /**
     * A sheet without those columns says nothing about them. It must not erase
     * what somebody typed by hand between two imports — which is the ordinary
     * case, since the ministry list is where the French names are least likely
     * to be.
     */
    @Test
    void animportWithoutTheseColumnsLeavesThemAlone() {
        long centre = centre();
        admin.add(centre, new Details("D713003", "نادية بنجلون", "Nadia Benjelloun",
                "الإنجليزية", null, "ثانوي تأهيلي", "F"));

        imports.apply(centre, """
                رقم التأجير;الاسم الكامل;المادة
                D713003;نادية بنجلون;الفرنسية
                """);

        var stored = teachers.findByCenterIdAndMatricule(centre, "D713003").orElseThrow();
        assertEquals("الفرنسية", stored.getSubject(), "the file did state a subject");
        assertEquals("Nadia Benjelloun", stored.getNameFr(), "the file said nothing about it");
        assertEquals("ثانوي تأهيلي", stored.getCorps(), "nor about this");
    }

    /** And the preview must not promise a change that apply() will not make. */
    @Test
    void thePreviewDoesNotReportAnAbsentColumnAsAChange() {
        long centre = centre();
        admin.add(centre, new Details("D713004", "يوسف الحسني", "Youssef El Hassani",
                "الفلسفة", null, "ثانوي تأهيلي", "M"));

        // the gender is spelt out because an absent الجنس column is a change:
        // apply() clears it, and the preview is right to say so. What is being
        // isolated here is the French name and the corps, which are not.
        var preview = imports.preview(centre, """
                رقم التأجير;الاسم الكامل;المادة;الجنس
                D713004;يوسف الحسني;الفلسفة;ذكر
                """);

        assertEquals(1, preview.unchanged().size());
        assertTrue(preview.updated().isEmpty());
    }

    // ----------------------------------------------------------- the catalogue

    @Test
    void aSubjectAndAFiliereCarryAFrenchLabel() {
        long centre = centre();
        catalogue.addSubject(centre, "الرياضيات", "Mathématiques");
        catalogue.addStream(centre, "العلوم التجريبية", "Sciences expérimentales", "BAC2");

        var subject = catalogue.subjectsOf(centre).getFirst();
        assertEquals("الرياضيات", subject.name());
        assertEquals("Mathématiques", subject.nameFr());

        var stream = catalogue.streamsOf(centre).getFirst();
        assertEquals("العلوم التجريبية", stream.name());
        assertEquals("Sciences expérimentales", stream.nameFr());
        assertEquals("BAC2", stream.level());
    }

    /**
     * Renaming rewrites every teacher and épreuve that stored the name, because
     * that string is what the solver matches on. Relabelling touches nothing
     * else at all — which is why it is a separate act and not a second argument
     * to the same one.
     */
    @Test
    void relabellingDoesNotRewriteAnythingAndDoesNotMakeDistributionsStale() {
        long centre = centre();
        Long subjectId = catalogue.addSubject(centre, "الرياضيات", "Maths");
        admin.add(centre, new Details("D714001", "عمر الشرقاوي", "الرياضيات", null, "M"));
        Instant before = centreRepo.findById(centre).orElseThrow().getChangedAt();

        catalogue.renameSubject(subjectId, "الرياضيات", "Mathématiques", true);

        assertEquals("الرياضيات",
                teachers.findByCenterIdAndMatricule(centre, "D714001").orElseThrow().getSubject(),
                "the teacher still names the subject the solver will match");
        assertEquals("Mathématiques", catalogue.subjectsOf(centre).getFirst().nameFr());
        assertEquals(before, centreRepo.findById(centre).orElseThrow().getChangedAt(),
                "a label reaches no rule, so no distribution is out of date");
    }

    @Test
    void renamingStillRewritesTheRowsThatStoredTheName() {
        long centre = centre();
        Long subjectId = catalogue.addSubject(centre, "الرياضيات", "Mathématiques");
        admin.add(centre, new Details("D714002", "ليلى بركة", "الرياضيات", null, "F"));

        catalogue.renameSubject(subjectId, "الرياضيات والإحصاء");

        assertEquals("الرياضيات والإحصاء",
                teachers.findByCenterIdAndMatricule(centre, "D714002").orElseThrow().getSubject(),
                "otherwise the teacher stops matching his own épreuve");
    }

    /** A screen that edits only the Arabic name must not blank the label. */
    @Test
    void renamingWithoutSendingALabelKeepsTheOneThatIsThere() {
        long centre = centre();
        Long subjectId = catalogue.addSubject(centre, "الفيزياء", "Physique");

        catalogue.renameSubject(subjectId, "الفيزياء والكيمياء");

        assertEquals("Physique", catalogue.subjectsOf(centre).getFirst().nameFr());
    }

    // -------------------------------------------------------------- the backup

    @Test
    void theBackupCarriesBothNamesAndTheCorps() {
        long centre = centre();
        catalogue.addSubject(centre, "الرياضيات", "Mathématiques");
        catalogue.addStream(centre, "العلوم الرياضية", "Sciences mathématiques", "BAC2");
        admin.add(centre, new Details("D715001", "إبراهيم الناصري", "Brahim Naciri",
                "الرياضيات", "ثانوية الأطلس", "ثانوي تأهيلي", "M"));

        var export = exports.of(centre);

        assertEquals(2, export.version(), "version 1 files hold none of these");
        var teacher = export.teachers().getFirst();
        assertEquals("إبراهيم الناصري", teacher.name());
        assertEquals("Brahim Naciri", teacher.nameFr());
        assertEquals("ثانوي تأهيلي", teacher.corps());
        assertEquals("Mathématiques", export.catalogue().subjects().getFirst().nameFr());
        assertEquals("Sciences mathématiques", export.catalogue().streams().getFirst().nameFr());
    }
}

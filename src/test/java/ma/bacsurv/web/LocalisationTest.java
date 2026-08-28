package ma.bacsurv.web;

import ma.bacsurv.web.config.LocaleConfig;
import ma.bacsurv.web.service.CenterAdminService;
import ma.bacsurv.web.service.TeacherAdminService;
import ma.bacsurv.web.service.TeacherAdminService.Details;
import ma.bacsurv.web.service.TimetableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import org.springframework.core.io.ClassPathResource;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The interface must work in both administrative languages, Arabic included. */
@SpringBootTest
@AutoConfigureMockMvc
class LocalisationTest {

    @Autowired MockMvc mvc;
    @Autowired MessageSource messages;
    @Autowired CenterAdminService centers;
    @Autowired TeacherAdminService teacherAdmin;
    @Autowired TimetableService timetable;

    private static final LocalDate DAY = LocalDate.of(2026, 6, 4);

    /**
     * An apostrophe in a message that carries arguments must be doubled.
     *
     * <p>MessageFormat reads a lone {@code \'} as the start of a quoted run, so
     * "L'année {0} existe déjà" reaches the screen as "Lannée {0} existe déjà":
     * the apostrophe eaten and the argument never substituted. French is full
     * of apostrophes, every one of these sentences is shown to somebody, and
     * nothing else catches it — a unit test asserting on the key passes, and
     * the fault only appears in a browser.
     */
    @Test
    void frenchApostrophesSurviveArgumentSubstitution() throws Exception {
        Properties fr = new Properties();
        try (var in = new java.io.InputStreamReader(
                new ClassPathResource("messages_fr.properties").getInputStream(),
                StandardCharsets.UTF_8)) {
            fr.load(in);
        }

        List<String> broken = fr.stringPropertyNames().stream()
                .filter(key -> fr.getProperty(key).contains("{"))
                .filter(key -> fr.getProperty(key).replace("\'\'", "").contains("\'"))
                .sorted()
                .toList();

        assertTrue(broken.isEmpty(),
                "apostrophes must be doubled in these keys, or the argument is swallowed: "
                        + broken);
    }

    /**
     * The interface is React now, so the server's share of the two languages
     * is what it writes back when it refuses something. That sentence has to
     * arrive translated, because the interface shows it as it comes rather
     * than inventing its own wording.
     */
    @Test
    void aRefusalIsWrittenInTheCallersLanguage() throws Exception {
        mvc.perform(post("/api/centers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Le nom du centre est obligatoire")));

        // read as UTF-8: MockMvc decodes with the response's declared charset,
        // which for JSON is not stated and falls back to Latin-1
        String arabic = mvc.perform(post("/api/centers")
                        .header("Accept-Language", "ar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(arabic.contains("اسم المركز إجباري"),
                "a caller asking for Arabic must be refused in Arabic: " + arabic);
    }

    @Test
    void anUnknownLanguageFallsBackToFrench() throws Exception {
        mvc.perform(post("/api/centers")
                        .header("Accept-Language", "de")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Le nom du centre est obligatoire")));
    }

    /**
     * Moroccan Arabic writes numbers 0123456789. Plain {@code ar} would render
     * a count of 45 as ٤٥, which is not what an administration here puts on
     * paper.
     */
    @Test
    void arabicNumbersUseTheDigitsUsedInMorocco() {
        assertEquals("45", new java.text.MessageFormat("{0}", LocaleConfig.ARABIC)
                .format(new Object[]{45}));
    }

    /**
     * A session that cannot be staffed is the one refusal an administrator is
     * most likely to meet, and it used to answer in English — the exception
     * wrote its own sentence in the code while every other refusal went
     * through the bundles.
     */
    @Test
    void aSolveThatCannotBeStaffedIsRefusedInTheCallersLanguage() throws Exception {
        long session = sessionWithNoSpecialistFor("Philosophie");

        String french = mvc.perform(post("/api/operations/" + session + "/solve"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(french.contains("Aucun spécialiste de Philosophie"),
                "the refusal must name the subject, in French: " + french);
        assertFalse(french.contains("no schedule exists"),
                "the English sentence belongs in the log, not on the screen: " + french);

        String arabic = mvc.perform(post("/api/operations/" + session + "/solve")
                        .header("Accept-Language", "ar"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(arabic.contains("لا يوجد أستاذ متخصص"),
                "a caller asking for Arabic must be refused in Arabic: " + arabic);
    }

    /** The hour, the count it needs, and the count the centre has. */
    @Test
    void aShortageSaysHowManyPeopleAreMissing() throws Exception {
        long centre = centers.createCenter("Lycée Effectifs " + System.nanoTime());
        long session = centers.createSession(centre, "Bac", "NATIONAL_2BAC", DAY, DAY);
        centers.addRooms(centre, 4, "Salle");
        // four rooms need eight surveillants; two people cannot cover them
        for (int i = 1; i <= 2; i++) {
            teacherAdmin.add(centre, new Details("L90000" + i, "Enseignant " + i,
                    "Mathématiques", null, "M"));
        }
        examOn(centre, session, "Mathématiques");

        String french = mvc.perform(post("/api/operations/" + session + "/solve"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(french.contains("04/06/2026") && french.contains("08:00"),
                "the refusal must say which hour is short: " + french);
        assertTrue(french.contains("surveillants"), french);
    }

    /**
     * The rules screen refuses like every other screen. Its endpoint used to
     * hand back whatever the domain records said, which is English.
     */
    @Test
    void aRuleBelowTheOfficialMinimumIsRefusedInWords() throws Exception {
        long centre = centers.createCenter("Lycée Règles " + System.nanoTime());
        long session = centers.createSession(centre, "Bac", "NATIONAL_2BAC", DAY, DAY);
        String body = """
                {"defaultSurveillantsPerRoom":1,"reserveMode":"PERCENTAGE","reservePercentage":0.10,
                 "reserveFixedCount":0,"maxConsecutiveDays":3,"consecutiveDaysStrength":"SOFT",
                 "minGapMinutes":0,"ownSubjectStrength":"HARD","forbidOwnSubjectReserve":false,
                 "solveSeconds":30}""";

        mvc.perform(post("/api/operations/" + session + "/settings")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Le minimum officiel est de 2")));

        String arabic = mvc.perform(post("/api/operations/" + session + "/settings")
                        .header("Accept-Language", "ar")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(arabic.contains("الحد الأدنى الرسمي"), arabic);
    }

    /** Enough people, none of them able to sit the permanence of that subject. */
    private long sessionWithNoSpecialistFor(String subject) {
        long centre = centers.createCenter("Lycée Spécialité " + System.nanoTime());
        long session = centers.createSession(centre, "Bac", "NATIONAL_2BAC", DAY, DAY);
        centers.addRooms(centre, 2, "Salle");
        for (int i = 1; i <= 12; i++) {
            teacherAdmin.add(centre, new Details("L80000" + i, "Enseignant " + i,
                    "Mathématiques", null, "F"));
        }
        examOn(centre, session, subject);
        return session;
    }

    private void examOn(long centre, long session, String subject) {
        List<Long> rooms = centers.detail(centre).rooms().stream()
                .map(CenterAdminService.RoomView::id).toList();
        long stream = timetable.addStream(session, "Lettres", rooms);
        timetable.setExam(session, stream, subject, DAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
    }

    @Test
    void bothBundlesDefineTheSameKeys() throws Exception {
        var french = load("messages_fr.properties");
        var arabic = load("messages_ar.properties");

        var missingInArabic = new TreeSet<>(french.stringPropertyNames());
        missingInArabic.removeAll(arabic.stringPropertyNames());
        assertTrue(missingInArabic.isEmpty(), "not translated to Arabic: " + missingInArabic);

        var missingInFrench = new TreeSet<>(arabic.stringPropertyNames());
        missingInFrench.removeAll(french.stringPropertyNames());
        assertTrue(missingInFrench.isEmpty(), "missing from the French bundle: " + missingInFrench);
    }

    @Test
    void resolvesMessagesInBothLocales() {
        assertEquals("Réserve", messages.getMessage(
                "job.workload.reserve", null, LocaleConfig.FRENCH));
        assertEquals("الاحتياط", messages.getMessage(
                "job.workload.reserve", null, LocaleConfig.ARABIC));
    }

    private Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream, name + " must be on the classpath");
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}

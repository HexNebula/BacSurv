package ma.bacsurv.web;

import ma.bacsurv.web.config.LocaleConfig;
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

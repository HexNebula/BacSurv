package ma.bacsurv.web;

import ma.bacsurv.web.config.LocaleConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The interface must work in both administrative languages, Arabic included. */
@SpringBootTest
@AutoConfigureMockMvc
class LocalisationTest {

    @Autowired MockMvc mvc;
    @Autowired MessageSource messages;

    @Test
    void frenchIsTheDefault() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"fr\"")))
                .andExpect(content().string(containsString("dir=\"ltr\"")))
                .andExpect(content().string(containsString("Importer une opération")));
    }

    @Test
    void arabicRendersRightToLeft() throws Exception {
        mvc.perform(get("/").param("lang", "ar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"ar\"")))
                .andExpect(content().string(containsString("dir=\"rtl\"")))
                .andExpect(content().string(containsString("استيراد عملية")));
    }

    @Test
    void unknownLanguageFallsBackToFrench() throws Exception {
        mvc.perform(get("/").param("lang", "de"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"fr\"")));
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

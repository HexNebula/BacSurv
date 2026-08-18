package ma.bacsurv.web;

import ma.bacsurv.web.service.CenterView;
import ma.bacsurv.web.service.SolveService;
import ma.bacsurv.web.service.TeacherImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Importing a pool from a spreadsheet: the administrator sees what will change
 * before anything is written, and a bad row never costs the good ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeacherImportTest {

    @Autowired MockMvc mvc;
    @Autowired SolveService solveService;
    @Autowired TeacherImportService teacherImport;

    /** Centers are created by importing an operation, so start from one. */
    private CenterView centerNamed(String name) throws Exception {
        String sample = Files.readString(Path.of("samples", "operation-sample.json"))
                .replace("\"operation\": { \"id\": \"NAT-2026-JUIN\"",
                        "\"center\": { \"name\": \"" + name + "\" },\n"
                                + "  \"operation\": { \"id\": \"OP-" + name.hashCode() + "\"");
        solveService.upload("op.json", sample);
        return teacherImport.centers().stream()
                .filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void previewChangesNothingUntilItIsConfirmed() throws Exception {
        CenterView center = centerNamed("Centre Import A");
        int before = teacherImport.pool(center.id()).size();

        String csv = """
                matricule;nom;matiere;etablissement;genre
                D100001;Amina El Fassi RENOMMEE;Mathématiques;Lycée Al Khawarizmi;F
                D900001;Nouveau Professeur;Anglais;Lycée Ibn Sina;M
                """;

        var preview = teacherImport.preview(center.id(), csv);
        assertEquals(1, preview.created().size());
        assertEquals(1, preview.updated().size());
        assertEquals(before, teacherImport.pool(center.id()).size(), "preview must not write");

        var applied = teacherImport.apply(center.id(), csv);
        assertEquals(1, applied.created().size());
        assertEquals(before + 1, teacherImport.pool(center.id()).size());
        assertTrue(teacherImport.pool(center.id()).stream()
                        .anyMatch(t -> t.name().equals("Amina El Fassi RENOMMEE")),
                "the existing teacher was updated, not duplicated");
    }

    @Test
    void reimportingTheSameFileChangesNothing() throws Exception {
        CenterView center = centerNamed("Centre Import B");
        String csv = Files.readString(Path.of("samples", "teachers-sample.csv"));

        teacherImport.apply(center.id(), csv);
        int after = teacherImport.pool(center.id()).size();

        var second = teacherImport.preview(center.id(), csv);
        assertFalse(second.hasChanges(), "a second import of the same file is a no-op");
        assertEquals(16, second.unchanged().size());
        assertEquals(after, teacherImport.pool(center.id()).size());
    }

    @Test
    void badRowsAreReportedAndTheGoodOnesStillImport() throws Exception {
        CenterView center = centerNamed("Centre Import C");

        var preview = teacherImport.preview(center.id(), """
                matricule;nom;matiere
                D800001;Bonne Ligne;Anglais
                ;Sans matricule;Anglais
                D800002;Autre Bonne Ligne;Arabe
                """);

        assertEquals(2, preview.created().size());
        assertEquals(1, preview.errors().size());
        assertEquals(3, preview.errors().getFirst().line());
    }

    @Test
    void theBrowserFlowShowsThePreviewPage() throws Exception {
        CenterView center = centerNamed("Centre Import D");
        var file = new MockMultipartFile("file", "enseignants.csv", MediaType.TEXT_PLAIN_VALUE,
                Files.readString(Path.of("samples", "teachers-sample.csv"))
                        .getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/centers/{id}/teachers/preview", center.id()).file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aperçu de l&#39;import")))
                .andExpect(content().string(containsString("D100001")));

        mvc.perform(get("/centers/{id}/teachers", center.id()).param("lang", "ar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dir=\"rtl\"")))
                .andExpect(content().string(containsString("رقم التأجير")));
    }
}

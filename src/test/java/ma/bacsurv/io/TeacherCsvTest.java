package ma.bacsurv.io;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The file arrives as the administration exported it, not as we would like it. */
class TeacherCsvTest {

    private final TeacherCsv csv = new TeacherCsv();

    @Test
    void readsTheSampleExport() throws Exception {
        var parsed = csv.parse(Files.readString(Path.of("samples", "teachers-sample.csv")));

        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
        assertEquals(16, parsed.rows().size());

        var first = parsed.rows().getFirst();
        assertEquals("D100001", first.matricule());
        assertEquals("Amina El Fassi", first.name());
        assertEquals("Mathématiques", first.subject());
        assertEquals("Lycée Al Khawarizmi", first.establishment());
        assertEquals("FEMALE", first.gender());
    }

    @Test
    void acceptsCommasSemicolonsAndTabs() {
        assertEquals(1, csv.parse("matricule,nom,matiere\nD1,Ali,Maths").rows().size());
        assertEquals(1, csv.parse("matricule;nom;matiere\nD1;Ali;Maths").rows().size());
        assertEquals(1, csv.parse("matricule\tnom\tmatiere\nD1\tAli\tMaths").rows().size());
    }

    @Test
    void acceptsArabicAndEnglishHeaders() {
        var arabic = csv.parse("رقم التأجير;الاسم;المادة\nD1;علي;الرياضيات");
        assertEquals(1, arabic.rows().size());
        assertEquals("علي", arabic.rows().getFirst().name());

        var english = csv.parse("Matricule,Full name,Subject\nD2,Ali,Maths");
        assertEquals(1, english.rows().size());
        assertEquals("Ali", english.rows().getFirst().name());
    }

    @Test
    void toleratesByteOrderMarkAccentsAndSpacingInHeaders() {
        var parsed = csv.parse("﻿ N° Matricule ; Nom et prénom ; Matière \nD1;Ali;Maths");

        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
        assertEquals("D1", parsed.rows().getFirst().matricule());
        assertEquals("Ali", parsed.rows().getFirst().name());
    }

    @Test
    void readsQuotedFieldsContainingTheSeparator() {
        var parsed = csv.parse("matricule,nom,matiere\nD1,\"Alaoui, Ali\",Maths");

        assertEquals("Alaoui, Ali", parsed.rows().getFirst().name());
    }

    @Test
    void keepsGoodRowsAndExplainsBadOnes() {
        var parsed = csv.parse("""
                matricule;nom;matiere
                D1;Ali;Maths
                ;Sans matricule;Maths
                D2;;Maths
                D3;Sara;
                D1;Doublon;Maths
                D4;Nadia;Français
                """);

        assertEquals(2, parsed.rows().size(), "the two usable rows survive");
        assertEquals(4, parsed.errors().size());
        assertEquals(3, parsed.errors().getFirst().line(), "line numbers point at the file");
        assertTrue(parsed.errors().stream().anyMatch(e -> e.reason().equals("duplicateMatricule")
                && "D1".equals(e.detail())), "the repeated matricule is named, not described");
    }

    @Test
    void reportsAMissingRequiredColumnOnceInsteadOfEveryRow() {
        var parsed = csv.parse("matricule;etablissement\nD1;Lycée\nD2;Lycée");

        assertTrue(parsed.rows().isEmpty());
        assertEquals(2, parsed.errors().size());
        assertTrue(parsed.errors().stream().allMatch(e -> e.reason().equals("missingColumn")));
    }

    @Test
    void readsGenderInEitherLanguageAndLeavesItEmptyWhenUnclear() {
        assertEquals("MALE", csv.parse("matricule;nom;matiere;genre\nD1;A;M;Homme")
                .rows().getFirst().gender());
        assertEquals("FEMALE", csv.parse("matricule;nom;matiere;genre\nD1;A;M;أنثى")
                .rows().getFirst().gender());
        assertNull(csv.parse("matricule;nom;matiere;genre\nD1;A;M;?")
                .rows().getFirst().gender());
    }
}

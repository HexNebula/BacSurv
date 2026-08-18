package ma.bacsurv.io;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a teacher list the way it actually arrives: exported from Excel by an
 * administration, in French or Arabic, comma or semicolon separated, sometimes
 * with a byte-order mark and stray spaces.
 *
 * A bad row is reported with its line number and kept out of the import; it
 * never rejects the whole file, because a hundred good rows should not be lost
 * to one missing matricule.
 */
public final class TeacherCsv {

    /** One teacher as written in the file. */
    public record Row(int line, String matricule, String name, String subject,
                      String establishment, String gender) {}

    /** One row that could not be used, and why. */
    public record RowError(int line, String reason, String content) {}

    public record Parsed(List<Row> rows, List<RowError> errors) {

        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    /** Header spellings accepted for each field, matched loosely. */
    private static final Map<String, Set<String>> HEADERS = Map.of(
            "matricule", Set.of("matricule", "matriculenumber", "ppr", "numeromatricule",
                    "numdematricule", "رقمالتاجير", "رقمالتأجير"),
            "name", Set.of("nom", "name", "nomprenom", "nometprenom", "nomcomplet",
                    "fullname", "الاسم", "الاسمالكامل", "النسبوالاسم"),
            "subject", Set.of("matiere", "subject", "discipline", "specialite", "المادة"),
            "establishment", Set.of("etablissement", "establishment", "school", "lycee",
                    "المؤسسة"),
            "gender", Set.of("genre", "gender", "sexe", "sex", "الجنس"));

    private static final Set<String> MALE =
            Set.of("m", "h", "male", "homme", "masculin", "ذكر");
    private static final Set<String> FEMALE =
            Set.of("f", "female", "femme", "feminin", "أنثى", "انثى");

    public Parsed parse(String content) {
        List<String> lines = splitLines(stripBom(content));
        if (lines.isEmpty()) {
            return new Parsed(List.of(), List.of(new RowError(1, "empty file", "")));
        }

        char separator = detectSeparator(lines.getFirst());
        Map<String, Integer> columns = mapHeader(splitRow(lines.getFirst(), separator));

        List<RowError> errors = new ArrayList<>();
        for (String required : List.of("matricule", "name", "subject")) {
            if (!columns.containsKey(required)) {
                errors.add(new RowError(1, "missing column: " + required, lines.getFirst()));
            }
        }
        if (!errors.isEmpty()) return new Parsed(List.of(), List.copyOf(errors));

        List<Row> rows = new ArrayList<>();
        Set<String> seenMatricules = new java.util.HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            int number = i + 1;
            if (line.isBlank()) continue;

            List<String> values = splitRow(line, separator);
            String matricule = value(values, columns.get("matricule"));
            String name = value(values, columns.get("name"));
            String subject = value(values, columns.get("subject"));

            if (matricule.isBlank()) {
                errors.add(new RowError(number, "no matricule", line));
            } else if (name.isBlank()) {
                errors.add(new RowError(number, "no name", line));
            } else if (subject.isBlank()) {
                errors.add(new RowError(number, "no subject", line));
            } else if (!seenMatricules.add(matricule)) {
                errors.add(new RowError(number, "matricule appears twice: " + matricule, line));
            } else {
                rows.add(new Row(number, matricule, name, subject,
                        value(values, columns.get("establishment")),
                        gender(value(values, columns.get("gender")))));
            }
        }
        return new Parsed(List.copyOf(rows), List.copyOf(errors));
    }

    /** MALE, FEMALE, or null when the file does not say. */
    private static String gender(String raw) {
        String key = fold(raw);
        if (MALE.contains(key)) return "MALE";
        if (FEMALE.contains(key)) return "FEMALE";
        return null;
    }

    /**
     * Exact spellings win first; only then does a column count because it
     * contains a known word, which is what rescues headers like "N° Matricule"
     * without letting "Nom de l'établissement" be read as the name column.
     */
    private static Map<String, Integer> mapHeader(List<String> header) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        Set<Integer> taken = new java.util.HashSet<>();

        for (int i = 0; i < header.size(); i++) {
            String cell = fold(header.get(i));
            for (var entry : HEADERS.entrySet()) {
                if (entry.getValue().contains(cell) && !columns.containsKey(entry.getKey())) {
                    columns.put(entry.getKey(), i);
                    taken.add(i);
                }
            }
        }

        for (int i = 0; i < header.size(); i++) {
            if (taken.contains(i)) continue;
            String cell = fold(header.get(i));
            String bestField = null;
            int bestLength = 0;
            for (var entry : HEADERS.entrySet()) {
                if (columns.containsKey(entry.getKey())) continue;
                for (String spelling : entry.getValue()) {
                    if (cell.contains(spelling) && spelling.length() > bestLength) {
                        bestField = entry.getKey();
                        bestLength = spelling.length();
                    }
                }
            }
            if (bestField != null) {
                columns.put(bestField, i);
                taken.add(i);
            }
        }
        return columns;
    }

    /** Lower-cased, unaccented, spaces and punctuation removed, so "N° Matricule" matches. */
    private static String fold(String value) {
        if (value == null) return "";
        String normalised = Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalised.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "");
    }

    private static String value(List<String> values, Integer index) {
        if (index == null || index >= values.size()) return "";
        return values.get(index).trim();
    }

    /** Whichever of ; , or tab appears most in the header wins. */
    private static char detectSeparator(String header) {
        char best = ',';
        long bestCount = 0;
        for (char candidate : new char[] {';', ',', '\t'}) {
            long count = header.chars().filter(c -> c == candidate).count();
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    private static List<String> splitRow(String line, char separator) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == separator) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static List<String> splitLines(String content) {
        return List.of(content.split("\r\n|\n|\r", -1));
    }

    private static String stripBom(String content) {
        return content.startsWith("﻿") ? content.substring(1) : content;
    }
}

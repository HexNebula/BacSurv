package ma.bacsurv.io;

import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.Teacher;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InputMapperTest {

    private static final Path SAMPLE = Path.of("samples", "operation-sample.json");

    @Test
    void parsesSampleFile() {
        var parsed = new InputMapper().read(SAMPLE);

        assertEquals("NAT-2026-JUIN", parsed.operation().id());
        assertEquals(4, parsed.operation().slots().size());
        assertEquals(16, parsed.teachers().size());

        ExamSlot s1 = slot(parsed.operation().slots(), "S1");
        assertEquals(2, s1.exams().size());
        assertEquals(8, s1.surveillanceDutyCount());
        // 10% of 8 rounded up = 1 (policy fills the gap when reserveCount absent)
        assertEquals(1, s1.reserveRequirement());
        // explicit reserveCount wins over the policy
        assertEquals(2, slot(parsed.operation().slots(), "S4").reserveRequirement());
    }

    @Test
    void computesOrdinalsPerDay() {
        var parsed = new InputMapper().read(SAMPLE);
        assertEquals(1, slot(parsed.operation().slots(), "S1").ordinalInDay());
        assertEquals(2, slot(parsed.operation().slots(), "S2").ordinalInDay());
        assertEquals(1, slot(parsed.operation().slots(), "S3").ordinalInDay());
    }

    @Test
    void mapsTeacherDetails() {
        var parsed = new InputMapper().read(SAMPLE);

        Teacher t4 = teacher(parsed.teachers(), "T4");
        assertEquals(3, t4.prior(DutyRole.SURVEILLANCE));
        assertEquals(1, t4.prior(DutyRole.RESERVE));
        assertEquals(4, t4.priorTotal());

        Teacher t8 = teacher(parsed.teachers(), "T8");
        ExamSlot s1 = slot(parsed.operation().slots(), "S1");
        assertFalse(t8.isAvailable(s1), "whole-day unavailability covers S1");

        Teacher t12 = teacher(parsed.teachers(), "T12");
        assertTrue(t12.isAvailable(slot(parsed.operation().slots(), "S3")),
                "morning slot outside the 14:00-18:00 window");
        assertFalse(t12.isAvailable(slot(parsed.operation().slots(), "S4")));

        // default qualifications: permanence only for own subject
        assertTrue(t4.isQualified(DutyRole.PERMANENCE, t4.subject()));
        assertFalse(t4.isQualified(DutyRole.PERMANENCE, t8.subject()));
    }

    @Test
    void rejectsUnknownRoomReference() {
        var ex = assertThrows(InputMapper.InputException.class, () ->
                new InputMapper().map(minimal(exam("E1", List.of("NOPE")))));
        assertTrue(ex.getMessage().contains("unknown room"));
        assertTrue(ex.getMessage().contains("NOPE"));
    }

    @Test
    void rejectsBadDate() {
        var slot = new OperationInput.SlotDto("S1", "01/06/2026", "08:00", "10:00",
                List.of(exam("E1", List.of("R1"))), null);
        var ex = assertThrows(InputMapper.InputException.class, () ->
                new InputMapper().map(withSlot(slot)));
        assertTrue(ex.getMessage().contains("bad date"));
    }

    @Test
    void rejectsDuplicateTeacherId() {
        var in = new OperationInput(
                new OperationInput.OperationDto("OP", "NATIONAL_2BAC"), null,
                List.of(new OperationInput.RoomDto("R1", null)),
                List.of(new OperationInput.SlotDto("S1", "2026-06-01", "08:00", "10:00",
                        List.of(exam("E1", List.of("R1"))), null)),
                List.of(teacherDto("T1"), teacherDto("T1")));
        var ex = assertThrows(InputMapper.InputException.class, () -> new InputMapper().map(in));
        assertTrue(ex.getMessage().contains("duplicate teacher id"));
    }

    // --- helpers ---

    private static ExamSlot slot(List<ExamSlot> slots, String id) {
        return slots.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private static Teacher teacher(List<Teacher> pool, String id) {
        return pool.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    private static OperationInput.ExamDto exam(String id, List<String> rooms) {
        return new OperationInput.ExamDto(id, "Mathématiques", "Sciences", rooms, null, null);
    }

    private static OperationInput.TeacherDto teacherDto(String id) {
        return new OperationInput.TeacherDto(id, null, "Français", null, null, null, Map.of());
    }

    private static OperationInput minimal(OperationInput.ExamDto exam) {
        return withSlot(new OperationInput.SlotDto(
                "S1", "2026-06-01", "08:00", "10:00", List.of(exam), null));
    }

    private static OperationInput withSlot(OperationInput.SlotDto slot) {
        return new OperationInput(
                new OperationInput.OperationDto("OP", "NATIONAL_2BAC"), null,
                List.of(new OperationInput.RoomDto("R1", null)),
                List.of(slot),
                List.of(teacherDto("T1")));
    }
}

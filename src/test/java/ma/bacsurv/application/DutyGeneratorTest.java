package ma.bacsurv.application;

import ma.bacsurv.TestFixtures;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.DutyRole;
import ma.bacsurv.domain.ExamSlot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DutyGeneratorTest {

    private final DutyGenerator generator = new DutyGenerator();

    @Test
    void generatesSurveillancePermanenceAndReserve() {
        List<Duty> duties = generator.generate(TestFixtures.singleSlotOperation());

        // 5 rooms × 2 + 2 permanence (one per exam) + 1 reserve
        assertEquals(13, duties.size());
        assertEquals(10, count(duties, DutyRole.SURVEILLANCE));
        assertEquals(2, count(duties, DutyRole.PERMANENCE));
        assertEquals(1, count(duties, DutyRole.RESERVE));
    }

    @Test
    void permanenceIsPerExamNotPerSlot() {
        List<Duty> duties = generator.generate(TestFixtures.singleSlotOperation());
        List<Duty> permanence = duties.stream()
                .filter(d -> d.role() == DutyRole.PERMANENCE).toList();
        assertEquals(2, permanence.stream()
                .map(d -> d.exam().orElseThrow().subject()).distinct().count());
    }

    @Test
    void reserveDutiesHaveNoExamOrRoom() {
        List<Duty> duties = generator.generate(TestFixtures.singleSlotOperation());
        assertTrue(duties.stream()
                .filter(d -> d.role() == DutyRole.RESERVE)
                .allMatch(d -> d.exam().isEmpty() && d.room().isEmpty()));
    }

    @Test
    void idsAreDeterministicAcrossRegeneration() {
        List<Duty> first = generator.generate(TestFixtures.singleSlotOperation());
        List<Duty> second = generator.generate(TestFixtures.singleSlotOperation());
        assertEquals(first.stream().map(Duty::id).toList(),
                     second.stream().map(Duty::id).toList());
    }

    @Test
    void reservePolicySuggestsTenPercentCeiled() {
        ExamSlot slot = TestFixtures.multiSubjectSlot(); // 10 surveillance duties
        assertEquals(1, ReservePolicy.officialDefault().suggest(slot));
        // 40 supervisors → 4 reservists (user's worked example)
        assertEquals(4, new ReservePolicy.PercentageOfSurveillance(0.10)
                .suggest(slotWithSurveillanceDuties(40)));
    }

    private ExamSlot slotWithSurveillanceDuties(int count) {
        // 20 rooms × 2 surveillants = 40
        var rooms = new java.util.ArrayList<ma.bacsurv.domain.Room>();
        for (int i = 0; i < count / 2; i++)
            rooms.add(new ma.bacsurv.domain.Room("X" + i, "Room X" + i));
        var exam = ma.bacsurv.domain.Exam.of("E-X", TestFixtures.MATH,
                TestFixtures.SCIENCES, rooms);
        return new ExamSlot("SLX", java.time.LocalDate.of(2026, 6, 2),
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(10, 0),
                1, List.of(exam), 0);
    }

    private long count(List<Duty> duties, DutyRole role) {
        return duties.stream().filter(d -> d.role() == role).count();
    }
}

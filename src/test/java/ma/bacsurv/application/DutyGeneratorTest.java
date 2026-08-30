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
    void permanenceIsOnePerSubjectOfTheSlot() {
        List<Duty> duties = generator.generate(TestFixtures.singleSlotOperation());
        List<Duty> permanence = duties.stream()
                .filter(d -> d.role() == DutyRole.PERMANENCE).toList();

        // the fixture examines two different subjects at that hour
        assertEquals(2, permanence.size());
        assertEquals(2, permanence.stream()
                .map(d -> d.exam().orElseThrow().subject()).distinct().count());
    }

    @Test
    void streamsSittingTheSameSubjectShareOnePermanence() {
        // three streams, one paper: one specialist answers for all of them
        ExamSlot slot = TestFixtures.sameSubjectAcrossStreamsSlot();
        List<Duty> duties = generator.generate(
                new ma.bacsurv.domain.ExamOperation("OP",
                        ma.bacsurv.domain.OperationType.NATIONAL_2BAC, List.of(slot)));

        assertEquals(3, slot.exams().size(), "three exams");
        assertEquals(1, count(duties, DutyRole.PERMANENCE),
                "but a single subject, so a single permanence");
    }

    /**
     * Two filières sitting philosophy from 15:00, one until 17:00 and one
     * until 18:00. Different end times make them two slots — réserve and
     * surveillance are counted against the hours a room is really occupied —
     * but the centre holds one 15:00 séance and one philosopher answers for it.
     *
     * <p>Grouping by slot asked a centre with three philosophers for two of
     * them at one hour. With a concurrent session holding the other two, the
     * distribution had nowhere left to go and seated a French teacher in the
     * philosophy chair.
     */
    @Test
    void oneSpecialistCoversASubjectAcrossSlotsOfTheSameSeance() {
        ma.bacsurv.domain.Exam shorter = ma.bacsurv.domain.Exam.of("E-P1",
                new ma.bacsurv.domain.Subject("Philosophie"),
                new ma.bacsurv.domain.Stream("Sciences Physiques"),
                List.of(TestFixtures.R1));
        ma.bacsurv.domain.Exam longer = ma.bacsurv.domain.Exam.of("E-P2",
                new ma.bacsurv.domain.Subject("Philosophie"),
                new ma.bacsurv.domain.Stream("Lettres"),
                List.of(TestFixtures.R2));

        ExamSlot until17 = new ExamSlot("S7", java.time.LocalDate.of(2026, 7, 3),
                java.time.LocalTime.of(15, 0), java.time.LocalTime.of(17, 0),
                1, List.of(shorter), 0);
        ExamSlot until18 = new ExamSlot("S3", java.time.LocalDate.of(2026, 7, 3),
                java.time.LocalTime.of(15, 0), java.time.LocalTime.of(18, 0),
                2, List.of(longer), 0);

        List<Duty> duties = generator.generate(new ma.bacsurv.domain.ExamOperation("OP",
                ma.bacsurv.domain.OperationType.NATIONAL_2BAC, List.of(until17, until18)));

        List<Duty> permanence = duties.stream()
                .filter(d -> d.role() == DutyRole.PERMANENCE).toList();

        assertEquals(1, permanence.size(), "one séance, one subject, one specialist");
        assertEquals("S3", permanence.getFirst().slot().id(),
                "anchored to the paper that finishes last: he stays until 18:00");
    }

    /** A different subject at the same hour still gets its own specialist. */
    @Test
    void anotherSubjectInTheSameSeanceStillNeedsItsOwn() {
        ma.bacsurv.domain.Exam philo = ma.bacsurv.domain.Exam.of("E-P1",
                new ma.bacsurv.domain.Subject("Philosophie"),
                new ma.bacsurv.domain.Stream("Lettres"), List.of(TestFixtures.R1));
        ma.bacsurv.domain.Exam maths = ma.bacsurv.domain.Exam.of("E-M1",
                new ma.bacsurv.domain.Subject("Mathematiques"),
                new ma.bacsurv.domain.Stream("Sciences"), List.of(TestFixtures.R2));

        ExamSlot a = new ExamSlot("S1", java.time.LocalDate.of(2026, 7, 3),
                java.time.LocalTime.of(15, 0), java.time.LocalTime.of(17, 0),
                1, List.of(philo), 0);
        ExamSlot b = new ExamSlot("S2", java.time.LocalDate.of(2026, 7, 3),
                java.time.LocalTime.of(15, 0), java.time.LocalTime.of(18, 0),
                2, List.of(maths), 0);

        List<Duty> duties = generator.generate(new ma.bacsurv.domain.ExamOperation("OP",
                ma.bacsurv.domain.OperationType.NATIONAL_2BAC, List.of(a, b)));

        assertEquals(2, duties.stream()
                .filter(d -> d.role() == DutyRole.PERMANENCE).count());
    }

    /** A later séance of the same subject is a separate one: he goes home between. */
    @Test
    void thatSameSubjectLaterInTheDayIsASecondSeance() {
        ma.bacsurv.domain.Exam morning = ma.bacsurv.domain.Exam.of("E-P1",
                new ma.bacsurv.domain.Subject("Philosophie"),
                new ma.bacsurv.domain.Stream("Lettres"), List.of(TestFixtures.R1));
        ma.bacsurv.domain.Exam afternoon = ma.bacsurv.domain.Exam.of("E-P2",
                new ma.bacsurv.domain.Subject("Philosophie"),
                new ma.bacsurv.domain.Stream("Sciences"), List.of(TestFixtures.R2));

        ExamSlot am = new ExamSlot("S1", java.time.LocalDate.of(2026, 7, 3),
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(11, 0),
                1, List.of(morning), 0);
        ExamSlot pm = new ExamSlot("S2", java.time.LocalDate.of(2026, 7, 3),
                java.time.LocalTime.of(15, 0), java.time.LocalTime.of(18, 0),
                2, List.of(afternoon), 0);

        List<Duty> duties = generator.generate(new ma.bacsurv.domain.ExamOperation("OP",
                ma.bacsurv.domain.OperationType.NATIONAL_2BAC, List.of(am, pm)));

        assertEquals(2, duties.stream()
                .filter(d -> d.role() == DutyRole.PERMANENCE).count());
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

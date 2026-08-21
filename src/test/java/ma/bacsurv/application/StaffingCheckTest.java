package ma.bacsurv.application;

import ma.bacsurv.TestFixtures;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.Exam;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.ExamSlot;
import ma.bacsurv.domain.OperationType;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.domain.Teacher;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A slot needs as many people as it has duties, because nobody can hold two
 * at once. Catching that before solving is the difference between one clear
 * sentence and a wall of unexplained hard violations.
 */
class StaffingCheckTest {

    private static final Subject MATHS = new Subject("Maths");

    private final StaffingCheck check = StaffingCheck.withDefaults();

    /** The fixture slot carries 13 duties (12 surveillance/permanence + 1 reserve). */
    private ExamOperation operation() {
        return new ExamOperation("OP", OperationType.NATIONAL_2BAC,
                List.of(TestFixtures.multiSubjectSlot()));
    }

    private List<Teacher> pool(int size) {
        return IntStream.rangeClosed(1, size)
                .mapToObj(i -> TestFixtures.teacher("T" + i, MATHS))
                .toList();
    }

    @Test
    void reportsNothingWhenThePoolIsBigEnough() {
        ExamOperation operation = operation();
        List<Duty> duties = new DutyGenerator().generate(operation);

        assertTrue(check.check(operation, pool(duties.size()), duties).isEmpty());
    }

    @Test
    void reportsTheSlotThatCannotBeCovered() {
        ExamOperation operation = operation();
        List<Duty> duties = new DutyGenerator().generate(operation);
        int short_ = duties.size() - 3;

        var shortages = check.check(operation, pool(short_), duties);

        assertEquals(1, shortages.size());
        var shortage = shortages.getFirst();
        assertEquals(duties.size(), shortage.required());
        assertEquals(short_, shortage.available());
        assertEquals(3, shortage.missing());
    }

    /**
     * A real 2BAC afternoon: two papers starting at 15:00, one running to
     * 18:00 and one to 17:00. Two slots, one moment — the centre has to field
     * both room lists at once, so the capacity question is about the hour and
     * not about either séance on its own.
     */
    @Test
    void countsSimultaneousEpreuvesTogether() {
        Exam longPaper = Exam.of("E-L", TestFixtures.FRENCH, TestFixtures.ARTS,
                List.of(TestFixtures.R1, TestFixtures.R2));
        Exam shortPaper = Exam.of("E-S", TestFixtures.HG, TestFixtures.SCIENCES,
                List.of(TestFixtures.R3, TestFixtures.R4));
        LocalDate day = LocalDate.of(2026, 6, 5);
        ExamSlot until18 = new ExamSlot("S-LONG", day,
                LocalTime.of(15, 0), LocalTime.of(18, 0), 1, List.of(longPaper), 0);
        ExamSlot until17 = new ExamSlot("S-SHORT", day,
                LocalTime.of(15, 0), LocalTime.of(17, 0), 1, List.of(shortPaper), 0);

        ExamOperation operation = new ExamOperation("OP", OperationType.NATIONAL_2BAC,
                List.of(until18, until17));
        List<Duty> duties = new DutyGenerator().generate(operation);
        assertEquals(10, duties.size(), "4 rooms x 2 surveillants + 2 permanences");

        // six people covers either séance alone, but not the two at 15:00
        var shortages = check.check(operation, pool(6), duties);

        assertEquals(1, shortages.size(), "one moment, reported once");
        var shortage = shortages.getFirst();
        assertTrue(shortage.isConcurrent());
        assertEquals(List.of("S-LONG", "S-SHORT"), shortage.slotIds());
        assertEquals(LocalTime.of(15, 0), shortage.at());
        assertEquals(10, shortage.required(), "both room lists have to be staffed at once");
        assertEquals(6, shortage.available());
    }

    @Test
    void aMorningAndAnAfternoonAreCountedApart() {
        Exam paper = Exam.of("E", TestFixtures.FRENCH, TestFixtures.ARTS,
                List.of(TestFixtures.R1, TestFixtures.R2));
        LocalDate day = LocalDate.of(2026, 6, 5);
        ExamSlot morning = new ExamSlot("S-AM", day,
                LocalTime.of(8, 0), LocalTime.of(11, 0), 1, List.of(paper), 0);
        ExamSlot afternoon = new ExamSlot("S-PM", day,
                LocalTime.of(15, 0), LocalTime.of(18, 0), 2, List.of(paper), 0);

        ExamOperation operation = new ExamOperation("OP", OperationType.NATIONAL_2BAC,
                List.of(morning, afternoon));
        List<Duty> duties = new DutyGenerator().generate(operation);

        // 5 duties per séance, and the same five people can do both
        assertTrue(check.check(operation, pool(5), duties).isEmpty(),
                "the pool is reused between séances of one day");
    }

    @Test
    void countsOnlyTeachersWhoCouldLegallyTakeADuty() {
        ExamOperation operation = operation();
        List<Duty> duties = new DutyGenerator().generate(operation);

        // teachers of the exam's own subject may not surveil it, but may still
        // take reserve duty, so they still count towards the slot's capacity
        List<Teacher> ownSubject = List.of(TestFixtures.teacher("X1", TestFixtures.HG));
        var shortages = check.check(operation, ownSubject, duties);

        assertEquals(1, shortages.size());
        assertEquals(1, shortages.getFirst().available());
    }
}

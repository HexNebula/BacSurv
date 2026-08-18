package ma.bacsurv.application;

import ma.bacsurv.TestFixtures;
import ma.bacsurv.domain.Duty;
import ma.bacsurv.domain.ExamOperation;
import ma.bacsurv.domain.OperationType;
import ma.bacsurv.domain.Subject;
import ma.bacsurv.domain.Teacher;
import org.junit.jupiter.api.Test;

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

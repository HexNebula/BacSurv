package ma.bacsurv.web.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a session hands to the next one. Sessions are solved on their own —
 * rattrapage cannot be sized in advance, so planning the year as one whole
 * means planning against an unknown. Only the unfinished tail of the last
 * round travels: who is still owed a turn.
 */
class PrivilegeCarryTest {

    private static final List<Long> POOL = List.of(1L, 2L, 3L, 4L, 5L);

    @Test
    void aCompletedRoundCarriesNothing() {
        // everyone had exactly one turn — nobody is owed anything
        Map<Long, Integer> carry = OperationAssembler.carryFrom(
                Map.of(1L, 1, 2L, 1, 3L, 1, 4L, 1, 5L, 1), POOL);

        assertTrue(carry.isEmpty(), "a clean round leaves no debt, was " + carry);
    }

    @Test
    void turnsTakenBeyondTheSlowestColleagueAreCarried() {
        // 7 privileges over 5 teachers: everyone once, two of them twice
        Map<Long, Integer> carry = OperationAssembler.carryFrom(
                Map.of(1L, 2, 2L, 2, 3L, 1, 4L, 1, 5L, 1), POOL);

        assertEquals(Map.of(1L, 1, 2L, 1), carry,
                "only the two who took a second turn start behind");
    }

    @Test
    void aTeacherWhoMissedTheLastSessionGoesToTheFront() {
        // teacher 5 was not in the previous session; the others each had one
        Map<Long, Integer> carry = OperationAssembler.carryFrom(
                Map.of(1L, 1, 2L, 1, 3L, 1, 4L, 1), POOL);

        assertEquals(Map.of(1L, 1, 2L, 1, 3L, 1, 4L, 1), carry,
                "the newcomer has had no turn, so everyone else waits for them");
        assertTrue(!carry.containsKey(5L), "the newcomer carries nothing");
    }

    @Test
    void theCarryCannotGrowWithTheSizeOfASession() {
        // a big session and a tiny one leave the same shape of debt: the
        // absolute counts differ wildly, the unfinished tail does not
        Map<Long, Integer> afterBigSession = OperationAssembler.carryFrom(
                Map.of(1L, 13, 2L, 12, 3L, 12, 4L, 12, 5L, 12), POOL);
        Map<Long, Integer> afterSmallSession = OperationAssembler.carryFrom(
                Map.of(1L, 1, 2L, 0, 3L, 0, 4L, 0, 5L, 0), POOL);

        assertEquals(afterSmallSession, afterBigSession);
        assertEquals(Map.of(1L, 1), afterBigSession);
    }

    @Test
    void aFirstSessionCarriesNothing() {
        assertTrue(OperationAssembler.carryFrom(Map.of(), POOL).isEmpty());
    }
}

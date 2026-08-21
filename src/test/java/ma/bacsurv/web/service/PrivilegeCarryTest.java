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

    /**
     * The rattrapage case, in miniature. A session repays the teachers it owes
     * only as far as its turns reach, so someone can still be waiting two
     * sessions later — and July has to tell them apart from the people June
     * already settled.
     *
     * <p>1BAC gave a turn to teachers 1 and 2. The 2BAC put 3, 4 and 5 at the
     * front but had only two turns, so it reached 3 and 4 and left 5 waiting.
     */
    @Test
    void aTeacherNoSessionHasReachedYetGoesFirst() {
        Map<Long, Integer> turnsOfEachSession = Map.of(1L, 1, 2L, 1, 3L, 1, 4L, 1, 5L, 0);

        Map<Long, Integer> carry = OperationAssembler.carryFrom(turnsOfEachSession, POOL);

        assertTrue(!carry.containsKey(5L), "teacher 5 is the only one still owed a turn");
        assertEquals(Map.of(1L, 1, 2L, 1, 3L, 1, 4L, 1), carry,
                "everyone already served waits behind them");
    }

    /**
     * Reading one session only would lose this: in the 2BAC alone, teachers 1
     * and 2 also show zero, because their turn was back in the 1BAC.
     */
    @Test
    void turnsFromAnOlderSessionStillCount() {
        Map<Long, Integer> lastSessionOnly = Map.of(3L, 1, 4L, 1);
        Map<Long, Integer> everySession = Map.of(1L, 1, 2L, 1, 3L, 1, 4L, 1);

        assertEquals(Map.of(3L, 1, 4L, 1),
                OperationAssembler.carryFrom(lastSessionOnly, POOL),
                "the newest session alone treats 1 and 2 as still owed");
        assertEquals(Map.of(1L, 1, 2L, 1, 3L, 1, 4L, 1),
                OperationAssembler.carryFrom(everySession, POOL),
                "counting every session leaves teacher 5 alone at the front");
    }

    /** A teacher forced into a second turn by scarcity ends up last in line. */
    @Test
    void someoneAheadOfEveryoneWaitsLongest() {
        Map<Long, Integer> carry = OperationAssembler.carryFrom(
                Map.of(1L, 2, 2L, 1, 3L, 1, 4L, 1, 5L, 0), POOL);

        assertEquals(2, carry.get(1L), "two turns while a colleague has none");
        assertEquals(1, carry.get(2L));
        assertTrue(!carry.containsKey(5L));
    }
}

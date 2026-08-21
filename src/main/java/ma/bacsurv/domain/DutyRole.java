package ma.bacsurv.domain;

public enum DutyRole {
    SURVEILLANCE, RESERVE, PERMANENCE;

    /**
     * Surveillance is the work: standing in a room for the whole épreuve.
     * Réserve and permanence are lighter — a turn people are glad to get.
     * All three count as one load unit and are paid the same (MODEL.md §4),
     * so fairness has to watch the mix, not only the total.
     *
     * <p>The two privileges draw from different pools — permanence only from
     * the subject's specialists — but they share one queue: whoever just had
     * a réserve waits for a permanence too, until everyone else has had a turn.
     */
    public boolean isPrivilege() {
        return this != SURVEILLANCE;
    }
}

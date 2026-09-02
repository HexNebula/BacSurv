package ma.bacsurv.domain;

public enum DutyRole {
    SURVEILLANCE, RESERVE, PERMANENCE;

    /**
     * Surveillance is the work: standing in a room for the whole épreuve.
     * Réserve and permanence are lighter — a turn people are glad to get.
     * All three count as one load unit and are paid the same, so fairness
     * has to watch the mix, not only the total.
     *
     * <p>The two privileges draw from different pools — permanence only from
     * the subject's specialists — and each keeps its own queue: whoever just
     * had one waits until everyone else has had one too. One queue for both
     * made the scarce subjects pay, since a specialist forced into permanence
     * spent the turn that would have given them a réserve.
     */
    public boolean isPrivilege() {
        return this != SURVEILLANCE;
    }
}

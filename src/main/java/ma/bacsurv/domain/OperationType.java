package ma.bacsurv.domain;

/**
 * The sessions a centre runs in a school year.
 *
 * <p>There is a regional rattrapage, and it exists for one reason: candidats
 * libres. A scolarisé sits the régionale in first year and, if he fails in
 * second year, only the national rattrapage. A candidat libre sits both the
 * régionale and the nationale in the same year — and so, when he fails, both
 * rattrapages. His regional rattrapage comes first, on its own days; the
 * national one he sits alongside everybody else.
 *
 * <p>That makes four sessions in a year, not three, and the fourth examines
 * first-year papers in the middle of a second-year season. Which is why the
 * level is not something to read off the calendar.
 */
public enum OperationType {

    REGIONAL_1BAC(Level.BAC1),
    REGIONAL_1BAC_RATTRAPAGE(Level.BAC1),
    NATIONAL_2BAC(Level.BAC2),
    NATIONAL_2BAC_RATTRAPAGE(Level.BAC2);

    /** The year whose candidates sit this session, and whose filières it runs. */
    public static final class Level {
        public static final String BAC1 = "BAC1";
        public static final String BAC2 = "BAC2";

        private Level() {}
    }

    private final String level;

    OperationType(String level) {
        this.level = level;
    }

    public String level() {
        return level;
    }

    /**
     * The level of a session type named as a string, for the rows that store it
     * that way. An unknown name is second-year: the enum is what validates a
     * type, and by the time anything asks this the name has already passed it.
     */
    public static String levelOf(String name) {
        try {
            return valueOf(name).level();
        } catch (RuntimeException unknown) {
            return Level.BAC2;
        }
    }
}

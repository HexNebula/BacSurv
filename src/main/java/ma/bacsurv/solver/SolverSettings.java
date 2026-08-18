package ma.bacsurv.solver;

import java.time.Duration;

/**
 * Technical knobs, kept apart from the exam rules on purpose. An administrator
 * raising this is saying "spend longer searching", not changing procedure.
 */
public record SolverSettings(int timeLimitSeconds, int unimprovedSecondsBeforeStopping) {

    public SolverSettings {
        if (timeLimitSeconds < 1) {
            throw new IllegalArgumentException("timeLimitSeconds must be at least 1, was "
                    + timeLimitSeconds);
        }
        if (unimprovedSecondsBeforeStopping < 1) {
            throw new IllegalArgumentException("unimprovedSecondsBeforeStopping must be at least 1");
        }
    }

    public static SolverSettings defaults() {
        return new SolverSettings(30, 5);
    }

    public static SolverSettings ofSeconds(int seconds) {
        return new SolverSettings(seconds, Math.min(5, seconds));
    }

    public Duration timeLimit() {
        return Duration.ofSeconds(timeLimitSeconds);
    }

    public Duration unimprovedLimit() {
        return Duration.ofSeconds(unimprovedSecondsBeforeStopping);
    }
}

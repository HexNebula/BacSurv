package ma.bacsurv.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.bacsurv.rules.ConstraintStrength;
import ma.bacsurv.rules.ReserveRequirement;
import ma.bacsurv.rules.SchedulingPolicy;
import ma.bacsurv.rules.StaffingPolicy;
import ma.bacsurv.rules.SubjectConflictConfig;
import ma.bacsurv.solver.SolverSettings;

import java.util.Map;

/** What a centre configured for one operation, stored as plain columns. */
@Entity
@Table(name = "operation_config")
public class OperationConfigEntity {

    @Id
    @Column(name = "operation_id")
    private Long operationId;

    @Column(name = "default_surveillants_per_room", nullable = false)
    private int defaultSurveillantsPerRoom = StaffingPolicy.MINIMUM_SURVEILLANTS_PER_ROOM;

    @Column(name = "reserve_mode", nullable = false, length = 20)
    private String reserveMode = ReserveRequirement.Mode.PERCENTAGE.name();

    @Column(name = "reserve_percentage", nullable = false)
    private double reservePercentage = 0.10;

    @Column(name = "reserve_fixed_count", nullable = false)
    private int reserveFixedCount;

    @Column(name = "max_consecutive_days", nullable = false)
    private int maxConsecutiveDays = 3;

    @Column(name = "consecutive_days_strength", nullable = false, length = 10)
    private String consecutiveDaysStrength = ConstraintStrength.SOFT.name();

    @Column(name = "min_gap_minutes", nullable = false)
    private int minGapMinutes;

    @Column(name = "own_subject_strength", nullable = false, length = 10)
    private String ownSubjectStrength = ConstraintStrength.HARD.name();

    @Column(name = "forbid_own_subject_reserve", nullable = false)
    private boolean forbidOwnSubjectReserve;

    @Column(name = "solve_seconds", nullable = false)
    private int solveSeconds = 30;

    protected OperationConfigEntity() {}

    public OperationConfigEntity(Long operationId) {
        this.operationId = operationId;
    }

    public StaffingPolicy staffing(Map<String, Integer> roomOverrides) {
        ReserveRequirement reserve =
                ReserveRequirement.Mode.valueOf(reserveMode) == ReserveRequirement.Mode.FIXED_COUNT
                        ? ReserveRequirement.fixed(reserveFixedCount)
                        : ReserveRequirement.percentage(reservePercentage);
        return new StaffingPolicy(defaultSurveillantsPerRoom, roomOverrides, reserve);
    }

    public SchedulingPolicy scheduling() {
        SchedulingPolicy defaults = SchedulingPolicy.defaults();
        return new SchedulingPolicy(maxConsecutiveDays,
                ConstraintStrength.valueOf(consecutiveDaysStrength),
                defaults.consecutiveDaysWeight(),
                minGapMinutes, defaults.minimumGapWeight(),
                new SubjectConflictConfig(true,
                        ConstraintStrength.valueOf(ownSubjectStrength),
                        forbidOwnSubjectReserve));
    }

    public SolverSettings solver() {
        return SolverSettings.ofSeconds(solveSeconds);
    }

    public void apply(int defaultSurveillantsPerRoom, String reserveMode, double reservePercentage,
                      int reserveFixedCount, int maxConsecutiveDays, String consecutiveDaysStrength,
                      int minGapMinutes, String ownSubjectStrength,
                      boolean forbidOwnSubjectReserve, int solveSeconds) {
        // validated by the policy records themselves, so bad input never reaches the table
        new StaffingPolicy(defaultSurveillantsPerRoom, Map.of(),
                ReserveRequirement.Mode.valueOf(reserveMode) == ReserveRequirement.Mode.FIXED_COUNT
                        ? ReserveRequirement.fixed(reserveFixedCount)
                        : ReserveRequirement.percentage(reservePercentage));
        new SchedulingPolicy(maxConsecutiveDays, ConstraintStrength.valueOf(consecutiveDaysStrength),
                1, minGapMinutes, 1, SubjectConflictConfig.defaults());
        SolverSettings.ofSeconds(solveSeconds);

        this.defaultSurveillantsPerRoom = defaultSurveillantsPerRoom;
        this.reserveMode = reserveMode;
        this.reservePercentage = reservePercentage;
        this.reserveFixedCount = reserveFixedCount;
        this.maxConsecutiveDays = maxConsecutiveDays;
        this.consecutiveDaysStrength = consecutiveDaysStrength;
        this.minGapMinutes = minGapMinutes;
        this.ownSubjectStrength = ownSubjectStrength;
        this.forbidOwnSubjectReserve = forbidOwnSubjectReserve;
        this.solveSeconds = solveSeconds;
    }

    public Long getOperationId() { return operationId; }
    public int getDefaultSurveillantsPerRoom() { return defaultSurveillantsPerRoom; }
    public String getReserveMode() { return reserveMode; }
    public double getReservePercentage() { return reservePercentage; }
    public int getReserveFixedCount() { return reserveFixedCount; }
    public int getMaxConsecutiveDays() { return maxConsecutiveDays; }
    public String getConsecutiveDaysStrength() { return consecutiveDaysStrength; }
    public int getMinGapMinutes() { return minGapMinutes; }
    public String getOwnSubjectStrength() { return ownSubjectStrength; }
    public boolean isForbidOwnSubjectReserve() { return forbidOwnSubjectReserve; }
    public int getSolveSeconds() { return solveSeconds; }
}

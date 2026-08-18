package ma.bacsurv.rules;

import ma.bacsurv.domain.Exam;

import java.util.Map;

/**
 * How many people a centre puts on the work — administrative staffing rules
 * a centre may legitimately set for itself.
 *
 * Surveillance staffing is resolved from the most specific value available:
 * room override, then the exam's own value, then the operation default. The
 * official floor of two surveillants per room is never negotiable, so any
 * configured value below it is rejected rather than silently raised.
 */
public record StaffingPolicy(int defaultSurveillantsPerRoom,
                             Map<String, Integer> surveillantsByRoom,
                             ReserveRequirement reserve) {

    public static final int MINIMUM_SURVEILLANTS_PER_ROOM = Exam.MIN_SURVEILLANTS_PER_ROOM;

    public StaffingPolicy {
        requireAtLeastMinimum(defaultSurveillantsPerRoom, "defaultSurveillantsPerRoom");
        surveillantsByRoom = Map.copyOf(surveillantsByRoom);
        surveillantsByRoom.forEach((room, count) ->
                requireAtLeastMinimum(count, "surveillants for room " + room));
    }

    public static StaffingPolicy defaults() {
        return new StaffingPolicy(MINIMUM_SURVEILLANTS_PER_ROOM, Map.of(),
                ReserveRequirement.percentage(0.10));
    }

    public StaffingPolicy withReserve(ReserveRequirement requirement) {
        return new StaffingPolicy(defaultSurveillantsPerRoom, surveillantsByRoom, requirement);
    }

    public StaffingPolicy withRoomOverride(String roomId, int surveillants) {
        Map<String, Integer> overrides = new java.util.HashMap<>(surveillantsByRoom);
        overrides.put(roomId, surveillants);
        return new StaffingPolicy(defaultSurveillantsPerRoom, overrides, reserve);
    }

    /** Room override first, then what the exam itself asks for, then the default. */
    public int surveillantsFor(Exam exam, String roomId) {
        Integer roomOverride = surveillantsByRoom.get(roomId);
        if (roomOverride != null) return roomOverride;
        return Math.max(exam.surveillantsPerRoom(), MINIMUM_SURVEILLANTS_PER_ROOM);
    }

    private static void requireAtLeastMinimum(int count, String what) {
        if (count < MINIMUM_SURVEILLANTS_PER_ROOM) {
            throw new IllegalArgumentException(what + " must be at least "
                    + MINIMUM_SURVEILLANTS_PER_ROOM + " (official rule), was " + count);
        }
    }
}

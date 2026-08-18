package ma.bacsurv.domain;

import java.util.List;

/**
 * One subject examination for one stream, inside an ExamSlot.
 * Official rule: at least 2 surveillants per room.
 */
public record Exam(String id, Subject subject, Stream stream, List<Room> rooms,
                   int surveillantsPerRoom, int permanenceCount) {

    public static final int MIN_SURVEILLANTS_PER_ROOM = 2;

    public Exam {
        rooms = List.copyOf(rooms);
        if (surveillantsPerRoom < MIN_SURVEILLANTS_PER_ROOM)
            throw new IllegalArgumentException("official rule requires >= 2 surveillants per room");
        if (permanenceCount < 0)
            throw new IllegalArgumentException("permanenceCount must be >= 0");
    }

    public static Exam of(String id, Subject subject, Stream stream, List<Room> rooms) {
        return new Exam(id, subject, stream, rooms, MIN_SURVEILLANTS_PER_ROOM, 1);
    }

    public int surveillanceDutyCount() {
        return rooms.size() * surveillantsPerRoom;
    }
}

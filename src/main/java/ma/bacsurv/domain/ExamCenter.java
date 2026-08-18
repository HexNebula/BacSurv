package ma.bacsurv.domain;

import java.util.List;

public record ExamCenter(String id, String name, List<Room> rooms) {
    public ExamCenter {
        rooms = List.copyOf(rooms);
    }
}

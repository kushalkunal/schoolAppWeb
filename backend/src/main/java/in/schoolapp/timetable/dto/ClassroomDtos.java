package in.schoolapp.timetable.dto;

import in.schoolapp.timetable.entity.Classroom;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** DTOs for classroom/room management. */
public final class ClassroomDtos {
    private ClassroomDtos() {}

    public record ClassroomResponse(UUID id, String name, String code, String building,
                                    Integer capacity, String roomType) {
        public static ClassroomResponse from(Classroom c) {
            return new ClassroomResponse(c.getId(), c.getName(), c.getCode(), c.getBuilding(),
                c.getCapacity(), c.getRoomType());
        }
    }

    public record SaveClassroomRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 30) String code,
        @Size(max = 80) String building,
        Integer capacity,
        String roomType
    ) {}
}

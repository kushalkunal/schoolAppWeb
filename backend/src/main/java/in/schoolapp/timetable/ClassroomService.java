package in.schoolapp.timetable;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.timetable.dto.ClassroomDtos.ClassroomResponse;
import in.schoolapp.timetable.dto.ClassroomDtos.SaveClassroomRequest;
import in.schoolapp.timetable.entity.Classroom;
import in.schoolapp.timetable.repository.ClassroomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomRepository repository;

    @Transactional(readOnly = true)
    public List<ClassroomResponse> list(UUID tenantId) {
        return repository.findBySchoolIdOrderByName(tenantId).stream().map(ClassroomResponse::from).toList();
    }

    @Transactional
    public ClassroomResponse create(UUID tenantId, SaveClassroomRequest req) {
        if (repository.existsBySchoolIdAndName(tenantId, req.name())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "A room named '" + req.name() + "' already exists");
        }
        Classroom c = new Classroom();
        c.setSchoolId(tenantId);
        apply(c, req);
        return ClassroomResponse.from(repository.save(c));
    }

    @Transactional
    public ClassroomResponse update(UUID tenantId, UUID id, SaveClassroomRequest req) {
        Classroom c = repository.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));
        apply(c, req);
        return ClassroomResponse.from(repository.save(c));
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        Classroom c = repository.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));
        repository.delete(c);   // timetable_entries.room_id is ON DELETE SET NULL
    }

    private void apply(Classroom c, SaveClassroomRequest req) {
        c.setName(req.name());
        c.setCode(req.code());
        c.setBuilding(req.building());
        c.setCapacity(req.capacity());
        c.setRoomType(req.roomType() == null || req.roomType().isBlank() ? "CLASSROOM" : req.roomType().toUpperCase());
    }
}

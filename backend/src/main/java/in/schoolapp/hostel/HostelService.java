package in.schoolapp.hostel;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.hostel.entity.Hostel;
import in.schoolapp.hostel.entity.HostelAllocation;
import in.schoolapp.hostel.entity.HostelAllocation.Status;
import in.schoolapp.hostel.entity.HostelRoom;
import in.schoolapp.hostel.entity.HostelVisitorLog;
import in.schoolapp.hostel.repository.HostelAllocationRepository;
import in.schoolapp.hostel.repository.HostelRepository;
import in.schoolapp.hostel.repository.HostelRoomRepository;
import in.schoolapp.hostel.repository.HostelVisitorLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Single service for the hostel module — small enough to keep CRUD + business logic
 * (allocate / vacate / sign-in / sign-out) in one file.
 *
 * <p>Allocation invariants:
 * <ul>
 *   <li>A student may have at most one ACTIVE allocation at a time.</li>
 *   <li>A room's {@code current_occupancy} cannot exceed its {@code capacity}.</li>
 *   <li>Vacating an allocation decrements occupancy and stamps {@code vacated_at}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostelService {

    private final HostelRepository hostelRepository;
    private final HostelRoomRepository roomRepository;
    private final HostelAllocationRepository allocationRepository;
    private final HostelVisitorLogRepository visitorRepository;

    // ---------------- hostels + rooms ----------------

    @Transactional
    public Hostel createHostel(UUID tenantId, Hostel template) {
        template.setSchoolId(tenantId);
        return hostelRepository.save(template);
    }

    public List<Hostel> listHostels(UUID tenantId) {
        return hostelRepository.findBySchoolIdAndActiveOrderByNameAsc(tenantId, true);
    }

    @Transactional
    public HostelRoom createRoom(UUID tenantId, UUID hostelId, HostelRoom template) {
        Hostel h = hostelRepository.findByIdAndSchoolId(hostelId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Hostel not found"));
        template.setSchoolId(tenantId);
        template.setHostelId(hostelId);
        template.setCurrentOccupancy(0);
        HostelRoom saved = roomRepository.save(template);
        h.setTotalRooms(h.getTotalRooms() + 1);
        h.setCapacity(h.getCapacity() + saved.getCapacity());
        hostelRepository.save(h);
        return saved;
    }

    public List<HostelRoom> listRooms(UUID tenantId, UUID hostelId) {
        // Tenant-scope check first.
        hostelRepository.findByIdAndSchoolId(hostelId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Hostel not found"));
        return roomRepository.findByHostelIdOrderByFloorAscRoomNumberAsc(hostelId);
    }

    // ---------------- allocations ----------------

    @Transactional
    public HostelAllocation allocate(UUID tenantId, UUID roomId, UUID studentId, LocalDate from) {
        HostelRoom room = roomRepository.findByIdAndSchoolId(roomId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));

        // 1. Student must not already have an active allocation.
        allocationRepository.findByStudentIdAndStatus(studentId, Status.ACTIVE).ifPresent(a -> {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Student already has active allocation " + a.getId());
        });

        // 2. Room capacity check.
        if (!room.hasVacancy()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Room " + room.getRoomNumber() + " is full ("
                    + room.getCurrentOccupancy() + "/" + room.getCapacity() + ")");
        }

        // 3. Allocate + bump occupancy.
        HostelAllocation alloc = new HostelAllocation();
        alloc.setSchoolId(tenantId);
        alloc.setRoomId(roomId);
        alloc.setStudentId(studentId);
        alloc.setAllocatedFrom(from != null ? from : LocalDate.now());
        alloc.setStatus(Status.ACTIVE);
        alloc = allocationRepository.save(alloc);
        room.setCurrentOccupancy(room.getCurrentOccupancy() + 1);
        roomRepository.save(room);
        log.info("Hostel allocated tenant={} room={} student={}", tenantId, roomId, studentId);
        return alloc;
    }

    @Transactional
    public HostelAllocation vacate(UUID tenantId, UUID allocationId, String reason) {
        HostelAllocation alloc = allocationRepository.findByIdAndSchoolId(allocationId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Allocation not found"));
        if (alloc.getStatus() == Status.VACATED) return alloc;

        alloc.setStatus(Status.VACATED);
        alloc.setVacatedAt(OffsetDateTime.now());
        alloc.setVacatedReason(reason);
        alloc.setAllocatedUntil(LocalDate.now());
        allocationRepository.save(alloc);

        HostelRoom room = roomRepository.findById(alloc.getRoomId()).orElse(null);
        if (room != null) {
            room.setCurrentOccupancy(Math.max(0, room.getCurrentOccupancy() - 1));
            roomRepository.save(room);
        }
        log.info("Hostel vacated tenant={} alloc={}", tenantId, allocationId);
        return alloc;
    }

    public List<HostelAllocation> listActiveAllocations(UUID tenantId) {
        return allocationRepository.findBySchoolIdAndStatusOrderByAllocatedFromDesc(tenantId, Status.ACTIVE);
    }

    // ---------------- visitor logs ----------------

    @Transactional
    public HostelVisitorLog signIn(UUID tenantId, HostelVisitorLog template) {
        // Tenant scope check on the hostel.
        hostelRepository.findByIdAndSchoolId(template.getHostelId(), tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Hostel not found"));
        template.setSchoolId(tenantId);
        if (template.getInTime() == null) template.setInTime(OffsetDateTime.now());
        return visitorRepository.save(template);
    }

    @Transactional
    public HostelVisitorLog signOut(UUID tenantId, UUID logId) {
        HostelVisitorLog log = visitorRepository.findByIdAndSchoolId(logId, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Visitor log not found"));
        if (log.getOutTime() == null) {
            log.setOutTime(OffsetDateTime.now());
            visitorRepository.save(log);
        }
        return log;
    }

    public List<HostelVisitorLog> listCurrentlyInside(UUID tenantId) {
        return visitorRepository.findBySchoolIdAndOutTimeIsNullOrderByInTimeDesc(tenantId);
    }

    public List<HostelVisitorLog> listAllVisits(UUID tenantId) {
        return visitorRepository.findBySchoolIdOrderByInTimeDesc(tenantId);
    }
}

package in.schoolapp.timetable;

import in.schoolapp.common.AppException;
import in.schoolapp.hr.repository.StaffAttendanceRepository;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.timetable.dto.TimetableEntryDto;
import in.schoolapp.timetable.entity.TimetableEntry;
import in.schoolapp.timetable.repository.TimetableEntryRepository;
import in.schoolapp.timetable.repository.TimetablePeriodRepository;
import in.schoolapp.timetable.repository.TimetableSubstitutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Room double-booking detection in the timetable upsert. */
@ExtendWith(MockitoExtension.class)
class TimetableRoomConflictTest {

    @Mock TimetablePeriodRepository periodRepository;
    @Mock TimetableEntryRepository entryRepository;
    @Mock TimetableSubstitutionRepository substitutionRepository;
    @Mock StaffRepository staffRepository;
    @Mock StaffAttendanceRepository staffAttendanceRepository;
    @InjectMocks TimetableService service;

    final UUID tenant = UUID.randomUUID();
    final UUID period = UUID.randomUUID();
    final UUID sectionA = UUID.randomUUID();
    final UUID sectionB = UUID.randomUUID();
    final UUID room = UUID.randomUUID();

    private TimetableEntry occupied() {
        TimetableEntry e = new TimetableEntry();
        e.setId(UUID.randomUUID());
        e.setSchoolId(tenant);
        e.setSectionId(sectionA);
        e.setPeriodId(period);
        e.setDayOfWeek(1);
        e.setRoomId(room);
        return e;
    }

    @Test
    void sameRoomSameSlotDifferentSectionIsRejected() {
        when(entryRepository.findByPeriodIdAndDayOfWeek(period, 1)).thenReturn(List.of(occupied()));
        TimetableEntryDto req = new TimetableEntryDto(null, sectionB, period, 1, null, null, room, null);

        assertThatThrownBy(() -> service.upsertEntry(tenant, req))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("room is already booked");
    }

    @Test
    void differentRoomSameSlotIsAllowed() {
        when(entryRepository.findByPeriodIdAndDayOfWeek(period, 1)).thenReturn(List.of(occupied()));
        when(entryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        UUID otherRoom = UUID.randomUUID();
        TimetableEntryDto req = new TimetableEntryDto(null, sectionB, period, 1, null, null, otherRoom, null);

        TimetableEntryDto saved = service.upsertEntry(tenant, req);

        assertThat(saved.roomId()).isEqualTo(otherRoom);
    }
}

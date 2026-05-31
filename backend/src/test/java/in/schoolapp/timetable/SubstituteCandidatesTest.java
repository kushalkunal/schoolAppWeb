package in.schoolapp.timetable;

import in.schoolapp.hr.entity.StaffAttendance;
import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;
import in.schoolapp.hr.repository.StaffAttendanceRepository;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.timetable.dto.SubstituteCandidatesResponse;
import in.schoolapp.timetable.entity.TimetableEntry;
import in.schoolapp.timetable.repository.TimetableEntryRepository;
import in.schoolapp.timetable.repository.TimetablePeriodRepository;
import in.schoolapp.timetable.repository.TimetableSubstitutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** getSubstituteCandidates splits teaching staff into absent / free-at-slot / busy-at-slot. */
@ExtendWith(MockitoExtension.class)
class SubstituteCandidatesTest {

    @Mock TimetablePeriodRepository periodRepository;
    @Mock TimetableEntryRepository entryRepository;
    @Mock TimetableSubstitutionRepository substitutionRepository;
    @Mock StaffRepository staffRepository;
    @Mock StaffAttendanceRepository staffAttendanceRepository;
    @InjectMocks TimetableService service;

    final UUID tenant = UUID.randomUUID();
    final UUID periodId = UUID.randomUUID();
    final UUID coverSection = UUID.randomUUID();
    final UUID otherSection = UUID.randomUUID();
    final LocalDate date = LocalDate.of(2026, 6, 1); // a Monday (dow=1)

    private Staff teacher(UUID id, String name) {
        Staff s = new Staff();
        s.setId(id);
        s.setSchoolId(tenant);
        s.setFirstName(name);
        s.setRole(StaffRole.SUBJECT_TEACHER);
        s.setActive(true);
        return s;
    }

    @Test
    void classifiesAbsentBusyAndAvailable() {
        UUID absentT = UUID.randomUUID();   // marked absent today
        UUID busyT = UUID.randomUUID();     // teaching another section this period
        UUID freeT = UUID.randomUUID();     // free → best substitute

        when(staffRepository.findBySchoolIdAndActiveTrueOrderByFirstName(tenant))
            .thenReturn(List.of(teacher(absentT, "Asha"), teacher(busyT, "Bina"), teacher(freeT, "Chetan")));

        StaffAttendance absentRow = new StaffAttendance();
        absentRow.setStaffId(absentT);
        absentRow.setStatus(StaffAttendanceStatus.ABSENT);
        when(staffAttendanceRepository.findBySchoolIdAndAttendanceDate(tenant, date))
            .thenReturn(List.of(absentRow));

        TimetableEntry busyEntry = new TimetableEntry();
        busyEntry.setSchoolId(tenant);
        busyEntry.setTeacherId(busyT);
        busyEntry.setSectionId(otherSection);   // teaching a DIFFERENT section this period
        busyEntry.setPeriodId(periodId);
        when(entryRepository.findByPeriodIdAndDayOfWeek(periodId, 1)).thenReturn(List.of(busyEntry));

        when(substitutionRepository.findByDateAndSchoolId(date, tenant)).thenReturn(List.of());

        SubstituteCandidatesResponse res =
            service.getSubstituteCandidates(tenant, date, periodId, coverSection);

        assertThat(res.absent()).extracting(SubstituteCandidatesResponse.Candidate::staffId)
            .containsExactly(absentT);
        assertThat(res.busy()).extracting(SubstituteCandidatesResponse.Candidate::staffId)
            .containsExactly(busyT);
        assertThat(res.available()).extracting(SubstituteCandidatesResponse.Candidate::staffId)
            .containsExactly(freeT);
    }
}

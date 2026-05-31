package in.schoolapp.calendar;

import in.schoolapp.common.AppException;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.repository.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Working-day / holiday logic for the per-tenant school calendar. */
@ExtendWith(MockitoExtension.class)
class SchoolCalendarServiceTest {

    @Mock SchoolRepository schoolRepository;
    @Mock SchoolHolidayRepository holidayRepository;
    @InjectMocks SchoolCalendarService service;

    final UUID tenant = UUID.randomUUID();
    final LocalDate monday = LocalDate.of(2026, 6, 1);  // Monday  (dow=1)
    final LocalDate sunday = LocalDate.of(2026, 6, 7);  // Sunday  (dow=7)

    private void stubSchool() {
        when(schoolRepository.findById(tenant)).thenReturn(Optional.of(new School())); // settings null → defaults
    }

    @Test
    void workingWeekdayWithNoHolidayIsOpen() {
        stubSchool();
        when(holidayRepository.existsBySchoolIdAndHolidayDate(tenant, monday)).thenReturn(false);
        assertThat(service.isWorkingDay(tenant, monday)).isTrue();
    }

    @Test
    void sundayIsClosedByDefault() {
        stubSchool();
        lenient().when(holidayRepository.existsBySchoolIdAndHolidayDate(eq(tenant), any())).thenReturn(false);
        assertThat(service.isWorkingDay(tenant, sunday)).isFalse();   // 7 not in default Mon–Sat
    }

    @Test
    void holidayOnAWorkingWeekdayIsClosed() {
        stubSchool();
        when(holidayRepository.existsBySchoolIdAndHolidayDate(tenant, monday)).thenReturn(true);
        assertThat(service.isWorkingDay(tenant, monday)).isFalse();
    }

    @Test
    void settingEmptyWorkingDaysIsRejected() {
        assertThatThrownBy(() -> service.setWorkingDays(tenant, java.util.List.of(0, 9)))
            .isInstanceOf(AppException.class);
    }
}

package in.schoolapp.school;

import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.repository.AcademicYearRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicYearServiceTest {

    @Mock
    AcademicYearRepository repo;

    @InjectMocks
    AcademicYearService service;

    @Test
    void createCurrentYearForSchool_usesAprilToMarchWindow() {
        UUID schoolId = UUID.randomUUID();
        when(repo.existsBySchoolIdAndName(any(), any())).thenReturn(false);
        when(repo.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicYear year = service.createCurrentYearForSchool(schoolId);

        assertThat(year.getSchoolId()).isEqualTo(schoolId);
        assertThat(year.isCurrent()).isTrue();
        assertThat(year.getStartDate().getMonth()).isEqualTo(Month.APRIL);
        assertThat(year.getStartDate().getDayOfMonth()).isEqualTo(1);
        assertThat(year.getEndDate().getMonth()).isEqualTo(Month.MARCH);
        assertThat(year.getEndDate().getDayOfMonth()).isEqualTo(31);
        assertThat(year.getEndDate().getYear()).isEqualTo(year.getStartDate().getYear() + 1);

        int expectedStartYear = LocalDate.now().getMonthValue() >= Month.APRIL.getValue()
            ? LocalDate.now().getYear()
            : LocalDate.now().getYear() - 1;
        assertThat(year.getName()).isEqualTo(expectedStartYear + "-" + (expectedStartYear + 1));
    }

    @Test
    void createCurrentYearForSchool_persistsExactlyOnce() {
        UUID schoolId = UUID.randomUUID();
        when(repo.existsBySchoolIdAndName(any(), any())).thenReturn(false);
        when(repo.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createCurrentYearForSchool(schoolId);

        ArgumentCaptor<AcademicYear> captor = ArgumentCaptor.forClass(AcademicYear.class);
        org.mockito.Mockito.verify(repo).save(captor.capture());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(schoolId);
    }
}

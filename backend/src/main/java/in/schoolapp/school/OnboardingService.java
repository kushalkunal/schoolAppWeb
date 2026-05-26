package in.schoolapp.school;

import in.schoolapp.school.dto.OnboardingStatusResponse;
import in.schoolapp.school.dto.OnboardingStatusResponse.StepStatus;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Computes the live onboarding status by inspecting actual entity state — not just reading
 * {@code school.settings.onboarding} flags. This keeps the UI truthful if flags ever drift.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final SchoolService schoolService;
    private final SchoolClassRepository classRepository;
    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public OnboardingStatusResponse getStatus(UUID schoolId) {
        School school = schoolService.getSchoolEntity(schoolId);

        boolean schoolInfoComplete = true;  // always true once school entity exists
        boolean classesCreated = !classRepository
            .findBySchoolIdOrderBySortOrderAscNameAsc(schoolId).isEmpty();
        boolean staffAdded = staffRepository
            .findBySchoolIdAndActiveTrueOrderByFirstName(schoolId).stream()
            .anyMatch(s -> s.getRole() != StaffRole.PRINCIPAL);
        boolean whatsappConnected = school.isWaConfigured();
        boolean studentsAdded = false;  // Slice 3: will query StudentRepository

        List<StepStatus> steps = List.of(
            new StepStatus("schoolInfoComplete", "School info", schoolInfoComplete),
            new StepStatus("classesCreated", "Classes & sections", classesCreated),
            new StepStatus("staffAdded", "Staff", staffAdded),
            new StepStatus("studentsAdded", "Students", studentsAdded),
            new StepStatus("whatsappConnected", "WhatsApp", whatsappConnected)
        );

        List<String> pending = new ArrayList<>();
        int completed = 0;
        for (StepStatus step : steps) {
            if (step.complete()) completed++;
            else pending.add(step.key());
        }
        int percent = (int) Math.round(100.0 * completed / steps.size());
        return new OnboardingStatusResponse(percent, steps, pending);
    }
}

package in.schoolapp.calendar;

import in.schoolapp.calendar.dto.CalendarDtos.CalendarResponse;
import in.schoolapp.calendar.dto.CalendarDtos.HolidayResponse;
import in.schoolapp.calendar.entity.SchoolHoliday;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tenant school calendar: which weekdays are working days, and which specific dates are
 * holidays/events. Working days live in {@code schools.settings.calendar.workingDays} (JSONB, no
 * schema); holidays are rows in {@code school_holidays}. Exposes {@link #isWorkingDay} so other
 * modules (attendance, timetable) can ask "is the school open on this date?".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolCalendarService {

    static final String SETTINGS_KEY = "calendar";
    static final String WORKING_DAYS = "workingDays";
    /** Default: Monday–Saturday (typical Indian school week). */
    static final List<Integer> DEFAULT_WORKING_DAYS = List.of(1, 2, 3, 4, 5, 6);

    private final SchoolRepository schoolRepository;
    private final SchoolHolidayRepository holidayRepository;

    @Transactional(readOnly = true)
    public CalendarResponse getCalendar(UUID tenantId) {
        return new CalendarResponse(
            getWorkingDays(tenantId),
            holidayRepository.findBySchoolIdOrderByHolidayDateAsc(tenantId).stream()
                .map(HolidayResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public List<Integer> getWorkingDays(UUID tenantId) {
        School s = school(tenantId);
        Object cal = s.getSettings() != null ? s.getSettings().get(SETTINGS_KEY) : null;
        if (cal instanceof Map<?, ?> m && m.get(WORKING_DAYS) instanceof List<?> days && !days.isEmpty()) {
            List<Integer> out = new ArrayList<>();
            for (Object d : days) {
                if (d instanceof Number n) out.add(n.intValue());
            }
            if (!out.isEmpty()) return out;
        }
        return DEFAULT_WORKING_DAYS;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public CalendarResponse setWorkingDays(UUID tenantId, List<Integer> days) {
        List<Integer> clean = days.stream().filter(d -> d != null && d >= 1 && d <= 7).distinct().sorted().toList();
        if (clean.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "At least one working day (1=Mon … 7=Sun) is required");
        }
        School s = school(tenantId);
        Map<String, Object> settings = s.getSettings() != null ? new HashMap<>(s.getSettings()) : new HashMap<>();
        Map<String, Object> cal = settings.get(SETTINGS_KEY) instanceof Map
            ? new HashMap<>((Map<String, Object>) settings.get(SETTINGS_KEY)) : new HashMap<>();
        cal.put(WORKING_DAYS, clean);
        settings.put(SETTINGS_KEY, cal);
        s.setSettings(settings);
        schoolRepository.save(s);
        log.info("Working days set for school={} days={}", tenantId, clean);
        return getCalendar(tenantId);
    }

    @Transactional
    public HolidayResponse addHoliday(UUID tenantId, LocalDate date, String name, String type) {
        SchoolHoliday h = holidayRepository.findBySchoolIdOrderByHolidayDateAsc(tenantId).stream()
            .filter(x -> x.getHolidayDate().equals(date))
            .findFirst()
            .orElseGet(() -> {
                SchoolHoliday n = new SchoolHoliday();
                n.setSchoolId(tenantId);
                n.setHolidayDate(date);
                return n;
            });
        h.setName(name);
        h.setType(type == null || type.isBlank() ? "HOLIDAY" : type.toUpperCase());
        return HolidayResponse.from(holidayRepository.save(h));
    }

    @Transactional
    public void deleteHoliday(UUID tenantId, UUID id) {
        SchoolHoliday h = holidayRepository.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Holiday not found"));
        holidayRepository.delete(h);
    }

    /** True when the school is open on {@code date}: a configured working weekday and not a holiday. */
    @Transactional(readOnly = true)
    public boolean isWorkingDay(UUID tenantId, LocalDate date) {
        if (!getWorkingDays(tenantId).contains(date.getDayOfWeek().getValue())) return false;
        return !holidayRepository.existsBySchoolIdAndHolidayDate(tenantId, date);
    }

    private School school(UUID tenantId) {
        return schoolRepository.findById(tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "School not found"));
    }
}

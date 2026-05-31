package in.schoolapp.school.dto;

import in.schoolapp.common.PiiMasking;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record StaffResponse(
    UUID id,
    UUID schoolId,
    String firstName,
    String lastName,
    String displayName,
    String phone,
    String email,
    String gender,
    LocalDate dateOfJoining,
    StaffRole role,
    boolean active,
    boolean mustResetPassword,
    /** Extended profile (identity/bank/professional); sensitive numbers are masked to last 4. */
    Map<String, Object> profile
) {
    private static final Set<String> SENSITIVE = Set.of("aadhaarNumber", "panNumber", "bankAccountNumber");

    public static StaffResponse from(Staff s) {
        return new StaffResponse(
            s.getId(),
            s.getSchoolId(),
            s.getFirstName(),
            s.getLastName(),
            s.displayName(),
            PiiMasking.phone(s.getPhone()),
            PiiMasking.email(s.getEmail()),
            s.getGender(),
            s.getDateOfJoining(),
            s.getRole(),
            s.isActive(),
            s.isMustResetPassword(),
            maskProfile(s.getProfile())
        );
    }

    private static Map<String, Object> maskProfile(Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        profile.forEach((k, v) -> out.put(k, SENSITIVE.contains(k) ? maskTail(String.valueOf(v)) : v));
        return out;
    }

    /** "123456789012" → "•••• 9012". */
    private static String maskTail(String v) {
        if (v == null || v.length() <= 4) return "••••";
        return "•••• " + v.substring(v.length() - 4);
    }
}

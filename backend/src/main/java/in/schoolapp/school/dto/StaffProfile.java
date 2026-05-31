package in.schoolapp.school.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extended staff/teacher profile captured at registration: personal, identity (Aadhaar/PAN),
 * bank, and professional details. Stored as JSONB on the staff row. Sensitive identity/bank
 * numbers are masked when read back (see {@link StaffResponse}).
 */
public record StaffProfile(
    // Personal
    LocalDate dateOfBirth,
    @Size(max = 300) String address,
    @Size(max = 60)  String emergencyContact,
    // Professional
    @Size(max = 120) String qualification,
    @Size(max = 80)  String designation,
    Integer experienceYears,
    @Size(max = 40)  String employeeCode,
    @Size(max = 20)  String employmentType,   // FULL_TIME | PART_TIME | CONTRACT | VISITING
    // Identity (sensitive)
    @Size(max = 20)  String aadhaarNumber,
    @Size(max = 15)  String panNumber,
    // Bank / account (sensitive)
    @Size(max = 80)  String bankName,
    @Size(max = 30)  String bankAccountNumber,
    @Size(max = 15)  String ifscCode
) {
    /** Non-null fields → a JSONB-friendly map for persistence. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "dateOfBirth", dateOfBirth == null ? null : dateOfBirth.toString());
        put(m, "address", address);
        put(m, "emergencyContact", emergencyContact);
        put(m, "qualification", qualification);
        put(m, "designation", designation);
        put(m, "experienceYears", experienceYears);
        put(m, "employeeCode", employeeCode);
        put(m, "employmentType", employmentType);
        put(m, "aadhaarNumber", aadhaarNumber);
        put(m, "panNumber", panNumber);
        put(m, "bankName", bankName);
        put(m, "bankAccountNumber", bankAccountNumber);
        put(m, "ifscCode", ifscCode);
        return m;
    }

    private static void put(Map<String, Object> m, String k, Object v) {
        if (v != null && !(v instanceof String s && s.isBlank())) m.put(k, v);
    }
}

package in.schoolapp.infirmary.dto;

import in.schoolapp.infirmary.entity.StudentMedicalRecord;

import java.util.UUID;

public record MedicalRecordDto(
    UUID studentId,
    String bloodGroup,
    String allergies,
    String chronicConditions,
    String medications,
    String emergencyContact,
    String emergencyPhone
) {
    public static MedicalRecordDto from(StudentMedicalRecord r) {
        return new MedicalRecordDto(
            r.getStudentId(), r.getBloodGroup(), r.getAllergies(),
            r.getChronicConditions(), r.getMedications(),
            r.getEmergencyContact(), r.getEmergencyPhone());
    }
}

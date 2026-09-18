package in.schoolapp.academics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * A class that participates in an exam. Composite key (examId, classId) — a class is either in the
 * exam or not. {@code schoolId} is carried for row-level tenant isolation.
 */
@Entity
@Table(name = "exam_classes")
@IdClass(ExamClass.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class ExamClass {

    @Id
    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Id
    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    public ExamClass(UUID examId, UUID classId, UUID schoolId) {
        this.examId = examId;
        this.classId = classId;
        this.schoolId = schoolId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private UUID examId;
        private UUID classId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(examId, key.examId) && Objects.equals(classId, key.classId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(examId, classId);
        }
    }
}

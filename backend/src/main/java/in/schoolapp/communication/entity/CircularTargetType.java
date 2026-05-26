package in.schoolapp.communication.entity;

/**
 * Who receives a {@link Circular}:
 * <ul>
 *   <li>{@link #ALL_PARENTS} — every primary parent in the school</li>
 *   <li>{@link #CLASSES} — {@code targetIds} contains school_class ids</li>
 *   <li>{@link #SECTIONS} — {@code targetIds} contains section ids</li>
 *   <li>{@link #STUDENTS} — {@code targetIds} contains student ids (specific parents only)</li>
 * </ul>
 */
public enum CircularTargetType {
    ALL_PARENTS,
    CLASSES,
    SECTIONS,
    STUDENTS
}

package in.schoolapp.academics.entity;

/**
 * How fee dues affect admit-card issue for an exam.
 * <ul>
 *   <li>{@link #BLOCK} – withhold the admit card while fees are outstanding (default).</li>
 *   <li>{@link #ALLOW} – always issue the admit card regardless of dues.</li>
 *   <li>{@link #OVERRIDE} – withhold by default, but Principal/Admin may force-issue per student.</li>
 * </ul>
 */
public enum FeePolicy {
    BLOCK,
    ALLOW,
    OVERRIDE
}

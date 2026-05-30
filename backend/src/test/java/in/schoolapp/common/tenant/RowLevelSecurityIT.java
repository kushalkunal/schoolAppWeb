package in.schoolapp.common.tenant;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the V37 row-level-security migration enforces tenant isolation against a real PostgreSQL,
 * independent of any application query. This is the database-layer backstop for the cross-tenant
 * IDOR (audit #1): even a query that forgets {@code AND school_id = ?} — or that targets another
 * tenant's primary key directly — returns nothing once the session is bound to a tenant.
 * <p>
 * Mirrors the runtime: Flyway migrates as the admin/superuser, then the "application" acts as the
 * non-superuser {@code school_app} role (as {@link RlsTenantConnectionProvider} does) with the
 * {@code app.current_tenant} GUC set.
 * <p>
 * Named {@code *IT} so it runs under failsafe / {@code mvn verify} (and on demand via
 * {@code -Dtest=RowLevelSecurityIT}), keeping the Docker-free unit suite fast.
 */
@Testcontainers
class RowLevelSecurityIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID SCHOOL_A = UUID.randomUUID();
    static final UUID SCHOOL_B = UUID.randomUUID();
    static final UUID STUDENT_A = UUID.randomUUID();
    static final UUID STUDENT_B = UUID.randomUUID();

    @BeforeAll
    static void migrateAndSeed() throws SQLException {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        // Seed as the superuser (bypasses RLS) so both tenants' rows exist regardless of policy.
        try (Connection c = admin()) {
            insertSchool(c, SCHOOL_A, "School A", "9000000001");
            insertSchool(c, SCHOOL_B, "School B", "9000000002");
            insertStudent(c, STUDENT_A, SCHOOL_A, "Alice");
            insertStudent(c, STUDENT_B, SCHOOL_B, "Bob");
        }
    }

    @Test
    void migrationCreatesTenantIsolationPolicyOnTenantTables() throws SQLException {
        try (Connection c = admin();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM pg_policies "
                     + "WHERE policyname = 'tenant_isolation' AND tablename = 'students'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void tenantSessionSeesOnlyItsOwnRows() throws SQLException {
        try (Connection c = appConnFor(SCHOOL_A)) {
            assertThat(countStudents(c)).isEqualTo(1);
            assertThat(singleStudentSchool(c)).isEqualTo(SCHOOL_A);
        }
        try (Connection c = appConnFor(SCHOOL_B)) {
            assertThat(countStudents(c)).isEqualTo(1);
            assertThat(singleStudentSchool(c)).isEqualTo(SCHOOL_B);
        }
    }

    @Test
    void crossTenantLookupByForeignPrimaryKeyReturnsNothing() throws SQLException {
        // The exact IDOR shape: tenant A asks for tenant B's student by id. RLS makes it invisible.
        try (Connection c = appConnFor(SCHOOL_A);
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM students WHERE id = ?")) {
            ps.setObject(1, STUDENT_B);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    void systemSessionWithNoTenantSeesAllRows() throws SQLException {
        // The schedulers / login / webhooks path: GUC unset -> permissive policy -> all tenants.
        try (Connection c = appConnFor(null)) {
            assertThat(countStudents(c)).isEqualTo(2);
        }
    }

    @Test
    void withCheckBlocksInsertingIntoAnotherTenant() throws SQLException {
        try (Connection c = appConnFor(SCHOOL_A)) {
            assertThatThrownBy(() -> insertStudent(c, UUID.randomUUID(), SCHOOL_B, "Mallory"))
                .isInstanceOf(SQLException.class);
        }
    }

    // ---- helpers ----

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** A connection acting exactly as the runtime does: as {@code school_app} with the tenant GUC. */
    private static Connection appConnFor(UUID tenant) throws SQLException {
        Connection c = admin();
        try (Statement s = c.createStatement()) {
            s.execute("SET ROLE school_app");
            if (tenant != null) {
                s.execute("SET app.current_tenant = '" + tenant + "'");
            }
        }
        return c;
    }

    private static int countStudents(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM students")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static UUID singleStudentSchool(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT school_id FROM students")) {
            rs.next();
            return rs.getObject(1, UUID.class);
        }
    }

    private static void insertSchool(Connection c, UUID id, String name, String phone) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO schools (id, name, principal_name, phone, state, board) "
                + "VALUES (?, ?, ?, ?, 'MH', 'CBSE')")) {
            ps.setObject(1, id);
            ps.setString(2, name);
            ps.setString(3, "Principal " + name);
            ps.setString(4, phone);
            ps.executeUpdate();
        }
    }

    private static void insertStudent(Connection c, UUID id, UUID schoolId, String firstName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO students (id, school_id, first_name) VALUES (?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, schoolId);
            ps.setString(3, firstName);
            ps.executeUpdate();
        }
    }
}

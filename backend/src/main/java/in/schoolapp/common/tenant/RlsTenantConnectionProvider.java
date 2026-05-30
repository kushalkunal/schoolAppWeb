package in.schoolapp.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Binds the current tenant onto every JDBC connection Hibernate hands out, so the V24
 * row-level-security policies enforce isolation regardless of what the query says. This is the
 * connection-layer form of "automatic tenant scoping" — and unlike Hibernate {@code @TenantId} it
 * also covers native queries and does not break the cross-tenant schedulers (they resolve to
 * {@link TenantIdentifierResolver#SYSTEM} and run unscoped).
 * <p>
 * On borrow:
 * <ol>
 *   <li>{@code SET ROLE school_app} — RLS does not apply to superusers, so the session must act as
 *       this non-superuser role (created in V24) for policies to bind. Re-applied on every borrow
 *       so a recycled pool connection can never carry another request's role/tenant.</li>
 *   <li>For a real tenant: {@code set_config('app.current_tenant', <uuid>, false)} — the GUC the
 *       policies compare {@code school_id} against. For {@code SYSTEM} the GUC is left unset, which
 *       the policy treats as permissive.</li>
 * </ol>
 * On release the role and GUC are reset before the connection returns to the pool.
 */
@Slf4j
public class RlsTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final transient DataSource dataSource;

    public RlsTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        // Used for tenant-less bootstrap work (schema tooling). No role/tenant binding.
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try (Statement st = connection.createStatement()) {
            st.execute("SET ROLE school_app");
        }
        if (!TenantIdentifierResolver.SYSTEM.equals(tenantIdentifier)) {
            // Parameterised to keep the UUID out of the SQL text; is_local=false so the GUC holds
            // for the whole connection (covers open-session-in-view reads), reset on release.
            try (PreparedStatement ps =
                     connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
                ps.setString(1, tenantIdentifier);
                ps.execute();
            }
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("RESET app.current_tenant");
            st.execute("RESET ROLE");
        } catch (SQLException e) {
            // Best-effort: the next borrow re-establishes role + tenant from scratch anyway, so a
            // failed reset cannot leak one tenant's context into another's request.
            log.warn("Failed to reset RLS session state on connection release", e);
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}

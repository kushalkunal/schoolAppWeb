package in.schoolapp.common.tenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the Java-side branching of the connection provider: every borrow assumes the
 * {@code school_app} role, a real tenant binds the {@code app.current_tenant} GUC, and the SYSTEM
 * sentinel leaves it unset (so trusted tenant-less work stays unscoped).
 */
@ExtendWith(MockitoExtension.class)
class RlsTenantConnectionProviderTest {

    @Test
    void realTenantSetsRoleAndTenantGuc() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement st = mock(Statement.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(st);
        when(conn.prepareStatement(anyString())).thenReturn(ps);

        String tenant = UUID.randomUUID().toString();
        new RlsTenantConnectionProvider(ds).getConnection(tenant);

        verify(st).execute("SET ROLE school_app");
        verify(conn).prepareStatement("SELECT set_config('app.current_tenant', ?, false)");
        verify(ps).setString(1, tenant);
        verify(ps).execute();
    }

    @Test
    void systemTenantSetsRoleButNoTenantGuc() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement st = mock(Statement.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(st);

        new RlsTenantConnectionProvider(ds).getConnection(TenantIdentifierResolver.SYSTEM);

        verify(st).execute("SET ROLE school_app");
        verify(conn, never()).prepareStatement(anyString());
    }

    @Test
    void releaseResetsRoleAndTenantGuc() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement st = mock(Statement.class);
        when(conn.createStatement()).thenReturn(st);

        new RlsTenantConnectionProvider(ds).releaseConnection("anything", conn);

        verify(st).execute("RESET app.current_tenant");
        verify(st).execute("RESET ROLE");
        verify(conn).close();
    }
}

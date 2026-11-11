package com.company.procurement.shared.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Sets {@code app.current_org} on every borrowed connection so PostgreSQL row level security can
 * enforce tenant isolation as defense in depth. The value is always overwritten on acquisition, so
 * pooled connections cannot leak a previous tenant.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

  private static final String SET_TENANT_SQL = "select set_config('app.current_org', ?, false)";

  public TenantAwareDataSource(DataSource delegate) {
    super(delegate);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return configure(super.getConnection());
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return configure(super.getConnection(username, password));
  }

  private static Connection configure(Connection connection) throws SQLException {
    String tenant = TenantContext.get().map(UUID::toString).orElse("");
    try (PreparedStatement statement = connection.prepareStatement(SET_TENANT_SQL)) {
      statement.setString(1, tenant);
      statement.execute();
    }
    return connection;
  }
}

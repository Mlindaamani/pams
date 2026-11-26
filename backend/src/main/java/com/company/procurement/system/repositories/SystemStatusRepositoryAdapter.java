package com.company.procurement.system.repositories;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

@Repository
public class SystemStatusRepositoryAdapter implements SystemStatusRepository {

  private final DataSource dataSource;

  public SystemStatusRepositoryAdapter(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public boolean databaseAvailable() {
    try (Connection connection = dataSource.getConnection()) {
      return connection.isValid(2);
    } catch (Exception ex) {
      return false;
    }
  }
}

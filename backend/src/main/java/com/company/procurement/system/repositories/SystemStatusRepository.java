package com.company.procurement.system.repositories;

/** Infrastructure health port for the system status page. */
public interface SystemStatusRepository {

  boolean databaseAvailable();
}

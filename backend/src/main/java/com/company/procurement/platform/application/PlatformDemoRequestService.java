package com.company.procurement.platform.application;

import com.company.procurement.platform.domain.DemoRequestStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformDemoRequestService {

  private final JdbcTemplate jdbc;

  public PlatformDemoRequestService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Page<DemoRequestView> search(String query, DemoRequestStatus status, Pageable pageable) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder(" where 1 = 1");
    String normalized = query == null ? null : query.trim().toLowerCase();
    if (normalized != null && !normalized.isEmpty()) {
      where.append(" and (lower(name) like ? or lower(email) like ? or lower(company) like ?)");
      String pattern = "%" + normalized + "%";
      args.add(pattern);
      args.add(pattern);
      args.add(pattern);
    }
    if (status != null) {
      where.append(" and status = ?");
      args.add(status.name());
    }

    Long total =
        jdbc.queryForObject(
            "select count(*) from demo_request" + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(pageable.getPageSize());
    pageArgs.add(pageable.getOffset());
    List<DemoRequestView> content =
        jdbc.query(
            "select id, name, email, company, phone, message, status, created_at from demo_request"
                + where
                + " order by "
                + orderBy(pageable)
                + " limit ? offset ?",
            (rs, rowNum) ->
                new DemoRequestView(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("company"),
                    rs.getString("phone"),
                    rs.getString("message"),
                    DemoRequestStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toInstant()),
            pageArgs.toArray());
    return new PageImpl<>(content, pageable, total == null ? 0 : total);
  }

  private static String orderBy(Pageable pageable) {
    if (pageable.getSort().isUnsorted()) {
      return "created_at desc";
    }
    Sort.Order order = pageable.getSort().stream().findFirst().orElse(Sort.Order.desc("createdAt"));
    String column =
        switch (order.getProperty()) {
          case "name" -> "name";
          case "company" -> "company";
          case "status" -> "status";
          default -> "created_at";
        };
    return column + " " + (order.isAscending() ? "asc" : "desc");
  }

  public record DemoRequestView(
      UUID id,
      String name,
      String email,
      String company,
      String phone,
      String message,
      DemoRequestStatus status,
      Instant createdAt) {}
}

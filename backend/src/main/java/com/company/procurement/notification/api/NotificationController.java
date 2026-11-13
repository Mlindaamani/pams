package com.company.procurement.notification.api;

import com.company.procurement.notification.api.dto.NotificationResponse;
import com.company.procurement.notification.api.mapper.NotificationMapper;
import com.company.procurement.notification.application.NotificationService;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.PageRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for in-app notifications for the current user. */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("@permissions.has('notification:read')")
@Tag(name = "Notifications", description = "In-app notifications for the current user")
public class NotificationController {

  private static final Set<String> SORTABLE = Set.of("createdAt");

  private final NotificationService notifications;
  private final NotificationMapper mapper;

  public NotificationController(NotificationService notifications, NotificationMapper mapper) {
    this.notifications = notifications;
    this.mapper = mapper;
  }

  @GetMapping
  public PageResponse<NotificationResponse> list(
      @RequestParam(required = false) Boolean unread,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(notifications.list(unread, pageable), mapper::toResponse);
  }

  @GetMapping("/unread-count")
  public Map<String, Long> unreadCount() {
    return Map.of("unread", notifications.unreadCount());
  }

  @PostMapping("/read-all")
  public Map<String, Integer> markAllRead() {
    return Map.of("updated", notifications.markAllRead());
  }

  @PostMapping("/{id}/read")
  public NotificationResponse markRead(@PathVariable UUID id) {
    return mapper.toResponse(notifications.markRead(id));
  }
}

package com.company.procurement.shared.idempotency;

import com.company.procurement.shared.error.ErrorCodes;
import com.company.procurement.shared.error.ProblemDetailFactory;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Enforces {@code Idempotency-Key} on purchase-request create/submit/cancel and replays the stored
 * response for duplicate attempts with the same payload.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

  public static final String HEADER = "Idempotency-Key";

  private static final Pattern REQUEST_ACTION_PATH =
      Pattern.compile("^/api/v1/purchase-requests/[^/]+/(submit|cancel)$");
  private static final Pattern APPROVAL_ACTION_PATH =
      Pattern.compile("^/api/v1/approvals/[^/]+/(approve|reject|request-changes)$");
  private static final Pattern BULK_APPROVAL_PATH =
      Pattern.compile("^/api/v1/approvals/bulk-approve$");
  private static final Pattern PURCHASE_ORDER_PATH =
      Pattern.compile(
          "^/api/v1/(purchase-requests/[^/]+/purchase-orders|purchase-orders/[^/]+/(issue|cancel|receipts|vendor-invoices|submit-approval|approval/(approve|reject|request-changes)))$");
  private static final Pattern VENDOR_INVOICE_ACTION_PATH =
      Pattern.compile("^/api/v1/vendor-invoices/[^/]+/(approve|reject|dispute|void)$");
  private static final Pattern VENDOR_PAYMENT_PATH =
      Pattern.compile("^/api/v1/vendor-payments(/[^/]+/(approve|pay|cancel))?$");
  private static final Pattern PLATFORM_TENANT_PATH =
      Pattern.compile("^/api/v1/platform/tenants(/[^/]+/(suspend|activate))?$");
  private static final Pattern WEBHOOK_PATH =
      Pattern.compile(
          "^/api/v1/webhooks(/[^/]+/(rotate-secret|deliveries)|/deliveries/[^/]+/retry)?$");

  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;
  private final ProblemDetailFactory problemDetailFactory;

  public IdempotencyFilter(
      IdempotencyService idempotencyService,
      ObjectMapper objectMapper,
      ProblemDetailFactory problemDetailFactory) {
    this.idempotencyService = idempotencyService;
    this.objectMapper = objectMapper;
    this.problemDetailFactory = problemDetailFactory;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return !("/api/v1/purchase-requests".equals(path)
        || REQUEST_ACTION_PATH.matcher(path).matches()
        || APPROVAL_ACTION_PATH.matcher(path).matches()
        || BULK_APPROVAL_PATH.matcher(path).matches()
        || PURCHASE_ORDER_PATH.matcher(path).matches()
        || VENDOR_INVOICE_ACTION_PATH.matcher(path).matches()
        || VENDOR_PAYMENT_PATH.matcher(path).matches()
        || PLATFORM_TENANT_PATH.matcher(path).matches()
        || "/api/v1/webhooks".equals(path)
        || WEBHOOK_PATH.matcher(path).matches());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String key = request.getHeader(HEADER);
    UUID organizationId = TenantContext.get().orElse(null);

    if (key == null || key.isBlank()) {
      writeProblem(
          request,
          response,
          HttpStatus.BAD_REQUEST,
          ErrorCodes.INVALID_REQUEST,
          "Idempotency-Key header is required");
      return;
    }
    if (organizationId == null) {
      writeProblem(
          request,
          response,
          HttpStatus.BAD_REQUEST,
          ErrorCodes.INVALID_REQUEST,
          "Organization context is required");
      return;
    }

    CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
    String requestHash = hash(cachedRequest);

    Optional<IdempotencyService.StoredResponse> existing =
        idempotencyService.find(organizationId, key);
    if (existing.isPresent()) {
      IdempotencyService.StoredResponse stored = existing.get();
      if (!stored.requestHash().equals(requestHash)) {
        writeProblem(
            request,
            response,
            HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorCodes.IDEMPOTENCY_KEY_REUSE,
            "This Idempotency-Key was already used with a different payload");
        return;
      }
      response.setStatus(stored.status());
      stored.headers().forEach(response::setHeader);
      if (stored.headers().containsKey("Content-Type")) {
        response.setContentType(stored.headers().get("Content-Type"));
      }
      response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
      return;
    }

    ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
    try {
      filterChain.doFilter(cachedRequest, cachedResponse);
      if (cachedResponse.getStatus() >= 200 && cachedResponse.getStatus() < 300) {
        idempotencyService.store(
            organizationId,
            key,
            requestHash,
            cachedResponse.getStatus(),
            new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8),
            headers(cachedResponse));
      }
    } finally {
      cachedResponse.copyBodyToResponse();
    }
  }

  private static Map<String, String> headers(ContentCachingResponseWrapper response) {
    Map<String, String> headers = new HashMap<>();
    copyHeader(response, headers, "Location");
    copyHeader(response, headers, "ETag");
    copyHeader(response, headers, "Content-Type");
    return headers;
  }

  private static void copyHeader(
      ContentCachingResponseWrapper response, Map<String, String> headers, String name) {
    String value = response.getHeader(name);
    if (value != null) {
      headers.put(name, value);
    }
  }

  private static String hash(HttpServletRequest request) throws IOException {
    byte[] body = ((CachedBodyHttpServletRequest) request).getBody();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '|');
      digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) '|');
      digest.update(body);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private void writeProblem(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String detail)
      throws IOException {
    ProblemDetail problem =
        problemDetailFactory.create(status, code, detail, request, java.util.List.of());
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}

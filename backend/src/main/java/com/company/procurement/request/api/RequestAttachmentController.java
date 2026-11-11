package com.company.procurement.request.api;

import com.company.procurement.request.api.dto.AttachmentResponse;
import com.company.procurement.request.api.mapper.AttachmentMapper;
import com.company.procurement.request.application.AttachmentUploadCommand;
import com.company.procurement.request.application.RequestAttachmentService;
import com.company.procurement.request.domain.RequestAttachment;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/purchase-requests/{requestId}/attachments")
@Tag(name = "Request attachments", description = "Quotes and specifications stored in MinIO")
@com.company.procurement.subscription.application.RequiresFeature("requests")
public class RequestAttachmentController {

  private final RequestAttachmentService attachments;
  private final AttachmentMapper mapper;

  public RequestAttachmentController(
      RequestAttachmentService attachments, AttachmentMapper mapper) {
    this.attachments = attachments;
    this.mapper = mapper;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("@permissions.has('purchase_request:update')")
  public AttachmentResponse upload(
      @PathVariable UUID requestId, @RequestPart("file") MultipartFile file) throws IOException {
    RequestAttachment attachment =
        attachments.upload(
            requestId, new AttachmentUploadCommand(file.getOriginalFilename(), file.getBytes()));
    return mapper.toResponse(attachment);
  }

  @GetMapping
  @PreAuthorize("@permissions.has('purchase_request:read')")
  public List<AttachmentResponse> list(@PathVariable UUID requestId) {
    return attachments.list(requestId).stream().map(mapper::toResponse).toList();
  }

  @GetMapping("/{attachmentId}")
  @PreAuthorize("@permissions.has('purchase_request:read')")
  public ResponseEntity<InputStreamResource> download(
      @PathVariable UUID requestId, @PathVariable UUID attachmentId) {
    RequestAttachmentService.StoredAttachment stored =
        attachments.download(requestId, attachmentId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(stored.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(stored.fileName()).build().toString())
        .body(new InputStreamResource(stored.stream()));
  }

  @DeleteMapping("/{attachmentId}")
  @PreAuthorize("@permissions.has('purchase_request:update')")
  public ResponseEntity<Void> delete(
      @PathVariable UUID requestId, @PathVariable UUID attachmentId) {
    attachments.delete(requestId, attachmentId);
    return ResponseEntity.noContent().build();
  }
}

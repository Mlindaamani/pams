package com.company.procurement.organization.api;

import com.company.procurement.organization.api.dto.UpdateBrandingRequest;
import com.company.procurement.organization.application.TenantBrandingService;
import com.company.procurement.subscription.application.RequiresFeature;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/branding")
@Tag(name = "Branding", description = "Tenant workspace branding")
public class TenantBrandingController {

  private final TenantBrandingService branding;

  public TenantBrandingController(TenantBrandingService branding) {
    this.branding = branding;
  }

  @GetMapping
  public TenantBrandingService.BrandingView current() {
    return branding.current();
  }

  @PutMapping
  @PreAuthorize("@permissions.has('organization:update')")
  @RequiresFeature("custom_branding")
  public TenantBrandingService.BrandingView update(
      @Valid @RequestBody UpdateBrandingRequest request) {
    return branding.update(
        new TenantBrandingService.BrandingCommand(
            request.displayName(),
            request.primaryColor(),
            request.secondaryColor(),
            request.emailFromName(),
            request.supportEmail(),
            request.cornerRadius(),
            request.fontFamily(),
            request.uiDensity(),
            request.requestLabel(),
            request.orderLabel(),
            request.vendorLabel(),
            request.budgetLabel(),
            request.catalogLabel()));
  }

  @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("@permissions.has('organization:update')")
  @RequiresFeature("custom_branding")
  public TenantBrandingService.BrandingView uploadLogo(@RequestPart("file") MultipartFile file)
      throws java.io.IOException {
    return branding.uploadLogo(file.getBytes(), file.getContentType());
  }

  @PostMapping(value = "/favicon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("@permissions.has('organization:update')")
  @RequiresFeature("custom_branding")
  public TenantBrandingService.BrandingView uploadFavicon(@RequestPart("file") MultipartFile file)
      throws java.io.IOException {
    return branding.uploadFavicon(file.getBytes(), file.getContentType());
  }

  @GetMapping("/favicon")
  public ResponseEntity<byte[]> favicon() {
    TenantBrandingService.Logo favicon = branding.currentFavicon();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(favicon.contentType()))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(favicon.content());
  }

  @DeleteMapping("/favicon")
  @PreAuthorize("@permissions.has('organization:update')")
  @RequiresFeature("custom_branding")
  public TenantBrandingService.BrandingView removeFavicon() {
    return branding.removeFavicon();
  }

  @GetMapping("/logo")
  public ResponseEntity<byte[]> logo() {
    TenantBrandingService.Logo logo = branding.currentLogo();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(logo.contentType()))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(logo.content());
  }

  @DeleteMapping("/logo")
  @PreAuthorize("@permissions.has('organization:update')")
  @RequiresFeature("custom_branding")
  public TenantBrandingService.BrandingView removeLogo() {
    return branding.removeLogo();
  }
}

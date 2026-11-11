package com.company.procurement.shared.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stable public alias for the generated OpenAPI document (used by Bruno and generators). */
@RestController
public class OpenApiAliasController {

  @GetMapping("/openapi.json")
  public void openapi(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    RequestDispatcher dispatcher = request.getRequestDispatcher("/v3/api-docs");
    dispatcher.forward(request, response);
  }
}

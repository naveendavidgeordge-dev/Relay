package com.relay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.relay.application.WorkflowService;
import com.relay.application.view.WorkflowView;
import com.relay.config.RelayProperties;
import com.relay.domain.error.DefinitionValidationException;
import com.relay.domain.error.ErrorCode;
import com.relay.domain.validation.ValidationReport;
import com.relay.domain.validation.Violation;
import com.relay.domain.workflow.WorkflowStatus;
import com.relay.security.ApiTokenStore;
import com.relay.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test: routing, auth, role guards and the uniform error body for workflow routes. */
@WebMvcTest(WorkflowController.class)
@Import({SecurityConfig.class, TraceIdFilter.class})
@EnableConfigurationProperties(RelayProperties.class)
class WorkflowControllerTest {

  private static final String TOKEN = "Bearer relay-demo-token";

  @Autowired MockMvc mvc;
  @MockitoBean WorkflowService workflows;
  @MockitoBean ApiTokenStore apiTokens;

  private static WorkflowView view(String id, WorkflowStatus status) {
    Instant t = Instant.parse("2026-07-04T10:00:00Z");
    return new WorkflowView(
        id, id, null, status, "manual", 1, false, t, t, 0, null, null, null, null, null, null, null);
  }

  @Test
  void missingTokenIs401WithTheUniformErrorBody() throws Exception {
    mvc.perform(get("/workflows"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("unauthorized"))
        .andExpect(jsonPath("$.error.message").exists())
        .andExpect(jsonPath("$.error.trace_id").exists())
        .andExpect(header().exists("X-Trace-Id"));
  }

  @Test
  void wrongTokenIs401() throws Exception {
    when(apiTokens.lookup(anyString())).thenReturn(Optional.empty());
    mvc.perform(get("/workflows").header("Authorization", "Bearer nope"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listReturnsAnArrayWithIdAndStatus() throws Exception {
    when(workflows.list(Optional.empty()))
        .thenReturn(List.of(view("wf_support_triage", WorkflowStatus.PUBLISHED)));
    mvc.perform(get("/workflows").header("Authorization", TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("wf_support_triage"))
        .andExpect(jsonPath("$[0].status").value("published"))
        .andExpect(jsonPath("$[0].published_version").value(1));
  }

  @Test
  void invalidPublishIs422WithCodeAndAllViolations() throws Exception {
    ValidationReport report =
        new ValidationReport(
            List.of(
                Violation.error(
                    ErrorCode.INVALID_NODE_TYPE,
                    "Node 'n1' has unknown type 'teleport'",
                    "n1",
                    "type")));
    when(workflows.publish(eq("wf_bad"), any()))
        .thenThrow(new DefinitionValidationException(report));
    mvc.perform(post("/workflows/wf_bad/publish").header("Authorization", TOKEN))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("invalid_node_type"))
        .andExpect(jsonPath("$.error.message").value("Node 'n1' has unknown type 'teleport'"))
        .andExpect(jsonPath("$.error.details.violations[0].node_id").value("n1"));
  }

  @Test
  void malformedJsonIs400() throws Exception {
    mvc.perform(
            post("/workflows")
                .header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("invalid_request"));
  }

  @Test
  void createReturns201WithLocation() throws Exception {
    when(workflows.create(any(), any())).thenReturn(view("wf_new", WorkflowStatus.DRAFT));
    mvc.perform(
            post("/workflows")
                .header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"wf_new\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/workflows/wf_new"))
        .andExpect(jsonPath("$.status").value("draft"));
  }

  @Test
  void tokenWithoutBuilderRoleCannotCreate() throws Exception {
    when(apiTokens.lookup("rly_operator_only_token_123456"))
        .thenReturn(Optional.of(new ApiTokenStore.Token("ops", List.of("OPERATOR"))));
    mvc.perform(
            post("/workflows")
                .header("Authorization", "Bearer rly_operator_only_token_123456")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("forbidden"));
  }
}

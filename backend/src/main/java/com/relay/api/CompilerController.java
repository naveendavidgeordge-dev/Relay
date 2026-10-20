package com.relay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.relay.application.compiler.CompilerService;
import com.relay.application.compiler.NlEvalService;
import com.relay.security.CurrentActor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Good To Have NL compiler (API_CONTRACT §6, shape suggested): {@code POST /workflows/compile}
 * with {@code {"description": "..."}} returns a validated Draft (stored, unless {@code
 * ?persist=false}) or {@code {"refusal": {...}}}. {@code POST|GET /workflows/compile/eval} runs the
 * eval over {@code data/nl_eval.jsonl} without storing anything.
 */
@RestController
public class CompilerController {

  private final CompilerService compiler;
  private final NlEvalService eval;

  /**
   * Creates the controller.
   *
   * @param compiler compiler
   * @param eval eval
   */
  public CompilerController(CompilerService compiler, NlEvalService eval) {
    this.compiler = compiler;
    this.eval = eval;
  }

  /**
   * Compiles plain English.
   *
   * @param body {@code {"description": "..."}}
   * @param persist store a valid draft (default true)
   * @return draft or refusal
   */
  @PostMapping("/workflows/compile")
  @PreAuthorize("hasRole('BUILDER')")
  public CompilerService.Result compile(
      @RequestBody JsonNode body, @RequestParam(defaultValue = "true") boolean persist) {
    return compiler.compile(body.path("description").asText(null), persist, CurrentActor.name());
  }

  /**
   * Runs the NL eval.
   *
   * @return accuracy report
   */
  @RequestMapping(
      value = "/workflows/compile/eval",
      method = {RequestMethod.GET, RequestMethod.POST})
  @PreAuthorize("hasRole('BUILDER')")
  public NlEvalService.Report eval() {
    return eval.run();
  }
}

package com.relay.engine.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The worker Template Method: acquire → execute → acknowledge, with error containment. */
class AbstractWorkerLoopTest {

  /** A loop that processes a fixed batch then stops. */
  static final class ScriptedLoop extends AbstractWorkerLoop<String, String> {
    final List<String> calls = new ArrayList<>();
    private List<String> batch;

    ScriptedLoop(List<String> batch) {
      this.batch = batch;
    }

    @Override
    protected void onStart() {
      calls.add("start");
    }

    @Override
    protected void onStop() {
      calls.add("stop");
    }

    @Override
    protected List<String> acquire() {
      List<String> b = batch;
      batch = List.of();
      return b;
    }

    @Override
    protected void dispatch(String job) {
      process(job);
    }

    @Override
    protected String execute(String job) {
      if (job.equals("boom")) {
        throw new IllegalStateException("executor blew up");
      }
      calls.add("execute:" + job);
      return "ok";
    }

    @Override
    protected String onExecuteError(String job, RuntimeException e) {
      calls.add("error:" + job);
      return "retry-later";
    }

    @Override
    protected void acknowledge(String job, String outcome) {
      calls.add("ack:" + job + "=" + outcome);
    }

    @Override
    protected void idle() {
      stop();
    }
  }

  @Test
  void runsTheTemplateInOrderAndContainsErrors() {
    ScriptedLoop loop = new ScriptedLoop(List.of("a", "boom", "b"));
    loop.run();
    assertThat(loop.calls)
        .containsExactly(
            "start",
            "execute:a",
            "ack:a=ok",
            "error:boom",
            "ack:boom=retry-later",
            "execute:b",
            "ack:b=ok",
            "stop");
  }
}

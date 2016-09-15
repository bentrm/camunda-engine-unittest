/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.unittest;

import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.MismatchingMessageCorrelationException;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;

/**
 * @author Daniel Meyer
 * @author Martin Schimak
 */
public class SimpleTestCase {

  @Rule
  public ProcessEngineRule rule = new ProcessEngineRule();

  @Test
  @Deployment(resources = {"testProcess.bpmn", "testCase.cmmn"})
  public void shouldExecuteProcess() {
    final CaseService caseService = processEngine().getCaseService();

    // Given we create a new process instance
    final ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("testProcess");
    // Then it should be active
    assertThat(processInstance).isActive();

    // And it should be the only instance
    assertThat(runtimeService()
            .createProcessInstanceQuery()
            .list()
            .size()
    ).isEqualTo(1);

    // And there should exist a case instance
    final List<CaseInstance> caseInstances = caseService
            .createCaseInstanceQuery()
            .list();
    assertThat(caseInstances
            .size()
    ).isEqualTo(1);

    // The case should have spawned a task
    assertThat(taskService()
            .createTaskQuery()
            .list()
            .size()
    ).isEqualTo(1);

    // Then the process instance is alive and well
    assertThat(!processInstance.isEnded());

    // The case instance feels the same
    final CaseInstance caseInstance = caseInstances.get(0);
    assertThat(caseInstance.isActive());
    assertThat(!caseInstance.isCompleted());
    assertThat(!caseInstance.isTerminated());

    // We should be able to correlate a kill message
    try {
      processEngine().getRuntimeService().createMessageCorrelation("kill").correlate();
    } catch (MismatchingMessageCorrelationException e) {
      fail("Message correlation failed");
    }

    // Then the process instance should be ended
    assertThat(processInstance.isEnded());

    // as well as the case instance
    assertThat(!caseInstance.isActive());
    assertThat(!caseInstance.isCompleted());
    assertThat(caseInstance.isTerminated());

    // With the case instance gone, no tasks should be left
    assertThat(taskService()
            .createTaskQuery()
            .active()
            .list()
            .size()
    ).isEqualTo(0);
  }

}

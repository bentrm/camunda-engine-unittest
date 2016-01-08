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

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Daniel Meyer
 * @author Martin Schimak
 */
public class SimpleTestCase {

  @Rule
  public ProcessEngineRule rule = new ProcessEngineRule();

  private final String USER_NAME = "testUser";
  private final String GROUP_NAME = "testGroup";
  private final String PROCESS_DEFINITION_KEY = "testProcess";

  private User user;
  private Group group;
  private Authentication authentication;

  @After
  public void after() {
    identityService().clearAuthentication();
  }

  @Test
  @Deployment(resources = {"testProcess.bpmn"})
  public void testAttachmentAuth() {
    user = identityService().newUser(USER_NAME);
    group = identityService().newGroup(GROUP_NAME);

    // create test users
    identityService().saveUser(user);
    identityService().saveGroup(group);
    identityService().createMembership(user.getId(), group.getId());

    // activate authorization
    processEngine().getProcessEngineConfiguration().setAuthorizationEnabled(true);

    // create authorization
    Authorization processDefinitionAuth = authorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    Authorization processInstanceAuth = authorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);

    // set user/groups
    processDefinitionAuth.setGroupId(group.getId());
    processInstanceAuth.setGroupId(group.getId());

    // set resources
    processDefinitionAuth.setResource(Resources.PROCESS_DEFINITION);
    processInstanceAuth.setResource(Resources.PROCESS_INSTANCE);

    // set resource ids
    processDefinitionAuth.setResourceId(PROCESS_DEFINITION_KEY);
    processInstanceAuth.setResourceId(Authorization.ANY);

    // set permissions
    processDefinitionAuth.addPermission(Permissions.READ);
    processDefinitionAuth.addPermission(Permissions.CREATE_INSTANCE);
    processDefinitionAuth.addPermission(Permissions.READ_TASK);

    processInstanceAuth.addPermission(Permissions.CREATE);

    // save authorization
    authorizationService().saveAuthorization(processDefinitionAuth);
    authorizationService().saveAuthorization(processInstanceAuth);

    // login in as new role
    authentication = new Authentication(user.getId(), Collections.singletonList(group.getId()));
    identityService().setAuthentication(authentication);
    Authentication currentAuthentication = identityService().getCurrentAuthentication();

    assertEquals(identityService().getCurrentAuthentication(), authentication);
    assertEquals(currentAuthentication.getGroupIds().size(), 1);
    assertEquals(currentAuthentication.getGroupIds().get(0), GROUP_NAME);

    // start process instance, which should be allowed
    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey(PROCESS_DEFINITION_KEY);

    // get active task
    Task currentTask = taskService().createTaskQuery()
            .executionId(processInstance.getProcessInstanceId())
            .singleResult();

    try {
      taskService().setVariable(currentTask.getId(), "test", "test");
      fail("User should not be allowed to update task with variable.");
    } catch(Exception e) {
      assert(e instanceof AuthorizationException);
    }

    try {
      String attachmentValue = "url";
      taskService().createAttachment("link", currentTask.getId(), processInstance.getProcessInstanceId(), "title", "desc", attachmentValue);
      fail("User should not be allowed to update task with attachment.");
    } catch (Exception e) {
      assert(e instanceof AuthorizationException);
    }
  }

}

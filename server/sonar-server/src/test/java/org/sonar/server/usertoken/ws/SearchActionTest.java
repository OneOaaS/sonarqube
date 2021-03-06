/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.usertoken.ws;

import com.google.common.base.Throwables;
import java.io.IOException;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.user.UserDbTester;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.WsUserTokens.SearchWsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.user.UserTesting.newUserDto;
import static org.sonar.db.user.UserTokenTesting.newUserToken;
import static org.sonar.test.JsonAssert.assertJson;
import static org.sonarqube.ws.client.usertoken.UserTokensWsParameters.PARAM_LOGIN;


public class SearchActionTest {
  static final String GRACE_HOPPER = "grace.hopper";
  static final String ADA_LOVELACE = "ada.lovelace";

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  UserDbTester userDb = new UserDbTester(db);
  DbClient dbClient = db.getDbClient();
  final DbSession dbSession = db.getSession();

  WsActionTester ws = new WsActionTester(new SearchAction(dbClient, userSession));

  @Before
  public void setUp() {
    userSession.login().setGlobalPermissions(GlobalPermissions.SYSTEM_ADMIN);
    userDb.insertUser(newUserDto().setLogin(GRACE_HOPPER));
    userDb.insertUser(newUserDto().setLogin(ADA_LOVELACE));
  }

  @Test
  public void search_json_example() {
    dbClient.userTokenDao().insert(dbSession, newUserToken()
      .setCreatedAt(1448523067221L)
      .setName("Project scan on Travis")
      .setLogin(GRACE_HOPPER));
    dbClient.userTokenDao().insert(dbSession, newUserToken()
      .setCreatedAt(1438523067221L)
      .setName("Project scan on AppVeyor")
      .setLogin(GRACE_HOPPER));
    dbClient.userTokenDao().insert(dbSession, newUserToken()
      .setCreatedAt(1428523067221L)
      .setName("Project scan on Jenkins")
      .setLogin(GRACE_HOPPER));
    dbClient.userTokenDao().insert(dbSession, newUserToken()
      .setCreatedAt(141456787123L)
      .setName("Project scan on Travis")
      .setLogin(ADA_LOVELACE));
    dbSession.commit();

    String response = ws.newRequest()
      .setParam(PARAM_LOGIN, GRACE_HOPPER)
      .execute().getInput();

    assertJson(response).isSimilarTo(getClass().getResource("search-example.json"));
  }

  @Test
  public void a_user_can_search_its_own_token() {
    userSession.login(GRACE_HOPPER).setGlobalPermissions(GlobalPermissions.SCAN_EXECUTION);
    dbClient.userTokenDao().insert(dbSession, newUserToken()
      .setCreatedAt(1448523067221L)
      .setName("Project scan on Travis")
      .setLogin(GRACE_HOPPER));
    db.commit();

    SearchWsResponse response = newRequest(null);

    assertThat(response.getUserTokensCount()).isEqualTo(1);
  }

  @Test
  public void fail_when_login_does_not_exist() {
    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("User with login 'unknown-login' not found");

    newRequest("unknown-login");
  }

  @Test
  public void fail_when_insufficient_privileges() {
    userSession.login().setGlobalPermissions(GlobalPermissions.SCAN_EXECUTION);
    expectedException.expect(ForbiddenException.class);

    newRequest(GRACE_HOPPER);
  }

  private SearchWsResponse newRequest(@Nullable String login) {
    TestRequest testRequest = ws.newRequest()
      .setMediaType(MediaTypes.PROTOBUF);
    if (login != null) {
      testRequest.setParam(PARAM_LOGIN, login);
    }

    TestResponse response = testRequest.execute();

    try {
      return SearchWsResponse.parseFrom(response.getInputStream());
    } catch (IOException e) {
      Throwables.propagate(e);
    }

    throw new IllegalStateException("unreachable");
  }
}

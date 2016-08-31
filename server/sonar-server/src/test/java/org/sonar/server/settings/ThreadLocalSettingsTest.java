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
package org.sonar.server.settings;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.property.PropertyDto;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class ThreadLocalSettingsTest {

  private static final String A_KEY = "a_key";

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);

  private SystemSettings systemSettings = new SystemSettings(new PropertyDefinitions(), new Properties());
  private ThreadLocalSettings underTest = new ThreadLocalSettings(dbTester.getDbClient(), systemSettings);

  @Test
  public void load_property_from_system() {
    systemSettings.set("foo", "from system");

    assertThat(underTest.get("foo").get()).isEqualTo("from system");
  }

  @Test
  public void database_properties_are_not_cached_by_default() {
    insertPropertyIntoDb("foo", "from db");
    assertThat(underTest.get("foo").get()).isEqualTo("from db");

    deletePropertyFromDb("foo");
    // no cache, change is visible immediately
    assertThat(underTest.get("foo")).isNotPresent();
  }

  @Test
  public void system_settings_have_precedence_over_database() {
    systemSettings.set("foo", "from system");
    insertPropertyIntoDb("foo", "from db");

    assertThat(underTest.get("foo").get()).isEqualTo("from system");
  }

  @Test
  public void getProperties_are_all_properties_with_value() {
    systemSettings.set("system", "from system");
    insertPropertyIntoDb("db", "from db");
    insertPropertyIntoDb("empty", null);

    assertThat(underTest.getProperties()).containsOnly(entry("system", "from system"), entry("db", "from db"), entry("empty", ""));
  }

  @Test
  public void load_creates_a_thread_specific_cache() throws InterruptedException {
    insertPropertyIntoDb(A_KEY, "v1");

    underTest.load();
    assertThat(underTest.get(A_KEY).get()).isEqualTo("v1");

    deletePropertyFromDb(A_KEY);
    // the main thread still has "v1" in cache, but not new thread
    assertThat(underTest.get(A_KEY).get()).isEqualTo("v1");
    verifyValueInNewThread(null);

    insertPropertyIntoDb(A_KEY, "v2");
    // the main thread still has the old value "v1" in cache, but new thread loads "v2"
    assertThat(underTest.get(A_KEY).get()).isEqualTo("v1");
    verifyValueInNewThread("v2");

    underTest.unload();
  }

  @Test
  public void load_throws_ISE_if_load_called_twice_without_unload_in_between() {
    underTest.load();

    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("load called twice for thread '" + Thread.currentThread().getName()
      + "' or state wasn't cleared last time it was used");

    underTest.load();
  }

  @Test
  public void set_does_nothing_if_no_thread_specific_cache() {
    underTest.set(A_KEY, "bar");
    assertThat(underTest.get(A_KEY)).isNotPresent();
  }

  @Test
  public void set_updates_value_in_thread_specific_cache() throws InterruptedException {
    underTest.load();

    underTest.set(A_KEY, "bar");
    assertThat(underTest.get(A_KEY).get()).isEqualTo("bar");

    verifyValueInNewThread(null);

    underTest.unload();
  }

  @Test
  public void remove_does_nothing_if_no_thread_specific_cache() {
    insertPropertyIntoDb(A_KEY, "bar");

    underTest.remove(A_KEY);

    assertThat(underTest.get(A_KEY).get()).isEqualTo("bar");
  }

  @Test
  public void remove_updates_value_in_thread_specific_cache() throws InterruptedException {
    insertPropertyIntoDb(A_KEY, "bar");

    underTest.load();

    underTest.remove(A_KEY);
    assertThat(underTest.get(A_KEY)).isNotPresent();

    verifyValueInNewThread("bar");

    underTest.unload();
  }

  @Test
  public void null_value_in_db_is_considered_as_empty_string() {
    insertPropertyIntoDb(A_KEY, null);
    assertThat(underTest.get(A_KEY).get()).isEqualTo("");

    // same behavior with cache
    underTest.load();
    assertThat(underTest.get(A_KEY).get()).isEqualTo("");
    underTest.unload();
  }

  @Test
  public void test_empty_value_in_db() {
    insertPropertyIntoDb(A_KEY, "");
    assertThat(underTest.get(A_KEY).get()).isEqualTo("");

    // same behavior with cache
    underTest.load();
    assertThat(underTest.get(A_KEY).get()).isEqualTo("");
    underTest.unload();
  }

  @Test
  public void keep_in_thread_cache_the_fact_that_a_property_is_not_in_db() {
    underTest.load();
    assertThat(underTest.get(A_KEY)).isNotPresent();

    insertPropertyIntoDb(A_KEY, "bar");
    // do not execute new SQL request, cache contains the information of missing property
    assertThat(underTest.get(A_KEY)).isNotPresent();

    underTest.unload();
  }

  private void insertPropertyIntoDb(String key, String value) {
    dbTester.getDbClient().propertiesDao().insertProperty(new PropertyDto().setKey(key).setValue(value));
  }

  private void deletePropertyFromDb(String key) {
    dbTester.getDbClient().propertiesDao().deleteGlobalProperty(key);
  }

  private void verifyValueInNewThread(@Nullable String expectedValue) throws InterruptedException {
    CacheCaptorThread captor = new CacheCaptorThread();
    captor.verifyValue(expectedValue);
  }

  private class CacheCaptorThread extends Thread {
    private final CountDownLatch latch = new CountDownLatch(1);
    private String value;

    void verifyValue(@Nullable String expectedValue) throws InterruptedException {
      this.start();
      this.latch.await(5, SECONDS);
      assertThat(value).isEqualTo(expectedValue);
    }

    @Override
    public void run() {
      try {
        underTest.load();
        value = underTest.get(A_KEY).orElse(null);
        latch.countDown();
      } finally {
        underTest.unload();
      }
    }
  }

}

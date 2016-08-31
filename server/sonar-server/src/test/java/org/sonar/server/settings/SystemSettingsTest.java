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

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Map;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.config.PropertyDefinitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class SystemSettingsTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void test_get() {
    SystemSettings underTest = create(ImmutableMap.of("foo", "bar"));

    assertThat(underTest.get("foo").get()).isEqualTo("bar");
    assertThat(underTest.get("missing")).isNotPresent();
    assertThat(underTest.getProperties()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void test_set() {
    SystemSettings underTest = create(ImmutableMap.of("foo", "bar"));

    underTest.set("foo", "wiz");

    assertThat(underTest.get("foo").get()).isEqualTo("wiz");
    assertThat(underTest.getProperties()).containsOnly(entry("foo", "wiz"));
  }

  @Test
  public void test_remove() {
    SystemSettings underTest = create(ImmutableMap.of("foo", "bar"));

    underTest.remove("foo");

    assertThat(underTest.get("foo")).isNotPresent();
    assertThat(underTest.getProperties()).isEmpty();
  }

  @Test
  public void load_encryption_secret_key() throws Exception {
    File secretKey = temp.newFile();
    SystemSettings underTest = create(ImmutableMap.of("foo", "bar", "sonar.secretKeyPath", secretKey.getAbsolutePath()));

    assertThat(underTest.getEncryption().hasSecretKey()).isTrue();
  }

  @Test
  public void encryption_secret_key_is_undefined_by_default() {
    SystemSettings underTest = create(ImmutableMap.of("foo", "bar"));

    assertThat(underTest.getEncryption().hasSecretKey()).isFalse();
  }

  private SystemSettings create(Map<String, String> systemProps) {
    Properties props = new Properties();
    props.putAll(systemProps);
    return new SystemSettings(new PropertyDefinitions(), props);
  }
}

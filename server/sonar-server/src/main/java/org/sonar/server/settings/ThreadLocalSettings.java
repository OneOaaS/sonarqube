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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.config.Settings;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.property.PropertyDto;

import static com.google.common.base.Preconditions.checkState;
import static org.apache.commons.lang.StringUtils.defaultString;

/**
 * Merge of {@link SystemSettings} and the global properties stored in the db table "properties". These
 * settings do not contain the settings specific to a project.
 *
 * <p>
 * System settings have precedence on others.
 * </p>
 *
 * <p>
 * The thread-local cache is optional. It is disabled when the method {@link #unload()} has not
 * been called. That allows to remove complexity with handling of cleanup of thread-local cache
 * on daemon threads (notifications) or startup "main" thread.
 * </p>
 */
@ServerSide
@ComputeEngineSide
public class ThreadLocalSettings extends Settings {

  private final DbClient dbClient;
  private final SystemSettings systemSettings;
  private final ThreadLocal<Map<String, String>> dbPropsThreadLocal = new ThreadLocal<>();

  public ThreadLocalSettings(DbClient dbClient, SystemSettings systemSettings) {
    super(systemSettings.getDefinitions(), systemSettings.getEncryption());
    this.dbClient = dbClient;
    this.systemSettings = systemSettings;
  }

  @Override
  protected Optional<String> get(String key) {
    // search for the first value available in
    // 1. system properties
    // 2. thread local cache (if enabled)
    // 3. db

    Optional<String> value = systemSettings.get(key);
    if (value.isPresent()) {
      return value;
    }

    String loadedValue = null;
    Map<String, String> dbProps = dbPropsThreadLocal.get();
    if (dbProps != null && dbProps.containsKey(key)) {
      // the fact that property is missing from db is cached.
      // In this case key is present in cache but loadedValue is null
      loadedValue = dbProps.get(key);
    } else {
      PropertyDto dto = dbClient.propertiesDao().selectGlobalProperty(key);
      if (dto != null) {
        loadedValue = defaultString(dto.getValue());
      }
      if (dbProps != null) {
        dbProps.put(key, loadedValue);
      }
    }
    return Optional.ofNullable(loadedValue);
  }

  @Override
  protected void set(String key, String value) {
    Map<String, String> dbProps = dbPropsThreadLocal.get();
    if (dbProps != null) {
      dbProps.put(key, value);
    }
  }

  @Override
  protected void remove(String key) {
    Map<String, String> dbProps = dbPropsThreadLocal.get();
    if (dbProps != null) {
      dbProps.put(key, null);
    }
  }

  /**
   * Enables the thread specific cache of settings.
   *
   * @throws IllegalStateException if the current thread already has specific cache
   */
  public void load() {
    checkState(dbPropsThreadLocal.get() == null,
      "load called twice for thread '%s' or state wasn't cleared last time it was used", Thread.currentThread().getName());
    dbPropsThreadLocal.set(new HashMap<>());
  }

  /**
   * Clears the cache specific to the current thread (if any).
   */
  public void unload() {
    dbPropsThreadLocal.remove();
  }

  @Override
  public Map<String, String> getProperties() {
    ImmutableMap.Builder<String,String> builder = ImmutableMap.builder();
    try (DbSession dbSession = dbClient.openSession(false)) {
      dbClient.propertiesDao().selectGlobalProperties(dbSession)
        .forEach(p -> builder.put(p.getKey(), defaultString(p.getValue())));
    }
    builder.putAll(systemSettings.getProperties());
    return builder.build();
  }
}

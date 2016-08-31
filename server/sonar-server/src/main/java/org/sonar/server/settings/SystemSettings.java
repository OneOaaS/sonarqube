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
import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.sonar.api.CoreProperties;
import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.config.Encryption;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.server.ServerSide;

/**
 * Settings defined by conf/sonar.properties and JVM command-line. This component does not connect to database
 * so don't load the settings stored in table "properties".
 */
@ServerSide
@ComputeEngineSide
public class SystemSettings extends Settings {

  private final Map<String, String> props;

  public SystemSettings(PropertyDefinitions definitions, Properties props) {
    super(definitions, new Encryption(null));
    this.props = new HashMap<>(Maps.fromProperties(props));

    // TODO something wrong about lifecycle here. It could be improved
    getEncryption().setPathToSecretKey(props.getProperty(CoreProperties.ENCRYPTION_SECRET_KEY_PATH));
  }

  @Override
  protected Optional<String> get(String key) {
    return Optional.ofNullable(props.get(key));
  }

  @Override
  protected void set(String key, String value) {
    this.props.put(key, value);
  }

  @Override
  protected void remove(String key) {
    this.props.remove(key);
  }

  @Override
  public Map<String, String> getProperties() {
    return ImmutableMap.copyOf(props);
  }
}

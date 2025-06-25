/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.stargate.db.cassandra.impl;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.stargate.db.ClientInfo;
import io.stargate.db.metrics.api.ClientInfoMetricsTagProvider;
import java.util.ArrayList;
import java.util.List;

/** Simple implementation of ClientInfoMetricsTagProvider for Cassandra 5.0 persistence. */
public class SimpleClientInfoTagProvider implements ClientInfoMetricsTagProvider {

  @Override
  public Tags getClientInfoTags(ClientInfo clientInfo) {
    List<Tag> tags = new ArrayList<>();

    if (clientInfo != null) {
      if (clientInfo.remoteAddress() != null) {
        tags.add(Tag.of("client_address", clientInfo.remoteAddress().toString()));
      }
    }

    return Tags.of(tags);
  }

  @Override
  public Tags getClientInfoTagsByDriver(ClientInfo clientInfo) {
    List<Tag> tags = new ArrayList<>();

    if (clientInfo != null) {
      clientInfo
          .driverInfo()
          .ifPresent(
              driverInfo -> {
                tags.add(Tag.of(TAG_KEY_DRIVER_NAME, driverInfo.name()));
                driverInfo
                    .version()
                    .ifPresent(version -> tags.add(Tag.of(TAG_KEY_DRIVER_VERSION, version)));
              });
    }

    return Tags.of(tags);
  }
}

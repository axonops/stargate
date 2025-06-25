/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.stargate.transport;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The native (CQL binary) protocol version.
 *
 * <p>Some versions may be in beta, which means that the client must specify the beta flag in the
 * frame for the version to be considered valid. Beta versions must have the word "beta" in their
 * description, this is mandated by the specs.
 */
public enum ProtocolVersion implements Comparable<ProtocolVersion> {
  // The order is important as it defines the chronological history of versions, which is used
  // to determine if a feature is supported or some serdes formats
  V1(1, "v1", false), // no longer supported
  V2(2, "v2", false), // no longer supported
  V3(3, "v3", false),
  V4(4, "v4", false),
  V5(5, "v5-beta", true);

  /** The version number */
  private final int num;

  /** A description of the version, beta versions should have the word "-beta" */
  private final String descr;

  /** Set this to true for beta versions */
  private final boolean beta;

  ProtocolVersion(int num, String descr, boolean beta) {
    this.num = num;
    this.descr = descr;
    this.beta = beta;
  }

  /**
   * The supported versions stored as an array, these should be private and are required for fast
   * decoding
   */
  private static final ProtocolVersion[] OS_VERSIONS = new ProtocolVersion[] {V3, V4, V5};

  static final ProtocolVersion MIN_OS_VERSION = OS_VERSIONS[0];
  static final ProtocolVersion MAX_OS_VERSION = OS_VERSIONS[OS_VERSIONS.length - 1];

  /** All supported versions */
  public static final EnumSet<ProtocolVersion> SUPPORTED = EnumSet.of(V3, V4, V5);

  public static final List<String> SUPPORTED_VERSION_NAMES =
      SUPPORTED.stream().map(ProtocolVersion::toString).collect(Collectors.toList());

  /** Old unsupported versions, this is OK as long as we never add newer unsupported versions */
  public static final EnumSet<ProtocolVersion> UNSUPPORTED = EnumSet.complementOf(SUPPORTED);

  /** The preferred versions */
  public static final ProtocolVersion CURRENT = V4;

  public static final Optional<ProtocolVersion> BETA = Optional.of(V5);

  public static List<String> supportedVersions() {
    return SUPPORTED_VERSION_NAMES;
  }

  public static List<ProtocolVersion> supportedVersionsStartingWith(
      ProtocolVersion smallestVersion) {
    ArrayList<ProtocolVersion> versions = new ArrayList<>(SUPPORTED.size());
    for (ProtocolVersion version : SUPPORTED) {
      if (version.isGreaterOrEqualTo(smallestVersion)) {
        versions.add(version);
      }
    }
    return versions;
  }

  public static ProtocolVersion decode(int versionNum, boolean allowOlderProtocols) {
    ProtocolVersion ret = null;
    if (versionNum >= MIN_OS_VERSION.num && versionNum <= MAX_OS_VERSION.num) {
      ret = OS_VERSIONS[versionNum - MIN_OS_VERSION.num];
    }

    if (ret == null) {
      // if this is not a supported version check the old versions
      for (ProtocolVersion version : UNSUPPORTED) {
        // if it is an old version that is no longer supported this ensures that we reply
        // with that same version
        if (version.num == versionNum)
          throw new ProtocolException(ProtocolVersion.invalidVersionMessage(versionNum), version);
      }

      // If the version is invalid reply with the highest version that we support
      throw new ProtocolException(invalidVersionMessage(versionNum), MAX_OS_VERSION);
    }
    if (!allowOlderProtocols && ret.isSmallerThan(CURRENT)) {
      throw new ProtocolException(
          String.format("Rejecting Protocol Version %s < %s.", ret, ProtocolVersion.CURRENT));
    }
    return ret;
  }

  public boolean isBeta() {
    return beta;
  }

  public static String invalidVersionMessage(int version) {
    return String.format(
        "Invalid or unsupported protocol version (%d); supported versions are (%s)",
        version, String.join(", ", ProtocolVersion.supportedVersions()));
  }

  public int asInt() {
    return num;
  }

  public boolean supportsChecksums() {
    return num >= V5.asInt();
  }

  @Override
  public String toString() {
    // This format is mandated by the protocl specs for the SUPPORTED message, see OptionsMessage
    // execute().
    return String.format("%d/%s", num, descr);
  }

  public final boolean isGreaterThan(ProtocolVersion other) {
    return num > other.num;
  }
  /**
   * Return true if this version is greater or equal to the specified OSS version.
   *
   * @param ossVersion - the OSS version to compare this to
   * @param dseVersion - deprecated parameter, kept for compatibility but ignored
   * @return true if this version is greater or equal to the OSS version
   */
  public final boolean isGreaterOrEqualTo(ProtocolVersion ossVersion, ProtocolVersion dseVersion) {
    return ordinal() >= ossVersion.ordinal();
  }

  public final boolean isGreaterOrEqualTo(ProtocolVersion other) {
    return num >= other.num;
  }

  public final boolean isSmallerThan(ProtocolVersion other) {
    return num < other.num;
  }

  public final boolean isSmallerOrEqualTo(ProtocolVersion other) {
    return num <= other.num;
  }

  public com.datastax.oss.driver.api.core.ProtocolVersion toDriverVersion() {
    switch (this) {
      case V1: // fallthrough on purpose
      case V2:
        // This should likely be rejected much sooner but ...
        throw new ProtocolException("Unsupported protocol version: " + this);
      case V3:
        return com.datastax.oss.driver.api.core.ProtocolVersion.V3;
      case V4:
        return com.datastax.oss.driver.api.core.ProtocolVersion.V4;
      case V5:
        return com.datastax.oss.driver.api.core.ProtocolVersion.V5;
      default:
        throw new AssertionError("Unhandled protocol version: " + this);
    }
  }
}

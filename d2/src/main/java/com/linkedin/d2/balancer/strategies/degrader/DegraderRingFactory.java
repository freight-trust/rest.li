/*
   Copyright (c) 2016 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.d2.balancer.strategies.degrader;

import com.linkedin.d2.balancer.util.hashing.MPConsistentHashRing;
import com.linkedin.d2.balancer.util.hashing.Ring;
import com.linkedin.util.degrader.CallTracker;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Ang Xu
 */
public class DegraderRingFactory<T> implements RingFactory<T>
{
  public static final String POINT_BASED_CONSISTENT_HASH = "pointBased";
  public static final String MULTI_PROBE_CONSISTENT_HASH = "multiProbe";
  public static final String DISTRIBUTION_NON_HASH = "distributionBased";

  private static final Logger _log = LoggerFactory.getLogger(DegraderRingFactory.class);

  private final RingFactory<T> _ringFactory;

  public DegraderRingFactory(DegraderLoadBalancerStrategyConfig config)
  {
    RingFactory<T> factory;

    String consistentHashAlgorithm = config.getConsistentHashAlgorithm();
    if (consistentHashAlgorithm == null)
    {
      // Choose the right algorithm if consistentHashAlgorithm is not specified
      if (isAffinityRoutingEnabled(config))
      {
        _log.info("URI Regex hash is specified, use multiProbe algorithm for consistent hashing");
        factory = new MPConsistentHashRingFactory<>(config.getNumProbes(), config.getPointsPerHost());
      }
      else
      {
        _log.info("DistributionBased algorithm is used for consistent hashing");
        factory = new DistributionNonDiscreteRingFactory<>();
      }
    }
    else if (consistentHashAlgorithm.equalsIgnoreCase(POINT_BASED_CONSISTENT_HASH))
    {
      factory = new PointBasedConsistentHashRingFactory<>(config);
    }
    else if (MULTI_PROBE_CONSISTENT_HASH.equalsIgnoreCase(consistentHashAlgorithm))
    {
      factory = new MPConsistentHashRingFactory<>(config.getNumProbes(), config.getPointsPerHost());
    }
    else if (DISTRIBUTION_NON_HASH.equalsIgnoreCase(consistentHashAlgorithm)) {
      if (isAffinityRoutingEnabled((config)))
      {
        _log.warn("URI Regex hash is specified but distribution based ring is picked, falling back to multiProbe ring");
        factory = new MPConsistentHashRingFactory<>(config.getNumProbes(), config.getPointsPerHost());
      }
      else
      {
        factory = new DistributionNonDiscreteRingFactory<>();
      }
    }
    else
    {
      _log.warn("Unknown consistent hash algorithm {}, falling back to multiprobe hash ring with default settings", consistentHashAlgorithm);
      factory = new MPConsistentHashRingFactory<>(MPConsistentHashRing.DEFAULT_NUM_PROBES, MPConsistentHashRing.DEFAULT_POINTS_PER_HOST);
    }

    if (config.getBoundedLoadBalancingFactor() > 1) {
      factory = new BoundedLoadConsistentHashRingFactory<>(factory, config.getBoundedLoadBalancingFactor());
    }

    _ringFactory = factory;
  }

  @Override
  public Ring<T> createRing(Map<T, Integer> pointsMap) {
    return _ringFactory.createRing(pointsMap);
  }

  @Override
  public Ring<T> createRing(Map<T, Integer> pointsMap, Map<T, CallTracker> callTrackerMap) {
    return _ringFactory.createRing(pointsMap, callTrackerMap);
  }

  private boolean isAffinityRoutingEnabled(DegraderLoadBalancerStrategyConfig config) {
    return config.getHashMethod() != null && config.getHashMethod().equalsIgnoreCase(DegraderLoadBalancerStrategyV3.HASH_METHOD_URI_REGEX);
  }
}

/*
 * #%L
 * servo
 * %%
 * Copyright (C) 2011 Netflix
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * #L%
 */
package com.netflix.servo.publish;

import static com.netflix.servo.annotations.DataSourceType.*;

import com.google.common.collect.Lists;

import com.netflix.servo.BasicTagList;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.Metric;
import com.netflix.servo.MetricConfig;
import com.netflix.servo.MonitorRegistry;
import com.netflix.servo.TagList;

import com.netflix.servo.annotations.AnnotationUtils;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;

import com.netflix.servo.jmx.MonitoredAttribute;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Poller for fetching {@link com.netflix.servo.annotations.Monitor} metrics
 * from a monitor registry.
 */
public final class MonitorRegistryMetricPoller implements MetricPoller {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(MonitorRegistryMetricPoller.class);

    private final MonitorRegistry registry;

    /**
     * Creates a new instance using
     * {@link com.netflix.servo.DefaultMonitorRegistry}.
     */
    public MonitorRegistryMetricPoller() {
        this(DefaultMonitorRegistry.getInstance());
    }

    /**
     * Creates a new instance using the specified registry.
     *
     * @param registry  registry to query for annotated objects
     */
    public MonitorRegistryMetricPoller(MonitorRegistry registry) {
        this.registry = registry;
    }

    /**
     * Try to convert an object into a number. Boolean values will return 1 if
     * true and 0 if false. If the value is null or an unknown data type null
     * will be returned.
     */
    private Number asNumber(Object value) {
        Number num = null;
        if (value == null) {
            num = null;
        } else if (value instanceof Number) {
            num = (Number) value;
        } else if (value instanceof Boolean) {
            num = ((Boolean) value) ? 1 : 0;
        }
        return num;
    }

    private void getMetrics(
            List<Metric> metrics, MetricFilter filter, Object obj)
            throws Exception {
        String classId = AnnotationUtils.getMonitorId(obj);
        TagList classTags = AnnotationUtils.getMonitorTags(obj);
        LOGGER.debug("retrieving metrics from class {} id {}",
            obj.getClass().getCanonicalName(), classId);

        List<MonitoredAttribute> attrs =
            AnnotationUtils.getMonitoredAttributes(obj);

        for (MonitoredAttribute attr : attrs) {
            // Skip informational annotations
            Monitor anno = attr.annotation();
            TagList annoTags = BasicTagList.copyOf(anno.tags());
            TagList tags = BasicTagList.concat(classTags, annoTags);
            if (anno.type() == DataSourceType.INFORMATIONAL) {
                continue;
            }

            // Create config and add metric if filter matches 
            MetricConfig config = new MetricConfig(anno.name(), tags);
            if (filter.matches(config)) {
                Object value = attr.value();
                Number num = asNumber(value);
                if (num != null) {
                    long now = System.currentTimeMillis();
                    metrics.add(new Metric(config, now, num));
                } else {
                    LOGGER.debug("expected number but found {}, metric {}",
                        value, config);
                }
            }
        }
    }

    /** {@inheritDoc} */
    public List<Metric> poll(MetricFilter filter) {
        List<Metric> metrics = Lists.newArrayList();
        for (Object obj : registry.getRegisteredObjects()) {
            try {
                getMetrics(metrics, filter, obj);
            } catch (Exception e) {
                LOGGER.warn("failed to extract metrics from class {}", e,
                    obj.getClass().getCanonicalName());
            }
        }
        return metrics;
    }
}

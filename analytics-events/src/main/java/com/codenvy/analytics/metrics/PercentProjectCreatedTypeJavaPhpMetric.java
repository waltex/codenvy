/*
 *    Copyright (C) 2013 Codenvy.
 *
 */
package com.codenvy.analytics.metrics;

import java.io.IOException;

/**
 * @author <a href="mailto:abazko@codenvy.com">Anatoliy Bazko</a>
 */
public class PercentProjectCreatedTypeJavaPhpMetric extends ValueFromMapMetric {

    PercentProjectCreatedTypeJavaPhpMetric() throws IOException {
        super(MetricType.PERCENT_PROJECT_TYPE_PHP, MetricFactory.createMetric(MetricType.PROJECT_CREATED_TYPES), "PHP", ValueType.BOTH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTitle() {
        return "% PHP";
    }
}

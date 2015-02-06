/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.ibm.mqlight.api.impl;

import java.util.Map;

import com.ibm.mqlight.api.Delivery;
import com.ibm.mqlight.api.QOS;
import com.ibm.mqlight.api.StateException;
import com.ibm.mqlight.api.impl.engine.DeliveryRequest;

public abstract class DeliveryImpl implements Delivery {

    private final NonBlockingClientImpl client;
    private final QOS qos;
    private final String share;
    private final String topic;
    private final String topicPattern;
    private final long ttl;
    private final Map<String, Object> properties;
    private final DeliveryRequest deliveryRequest;
    private boolean confirmed = false;

    protected DeliveryImpl(NonBlockingClientImpl client, QOS qos, String share, String topic, String topicPattern, long ttl, Map<String, Object> properties, DeliveryRequest deliveryRequest) {
        this.client = client;
        this.qos = qos;
        this.share = share;
        this.topic = topic;
        this.topicPattern = topicPattern;
        this.ttl = ttl;
        this.properties = properties;
        this.deliveryRequest = deliveryRequest;
    }

    @Override
    public abstract Type getType();

    @Override
    public void confirm() {
        if (deliveryRequest == null) {
            throw new StateException("Subscription has autoConfirm option set to true");
        } else {
            if (confirmed) {
                throw new StateException("Delivery has already been confirmed");
            } else if (!client.doDelivery(deliveryRequest)) {
                throw new StateException("Cannot confirm delivery because of an interruption to the network connection to the MQ Light server");
            } else {
                confirmed = true;
            }
        }
    }

    @Override
    public QOS getQOS() {
        return qos;
    }

    @Override
    public String getShare() {
        return share;
    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public String getTopicPattern() {
        return topicPattern;
    }

    @Override
    public long getTtl() {
        return ttl;
    }

    @Override
    public Map<String, Object> getProperties() {
        return properties;
    }
}

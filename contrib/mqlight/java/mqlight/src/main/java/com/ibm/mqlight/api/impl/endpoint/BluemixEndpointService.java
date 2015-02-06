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
package com.ibm.mqlight.api.impl.endpoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.ibm.mqlight.api.ClientException;
import com.ibm.mqlight.api.endpoint.Endpoint;
import com.ibm.mqlight.api.endpoint.EndpointPromise;
import com.ibm.mqlight.api.impl.LogbackLogging;

/*
 * TODO
 { "mqlight": [
     { "name": "mqlsampleservice",
       "label": "mqlight",
       "plan": "default",
       "credentials":
         { "username": "jBruGnaTHuwq",
           "connectionLookupURI": "http://mqlightp-lookup.ng.bluemix.net/Lookup?serviceId=ServiceId_0000000090",
           "password": "xhUQve2gdgAN",
           "version": "2"
         }
      } ] }
 */
public class BluemixEndpointService extends EndpointServiceImpl {

    static {
        LogbackLogging.setup();
    }

    private static ThreadPoolExecutor executor;

    private static class State {
        String lookupUri;
        int retryCount;
        String user;
        String password;
        LinkedList<Endpoint> endpoints;
        int nextEndpointIndex;
    }
    private final State state = new State();

    private static class BluemixThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger number = new AtomicInteger();
        protected BluemixThreadFactory() {
            SecurityManager sm = System.getSecurityManager();
            group = sm == null ? Thread.currentThread().getThreadGroup() : sm.getThreadGroup();
        }
        @Override
        public Thread newThread(Runnable runnable) {
            Thread result = new Thread(group, runnable, "bluemix-endpoint-" + number.getAndIncrement());
            return result;
        }
    }

    protected String getVcapServices() {
        return System.getenv("VCAP_SERVICES");
    }

    protected String hitUri(String httpUri) throws IOException {
        URL url = new URL(httpUri);
        InputStream in = url.openStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte buffer[] = new byte[1024];
        while(true) {
            int amount = in.read(buffer);
            if (amount < 0) break;
            out.write(buffer, 0, amount);
        }
        return out.toString("UTF-8");
    }

    protected void doHttpLookup(final String httpUri, final EndpointPromise future) {

        synchronized(this) {
            if (executor == null) {
                // TODO: 5 threads == number pulled from thin air.
                executor = new ThreadPoolExecutor(5, 5, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new BluemixThreadFactory());
            }
        }

        executor.execute(new Runnable() {
            public void run() {
                try {
                    String serviceJson = hitUri(httpUri);

                    JsonParser parser = new JsonParser();
                    JsonObject root = parser.parse(serviceJson).getAsJsonObject();
                    JsonArray services = root.get("service").getAsJsonArray();
                    Endpoint endpoint = null;
                    synchronized(this) {
                        state.endpoints = new LinkedList<>();
                        for (JsonElement serviceElement : services) {
                            String uri = serviceElement.getAsString();
                            state.endpoints.add(new EndpointImpl(uri, state.user, state.password));
                        }

                        if (state.endpoints.isEmpty()) {
                            state.endpoints = null;
                            state.nextEndpointIndex = 0;
                        } else {
                            endpoint = state.endpoints.get(0);
                            state.nextEndpointIndex = 1;
                        }
                    }

                    if (endpoint == null) {
                        doRetry(future);
                    } else {
                        future.setSuccess(endpoint);
                    }
                } catch(IOException ioException) {  // TODO: should we capture this exception somewhere?
                    // Retry later...
                    doRetry(future);
                } catch(JsonParseException parseException) {
                    future.setFailure(new ClientException(
                            "Could not parse the JSON returned by the IBM MQ Light Bluemix lookup service.  See linked exception for more information", parseException));
                } catch(IllegalArgumentException iae) {
                    future.setFailure(new ClientException(
                            "Endpoint information returned by IBM MQ Light Bluemix lookup service was not valid.  See linked exception for more information", iae));
                }
            }
        });
    }

    protected void doRetry(EndpointPromise future) {
        int retry;
        synchronized(state) {
            retry = state.retryCount;
            ++state.retryCount;
        }
        future.setWait(calculateDelay(retry));
    }

    @Override
    public void lookup(EndpointPromise future) {
        try {
            String lookupUri = null;
            Endpoint endpoint = null;
            boolean retry = false;

            synchronized(state) {
                if (state.lookupUri == null) {
                    String vcapServices = getVcapServices();
                    if (vcapServices != null) {

                            JsonParser parser = new JsonParser();
                            JsonObject root = parser.parse(vcapServices).getAsJsonObject();
                            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                                if (entry.getKey().startsWith("mqlight")) {
                                    JsonObject mqlight = entry.getValue().getAsJsonArray().get(0).getAsJsonObject();
                                    JsonObject credentials = mqlight.get("credentials").getAsJsonObject();
                                    state.user = credentials.get("username").getAsString();
                                    state.lookupUri = credentials.get("connectionLookupURI").getAsString();
                                    state.password = credentials.get("password").getAsString();
                                    break;
                                }
                            }
                    }
                }

                lookupUri = state.lookupUri;
                if (state.lookupUri != null) {
                    if (state.endpoints == null) {
                        doHttpLookup(state.lookupUri, future);
                    } else if (state.nextEndpointIndex >= state.endpoints.size()) {
                        state.endpoints = null;
                        retry = true;
                    } else {
                        endpoint = state.endpoints.get(state.nextEndpointIndex++);
                    }
                }
            }

            if (lookupUri == null) {
                future.setFailure(new ClientException("Could not locate a valid IBM Bluemix VCAP_SERVICES environment variable. " +
                                                      "Check 'service' parameter to NonBlockingClient.create(...) method."));
            } else if (retry) {
                doRetry(future);
            } else if (endpoint != null) {
                future.setSuccess(endpoint);
            }
        } catch(JsonParseException e) {
            // Can't parse VCAP_SERVICES values
            future.setFailure(new ClientException(
                    "Could not parse the JSON present in the IBM Bluemix VCAP_SERVICES environment variable.  See linked exception for more information", e));
        }
    }

    @Override
    public void onSuccess(Endpoint endpoint) {
        synchronized(state) {
            int index = -1;
            if (state.endpoints != null) {
                index = state.endpoints.indexOf(endpoint);
            }

            if (index == 0) {
                state.nextEndpointIndex = 0;
            } else if (index > 0) {
                // Shuffle to front
                state.endpoints.remove(index);
                state.endpoints.addFirst(endpoint);
                state.nextEndpointIndex = 0;
            }
        }
    }

}

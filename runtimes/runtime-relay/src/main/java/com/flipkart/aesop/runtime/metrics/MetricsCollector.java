/*
 * Copyright 2012-2015, the original author or authors.
 *
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
 */
package com.flipkart.aesop.runtime.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.codehaus.jackson.map.ObjectMapper;

import com.flipkart.aesop.runtime.relay.DefaultRelay;
import com.linkedin.databus.core.monitoring.mbean.DbusEventsTotalStats;
import com.linkedin.databus2.core.container.monitoring.mbean.DbusHttpTotalStats;

/**
 * Periodically collects relay statistics for producers and consumers and serializes it into a JSON representation. 
 * @author kartikbu
 * @created 09/06/14
 */
public class MetricsCollector {

    /** Default refresh interval */
    private static final int DEFAULT_REFRESH_INTERVAL = 1;

    /** relay instance */
    private DefaultRelay relay;
    
    /** Scheduler for doing the encoding */
    private ScheduledExecutorService scheduledExecutorService;

    /** Object mapper */
    private ObjectMapper objectMapper = new ObjectMapper();

    /** JSON encoded registry */
    private String json = "";

    /** refresh interval */
    private int refreshInterval = DEFAULT_REFRESH_INTERVAL;

    /** Stats Collectors */
    private DbusHttpTotalStats httpTotalStats;
    private DbusEventsTotalStats inboundTotalStats;
    private DbusEventsTotalStats outboundTotalStats;

    /** scn trackers */
    private Map<String,Long> producerSCN = new HashMap<String,Long>();
    private Map<String,Long> clientSCN = new HashMap<String,Long>();

    /**
     * Constructor
     * @param relay     HTTP relay instance
     */
    public MetricsCollector(DefaultRelay relay) {

    	this.relay = relay;
        // stats collectors
        this.httpTotalStats = relay.getHttpStatisticsCollector().getTotalStats();
        this.inboundTotalStats = relay.getInboundEventStatisticsCollector().getTotalStats();
        this.outboundTotalStats = relay.getOutboundEventStatisticsCollector().getTotalStats();

        // schedule encoder thread
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(new Encoder(this), 0, DEFAULT_REFRESH_INTERVAL, TimeUnit.SECONDS);

    }

    /**
     * Update SCN for a client
     * @param client    Client as string
     * @param SCN       SCN requested by client
     */
    public void setClientSCN(String client, long SCN) {
        this.clientSCN.put(client,SCN);
    }

    /**
     * Update SCN generated by a producer
     * @param producer  Producer as string
     * @param SCN       SCN generated by producer
     */
    public void setProducerSCN(String producer, long SCN) {
        this.producerSCN.put(producer,SCN);
    }

    /**
     * Get generated JSON
     * @return generated JSON
     */
    public String getJson() {
        return json;
    }

    /**
     * Refresh interval, also used by controller
     * @return Refresh interval
     */
    public int getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * Encoder class which JSON encodes metrics data.
     */
    public class Encoder implements Runnable {

        /** Collector instance */
        private MetricsCollector collector;

        /**
         * Constructor
         * @param collector collector instance
         */
        public Encoder(MetricsCollector collector) {
            this.collector = collector;
        }

        /**
         * Interface method implementation.
         */
        @Override
        public void run() {
            Map<String,Object> map = new HashMap<String,Object>();
            map.put("producer",this.collector.producerSCN);
            // we want stats of only connected clients as known to the Relay
            Map<String,Long> connectedClientSCN = new HashMap<String,Long>();
            for (String client : relay.getPeers()) {
            	connectedClientSCN.put(client, this.collector.clientSCN.get(client));
            }
            map.put("client",connectedClientSCN);
            map.put("http",this.collector.httpTotalStats);
            map.put("inbound",this.collector.inboundTotalStats);
            map.put("outbound",this.collector.outboundTotalStats);
            try {
                this.collector.json = this.collector.objectMapper.writeValueAsString(map);
            } catch (Exception e) {
                this.collector.json = this.mapException(e);
            }
        }

        /**
         * In case of exceptions while encoding this method is used to send an alternate JSON.
         * @param e     Exception caught
         * @return      String representation of exception.
         */
        private String mapException(Exception e) {
            try {
                Map<String,String> map = new HashMap<String,String>();
                map.put("status", "exception");
                map.put("class", e.getClass().getName());
                map.put("message", e.getMessage());
                return objectMapper.writeValueAsString(map);
            } catch (Exception x) {
                return "";
            }
        }

    }


}

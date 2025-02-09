/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package com.amazon.dataprepper.plugins.prepper.peerforwarder.discovery;

import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.plugins.prepper.peerforwarder.PeerForwarderConfig;
import com.google.common.base.Preconditions;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.amazon.dataprepper.plugins.prepper.peerforwarder.discovery.DiscoveryUtils.validateEndpoint;

public class DnsPeerListProvider implements PeerListProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DnsPeerListProvider.class);
    private static final int MIN_TTL = 10;
    private static final int MAX_TTL = 20;

    private final DnsAddressEndpointGroup endpointGroup;

    public DnsPeerListProvider(final DnsAddressEndpointGroup endpointGroup, final PluginMetrics pluginMetrics) {
        Objects.requireNonNull(endpointGroup);

        this.endpointGroup = endpointGroup;

        try {
            endpointGroup.whenReady().get();
            LOG.info("Found endpoints: {}", String.join(",", endpointGroup.endpoints().toString()));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Caught exception while querying DNS", e);
        }

        pluginMetrics.gauge(PEER_ENDPOINTS, endpointGroup, group -> group.endpoints().size());
    }

    static DnsPeerListProvider createPeerListProvider(PluginSetting pluginSetting, PluginMetrics pluginMetrics) {
        final String domainName = pluginSetting.getStringOrDefault(PeerForwarderConfig.DOMAIN_NAME, null);
        Objects.requireNonNull(domainName, String.format("Missing '%s' configuration value",PeerForwarderConfig. DOMAIN_NAME));
        Preconditions.checkState(validateEndpoint(domainName), "Invalid domain name: %s", domainName);

        final DnsAddressEndpointGroup endpointGroup = DnsAddressEndpointGroup.builder(domainName)
                .ttl(MIN_TTL, MAX_TTL)
                .build();

        return new DnsPeerListProvider(endpointGroup, pluginMetrics);
    }

    @Override
    public List<String> getPeerList() {
        return endpointGroup.endpoints()
                .stream()
                .map(endpoint -> endpoint.ipAddr())
                .collect(Collectors.toList());
    }

    @Override
    public void addListener(final Consumer<? super List<Endpoint>> listener) {
        endpointGroup.addListener(listener);
    }

    @Override
    public void removeListener(final Consumer<?> listener) {
        endpointGroup.removeListener(listener);
    }
}

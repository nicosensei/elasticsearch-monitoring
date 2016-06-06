package com.github.nicosensei.elasticsearch.monitor;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

/**
 * Created by nicolas on 6/2/16.
 */
public class NodeStatsIndexerService extends AbstractLifecycleComponent<Boolean> {

    private final NodeStatsIndexer indexer;

    @Inject
    public NodeStatsIndexerService(
            final Settings settings,
            final Client client) {
        super(settings);
        this.indexer = new NodeStatsIndexer(client);
    }

    protected void doStart() {
        indexer.start();
    }

    protected void doStop() {
        indexer.stop();
    }

    protected void doClose() {}

}

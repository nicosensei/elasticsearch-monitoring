package com.github.nicosensei.elasticsearch.monitor;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by nicolas on 5/31/16.
 */
public final class NodeStatsIndexer implements Runnable {

    private static final ESLogger LOGGER = Loggers.getLogger(NodeStatsIndexer.class);

    private static final String INDEX_ALIAS = "es_monitor"; // TODO config parameter
    private static final String TYPE = "node_stats";
    private static final String UTF8 = "UTF-8";

    private static final String RESOURCE_SETTINGS = "/elasticsearch/_settings.json";
    private static final String RESOURCE_MAPPING = "/elasticsearch/node_stats.json";

    private static final String TIMESTAMP_PATTERN = "_yyyyMM";

    private final Client client;

    private ScheduledExecutorService executor;

    private boolean initialized = false;

    private final JSONStringParser jsonParser = new JSONStringParser();

    public NodeStatsIndexer(final Client client) {
        this.client = client;
    }

    public final void start() {
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this, 1L, 1L, TimeUnit.MINUTES); // TODO config parameter
        LOGGER.info("Started executor service");
    }

    public final void stop() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(1, TimeUnit.MINUTES);
            } catch (final InterruptedException e) {
                // NOP
            }
            LOGGER.info("Stopped executor service");
        }
    }

    public void run() {
        try {
            if (!initialized) {
                checkCreateIndex();
            }
            final NodesStatsResponse stats = client.admin().cluster().prepareNodesStats(INDEX_ALIAS)
                    .setNodesIds("*")
                    .setBreaker(false)  // TODO config parameters
                    .setFs(true)
                    .setHttp(true)
                    .setIndices(true)
                    .setJvm(true)
                    .setOs(true)
                    .setProcess(true)
                    .setScript(false)
                    .setThreadPool(true)
                    .setTransport(true)
                    .get();

            final BulkRequestBuilder bulk = client.prepareBulk();
            final Map<String, NodeStats> nsMap = stats.getNodesMap();
            for (String nodeId : nsMap.keySet()) {
                final NodeStats ns = nsMap.get(nodeId);
                XContentBuilder builder = XContentFactory.jsonBuilder();
                builder.startObject();
                ns.toXContent(builder, ToXContent.EMPTY_PARAMS);
                builder.endObject();
                builder.close();
                final Map<String, Object> sourceMap = jsonParser.parseJSON(builder.string());
                sourceMap.put("nodeId", nodeId);
                bulk.add(client.prepareIndex(INDEX_ALIAS, TYPE).setSource(sourceMap));
            }

            if (bulk.numberOfActions() > 0) {
                final BulkResponse resp = bulk.get();
                for (BulkItemResponse item : resp.getItems()) {
                    if (item.isFailed()) {
                        LOGGER.warn("Failed indexation: " + item.getFailureMessage());
                    }
                }
            }
        } catch (final Throwable t) {
            LOGGER.error("Error raised in indexer", t);
        }
    }

    private final void checkCreateIndex() {
        final String currentIndexName = INDEX_ALIAS
                + new SimpleDateFormat(TIMESTAMP_PATTERN).format(new Date(System.currentTimeMillis()));

        final IndicesExistsResponse exists = client.admin().indices().prepareExists(currentIndexName).get();
        if (!exists.isExists()) {
            try {
                final CreateIndexResponse resp = client.admin().indices().prepareCreate(currentIndexName)

                        .setSettings(readTextFile(RESOURCE_SETTINGS, UTF8).toString()).get();
                if (!resp.isAcknowledged()) {
                    throw new ElasticsearchException(
                            "Create monitor index with name " + currentIndexName + " ack=false");

                }
            } catch (final IOException e) {
                throw new ElasticsearchException("Failed to create monitor index with name " + currentIndexName);
            }
            LOGGER.info("Created index " + currentIndexName);
            try {
                final PutMappingResponse resp = client.admin().indices().preparePutMapping(currentIndexName)
                        .setType(TYPE)
                        .setSource(readTextFile(RESOURCE_MAPPING, UTF8).toString()).get();
                if (!resp.isAcknowledged()) {
                    throw new ElasticsearchException(
                            "Create mapping in index " + currentIndexName + " ack=false");
                }
            } catch (final IOException e) {
                throw new ElasticsearchException("Failed to create mapping in index " + currentIndexName);
            }
            LOGGER.info("Added mapping to index " + currentIndexName);
            final IndicesAliasesResponse resp =
                    client.admin().indices().prepareAliases().addAlias(currentIndexName, INDEX_ALIAS).get();
            if (!resp.isAcknowledged()) {
                throw new ElasticsearchException(
                        "Alias index " + currentIndexName + " as " + INDEX_ALIAS + " ack=false");
            }
            LOGGER.info("Aliased index " + currentIndexName + " as " + INDEX_ALIAS);
        }
    }

    private final StringBuilder readTextFile(final String classpathResourcePath, final String encoding)
            throws IOException {
        final StringBuilder sb = new StringBuilder(1000);
        BufferedReader br = new BufferedReader(new InputStreamReader(
                NodeStatsIndexer.class.getResourceAsStream(classpathResourcePath),
                encoding
        ));
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line + "\n");
        }
        br.close();
        return sb;
    }
}

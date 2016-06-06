package com.github.nicosensei.elasticsearch.monitor;

import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.plugins.Plugin;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by nicolas on 6/2/16.
 */
public class NodeStatsIndexerPlugin extends Plugin {

    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "Plugin that periodically stores node stats for monitoring in Kibana or any external app.";
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        final ArrayList<Class<? extends LifecycleComponent>> services =  new ArrayList(1);
        services.add(NodeStatsIndexerService.class);
        return services;
    }

}

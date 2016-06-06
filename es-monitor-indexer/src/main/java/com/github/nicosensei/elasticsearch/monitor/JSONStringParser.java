/**
 *
 */
package com.github.nicosensei.elasticsearch.monitor;

import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * @author nicolas
 *
 */
public class JSONStringParser {

    private final ContainerFactory containerFactory;

    private final JSONParser parser = new JSONParser();

    public JSONStringParser() {
        containerFactory = new ContainerFactory() {

            @Override
            public Map<String, Object> createObjectContainer() {
                return new LinkedHashMap<String, Object>(3);
            }

            @Override
            public List<Object> creatArrayContainer() {
                return new ArrayList<Object>(2);
            }

        };
    }

    public final Map<String, Object> parseJSON(final String jsonString)
            throws IOException {
        try {
            return (Map<String, Object>) parser.parse(new StringReader(jsonString), containerFactory);
        } catch (final ParseException e) {
            throw new IOException(e);
        }
    }

}
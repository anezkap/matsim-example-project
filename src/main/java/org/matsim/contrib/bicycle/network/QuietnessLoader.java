package org.matsim.contrib.bicycle.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class QuietnessLoader {

    public static Map<Long, Integer> loadQuietnessMap(String geojsonFile) throws IOException {
        Map<Long, Integer> quietnessMap = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(geojsonFile));

        for (JsonNode feature : root.get("features")) {
            JsonNode properties = feature.get("properties");
            if (properties.has("id") && properties.has("quietness")) {
                long id = properties.get("id").asLong();
                int quietness = properties.get("quietness").asInt();
                quietnessMap.put(id, quietness);
            }
        }
        return quietnessMap;
    }
}
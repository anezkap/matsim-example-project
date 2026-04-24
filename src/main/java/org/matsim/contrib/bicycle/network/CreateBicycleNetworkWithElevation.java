package org.matsim.contrib.bicycle.network;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.osm.networkReader.OsmBicycleReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class CreateBicycleNetworkWithElevation {

    private static final String outputCRS = "EPSG:31370"; // Belgian Lambert 72
    private static final String tiffFileCRS = "EPSG:31370"; // Belgian Lambert 72
//    private static final String inputOsmFile = "inputs_network/merged-network_pbf.osm.pbf";
//    private static final String inputTiffFile = "inputs_network/terrain.tif";
//    private static final String outputFile = "outputs_network/network_with_cars_bikes_elevations.xml.gz";

//    private static final String inputOsmFile = "../../../../Downloads/belgium_merged_network.osm.pbf";
//    private static final String inputTiffFile = "../../../../Downloads/DTM_RBC_50cm.tif";
//    private static final String outputFile = "../../../../Downloads/belgium_merged_network.xml.gz";
//    private static final String quietnessFile = "../../../../Downloads/Bruxelles_Cyclability_Data.geojson";

//    private static final String inputOsmFile = "../../../../Downloads/testing_brussels.osm.pbf";
//    private static final String inputTiffFile = "../../../../Downloads/DTM_RBC_50cm_fixed.tif";
//    private static final String outputFile = "../../../../Downloads/testing_brussels_network.xml.gz";
//    private static final String quietnessFile = "../../../../Downloads/Bruxelles_Cyclability_Data.geojson";

    private static final String inputOsmFile = "../../../../Downloads/brussels_network.osm.pbf";
    private static final String inputTiffFile = "../../../../Downloads/DTM_RBC_50cm.tif";
    private static final String outputFile = "../../../../Downloads/brussels_only_network.xml.gz";
    private static final String quietnessFile = "../../../../Downloads/Bruxelles_Cyclability_Data.geojson";

    public static void main(String[] args) throws IOException {

        var elevationParser = new ElevationDataParser(inputTiffFile, outputCRS, tiffFileCRS);

        // The OSM data are usually in WGS84
        var transformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, outputCRS);

        // Pre-load quietness map once
        Map<Long, Integer> quietnessMap = QuietnessLoader.loadQuietnessMap(quietnessFile);

        var network = new OsmBicycleReader.Builder()
                .setCoordinateTransformation(transformation)
                .setAfterLinkCreated((link, tags, direction) -> {
                    addElevationIfNecessary(link.getFromNode(), elevationParser);
                    addElevationIfNecessary(link.getToNode(), elevationParser);
                    addQuietness(link, quietnessMap);
                })
                .build()
                .read(inputOsmFile);

        NetworkUtils.cleanNetwork(network, Set.of(TransportMode.car, TransportMode.bike));

        new NetworkWriter(network).write(outputFile);
    }

    private static synchronized void addElevationIfNecessary(Node node, ElevationDataParser elevationParser) {
        if (node.getCoord().hasZ()) return; // already has elevation, skip

        Coord coord = node.getCoord();

        try {
            double elevation = elevationParser.getElevation(coord);

            // Clamp invalid/null values (TIFF uses -999 for no-data)
            if (Double.isNaN(elevation) || elevation < -500) {
                elevation = 0;
            }

            node.setCoord(CoordUtils.createCoord(coord.getX(), coord.getY(), elevation));

        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            // Node is outside the TIFF bounds — default to elevation 0
            node.setCoord(CoordUtils.createCoord(coord.getX(), coord.getY(), 0.0));
        }
    }

    private static void addQuietness(Link link, Map<Long, Integer> quietnessMap) {
        Object origIdObj = link.getAttributes().getAttribute("origid");
        if (origIdObj != null) {
            long origId = ((Number) origIdObj).longValue();
            Integer quietness = quietnessMap.get(origId);
            if (quietness != null) {
                link.getAttributes().putAttribute("quietness", quietness);
            }
        }
    }
}
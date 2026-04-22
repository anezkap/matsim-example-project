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
//    private static final String inputTiffFile = "inputs_network/terrain.tif";
//    private static final String outputFile = "../../../../Downloads/belgium_merged_network.xml.gz";
//    private static final String quietnessFile = "../../../../Downloads/Bruxelles_Cyclability_Data.geojson";

    private static final String inputOsmFile = "../../../../Downloads/testing_brussels.osm.pbf";
    private static final String inputTiffFile = "inputs_network/terrain.tif";
    private static final String outputFile = "../../../../Downloads/testing_brussels_network.xml.gz";
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
                    // Pass the transformation into the method so we can ensure the coord is in Lambert 72
                    addElevationIfNecessary(link.getFromNode(), elevationParser, transformation);
                    addElevationIfNecessary(link.getToNode(), elevationParser, transformation);
                    addQuietness(link, quietnessMap);
                })
                .build()
                .read(inputOsmFile);

        NetworkUtils.cleanNetwork(network, Set.of(TransportMode.car, TransportMode.bike));

        new NetworkWriter(network).write(outputFile);
    }

    private static synchronized void addElevationIfNecessary(Node node, ElevationDataParser elevationParser, CoordinateTransformation transformation) {
        Coord coord = node.getCoord();

        if (coord.getX() < 1000) {
            coord = transformation.transform(coord);
        }

        // Inside your lambda or addElevation method
        try {
            // Only call getElevation if we are reasonably sure it's inside the TIFF bounds
            double elevation = elevationParser.getElevation(coord);

            if (elevation < -500) {  // used -999 for null values
                elevation = 0;
            }

            // Set elevation only if it's a valid number
            if (!Double.isNaN(elevation)) {
                node.setCoord(CoordUtils.createCoord(coord.getX(), coord.getY(), elevation));
            }
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            // If the node is outside the TIFF (e.g. index -31985),
            // just leave it at Z=0.0 and move on.
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
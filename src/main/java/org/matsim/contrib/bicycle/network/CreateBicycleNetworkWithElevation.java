package org.matsim.contrib.bicycle.network;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.osm.networkReader.OsmBicycleReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.Set;

public class CreateBicycleNetworkWithElevation {

    private static final String outputCRS = "EPSG:31370"; // Belgian Lambert 72
    private static final String inputOsmFile = "inputs_network/allroads_brussels.osm.pbf";
    private static final String inputTiffFile = "inputs_network/terrain.tif";
    private static final String outputFile = "outputs_network/network_with_cars_bikes_elevations.xml.gz";

    public static void main(String[] args) {

        var elevationParser = new ElevationDataParser(inputTiffFile, outputCRS);

        var transformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, outputCRS);

        var network = new OsmBicycleReader.Builder()
                .setCoordinateTransformation(transformation)
                .setAfterLinkCreated((link, tags, direction) -> {
                    addElevationIfNecessary(link.getFromNode(), elevationParser);
                    addElevationIfNecessary(link.getToNode(), elevationParser);
                })
                .build()
                .read(inputOsmFile);

        NetworkUtils.cleanNetwork(network, Set.of(TransportMode.car, TransportMode.bike));

        new NetworkWriter(network).write(outputFile);
    }

    private static synchronized void addElevationIfNecessary(Node node, ElevationDataParser elevationParser) {
        Coord coord = node.getCoord();

        // Add the elevation to all coordinates and set it to 0.0 if it's outside the TIFF bounds or if the elevation is NaN.
        try {
            // Only call getElevation if we are reasonably sure it's inside the TIFF bounds
            double elevation = elevationParser.getElevation(coord);

            // Set elevation only if it's a valid number
            if (!Double.isNaN(elevation)) {
                node.setCoord(CoordUtils.createCoord(coord.getX(), coord.getY(), elevation));
            }
            else {
                node.setCoord(CoordUtils.createCoord(coord.getX(), coord.getY(), 0.0));
            }
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            // If the node is outside the TIFF (e.g. index -31985),
            // just leave it at Z=0.0 and move on.
            node.setCoord(CoordUtils.createCoord(coord.getX(), coord.getY(), 0.0));
        }
    }
}
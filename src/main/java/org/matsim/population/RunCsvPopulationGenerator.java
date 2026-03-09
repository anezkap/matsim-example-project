package org.matsim.population;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class RunCsvPopulationGenerator {

    public static void main(String[] args) throws IOException {
        Path csv = Path.of("src/main/java/org/matsim/population/input/combined_population.csv");
        Path out = Path.of("src/main/java/org/matsim/population/output/population.xml.gz");

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        createPopulationFromCsv(scenario, csv);

        new PopulationWriter(scenario.getPopulation(), scenario.getNetwork()).write(out.toString());
    }

    private static void createPopulationFromCsv(Scenario scenario, Path csvFile) throws IOException {
        Population population = scenario.getPopulation();
        PopulationFactory pf = population.getFactory();

        try (BufferedReader br = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IllegalArgumentException("CSV is empty: " + csvFile);

            String[] header = splitCsvLine(headerLine);
            Map<String, Integer> idx = indexByName(header);

            String line;
            long personCounter = 0;

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] c = splitCsvLine(line);
                personCounter++;

                String personId = "p" + personCounter;

                Person person = pf.createPerson(Id.create(personId, Person.class));
                population.addPerson(person);

                // ---- person attributes ----
                person.getAttributes().putAttribute("sex", getRequired(c, idx, "sex"));
                person.getAttributes().putAttribute("age", getRequired(c, idx, "age"));
                person.getAttributes().putAttribute("education", getRequired(c, idx, "education"));
                person.getAttributes().putAttribute("median_income", parseNullableDouble(getRequired(c, idx, "median_income")));
                person.getAttributes().putAttribute("has_car", parseBoolean(getRequired(c, idx, "has_car")));
                person.getAttributes().putAttribute("carAvail", parseBoolean(getRequired(c, idx, "has_car")) ? "always" : "never");
                person.getAttributes().putAttribute("brussels_resident", parseBoolean(getRequired(c, idx, "brussels_resident")));

                // ---- locations (EPSG:31370, same as network) ----
                Coord home = new Coord(
                        parseDouble(getRequired(c, idx, "home_x")),
                        parseDouble(getRequired(c, idx, "home_y"))
                );
                Coord work = new Coord(
                        parseDouble(getRequired(c, idx, "work_x")),
                        parseDouble(getRequired(c, idx, "work_y"))
                );

                // ---- times: in seconds since midnight already  ----
                double depWork_s = parseSecondsSinceMidnight(getRequired(c, idx, "departure_time_home"));
                double depHome_s = parseSecondsSinceMidnight(getRequired(c, idx, "departure_time_work"));

                // ---- mode (car/bike/walk/public transport) ----
                String mode = normalizeMode(getRequired(c, idx, "matsim_mode"));

                // ---- build plan: home -> work -> home ----
                Plan plan = pf.createPlan();
                person.addPlan(plan);

                Activity homeAct1 = pf.createActivityFromCoord("home", home);
                homeAct1.setEndTime(depWork_s);
                plan.addActivity(homeAct1);

                Leg leg1 = pf.createLeg(mode);
                plan.addLeg(leg1);

                Activity workAct = pf.createActivityFromCoord("work", work);
                workAct.setEndTime(depHome_s);
                plan.addActivity(workAct);

                Leg leg2 = pf.createLeg(mode);
                plan.addLeg(leg2);

                Activity homeAct2 = pf.createActivityFromCoord("home", home);
                plan.addActivity(homeAct2);
            }
        }
    }

    // -------- helpers --------

    private static double parseSecondsSinceMidnight(String raw) {
        return Double.parseDouble(raw.trim());
    }

    private static String normalizeMode(String raw) {
        String m = raw.trim().toLowerCase();

        // Accept a few likely variants from CSV
        return switch (m) {
            case "car" -> TransportMode.car;
            case "bike", "bicycle", "e-bike" -> TransportMode.bike;
            case "walk", "walking" -> TransportMode.walk;
            case "pt", "publictransport", "public transport", "public_transport", "transit" -> TransportMode.pt;
            default -> throw new IllegalArgumentException("Unsupported mode value: " + raw
                    + " (expected car/bike/walk/public transport)");
        };
    }

    private static String[] splitCsvLine(String line) {
        // Minimal CSV splitter: OK only if there are no quoted commas in fields.
        return line.split("\\s*,\\s*", -1);
    }

    private static Map<String, Integer> indexByName(String[] header) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            idx.put(header[i].trim().toLowerCase(), i);
        }
        return idx;
    }

    private static String getRequired(String[] c, Map<String, Integer> idx, String name) {
        Integer i = idx.get(name.toLowerCase());
        if (i == null) throw new IllegalArgumentException("Missing required column: " + name);
        if (i >= c.length) throw new IllegalArgumentException("Row has too few columns for: " + name);
        return c[i];
    }

    private static Double parseNullableDouble(String s) {
        String value = s.trim();
        return value.isEmpty() ? 0 : Double.parseDouble(value);
    }

    private static double parseDouble(String s) {
        return Double.parseDouble(s.trim());
    }

    private static boolean parseBoolean(String s) {
        String v = s.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("y");
    }
}
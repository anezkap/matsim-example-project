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
import java.util.ArrayList;
import java.util.List;

public class RunCsvPopulationGenerator {

    public static void main(String[] args) throws IOException {
//        Path csv = Path.of("src/main/java/org/matsim/population/input/combined_population.csv");
//        Path csv = Path.of("src/main/java/org/matsim/population/input/combined_population_active.csv");
//        Path csv = Path.of("src/main/java/org/matsim/population/input/combined_population_active_company_car.csv");
//        Path csv = Path.of("src/main/java/org/matsim/population/input/combined_population_active_1903.csv");
        Path csv = Path.of("src/main/java/org/matsim/population/input/all_active_workers_final.csv");

        //        Path out = Path.of("src/main/java/org/matsim/population/output/population.xml.gz");
//        Path out = Path.of("src/main/java/org/matsim/population/output/population_active.xml.gz");
//        Path out = Path.of("src/main/java/org/matsim/population/output/population_active_company_car.xml.gz");
        Path out = Path.of("src/main/java/org/matsim/population/output/all_active_workers_final_0904.xml.gz");

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
                person.getAttributes().putAttribute("industry", getRequired(c, idx, "industry"));
                person.getAttributes().putAttribute("home_municipality", getRequired(c, idx, "home_municipality"));
                person.getAttributes().putAttribute("median_income", parseNullableDouble(getRequired(c, idx, "median_income")));
                person.getAttributes().putAttribute("has_car", parseBoolean(getRequired(c, idx, "has_car")));
                person.getAttributes().putAttribute("carAvail", parseBoolean(getRequired(c, idx, "has_car")) ? "always" : "never");
                person.getAttributes().putAttribute("lives_in_brussels", getRequired(c, idx, "lives_in_brussels"));
                person.getAttributes().putAttribute("works_in_brussels", getRequired(c, idx, "works_in_brussels"));
                person.getAttributes().putAttribute("subpopulation", getRequired(c, idx, "subpopulation"));

                // ---- locations (EPSG:31370, same as network) ----
                Coord home = new Coord(
                        parseDouble(getRequired(c, idx, "home_x")),
                        parseDouble(getRequired(c, idx, "home_y"))
                );
                Coord work = new Coord(
                        parseDouble(getRequired(c, idx, "work_x")),
                        parseDouble(getRequired(c, idx, "work_y"))
                );

                // ---- times: in minutes since midnight already  ----
                double depHome_s = parseMinutesSinceMidnight(getRequired(c, idx, "departure_home_to_work"));
                double depWork_s = parseMinutesSinceMidnight(getRequired(c, idx, "departure_from_work"));

                // ---- mode (car/bike/walk/public transport) ----
                String mode = normalizeMode(getRequired(c, idx, "assigned_mode"));

                // ---- build plan: home -> work -> home ----
                Plan plan = pf.createPlan();
                person.addPlan(plan);

                Activity homeAct1 = pf.createActivityFromCoord("home", home);
                homeAct1.setEndTime(depHome_s);
                plan.addActivity(homeAct1);

                Leg leg1 = pf.createLeg(mode);
                plan.addLeg(leg1);

                Activity workAct = pf.createActivityFromCoord("work", work);
                workAct.setEndTime(depWork_s);
                plan.addActivity(workAct);

                Leg leg2 = pf.createLeg(mode);
                plan.addLeg(leg2);

                Activity homeAct2 = pf.createActivityFromCoord("home", home);
                plan.addActivity(homeAct2);
            }

//            Add my wife as an agent
            String personId = "KRISTINA";

            Person person = pf.createPerson(Id.create(personId, Person.class));
            population.addPerson(person);

            // ---- person attributes ----
            person.getAttributes().putAttribute("sex", "F");
            person.getAttributes().putAttribute("age", "15-19");
            person.getAttributes().putAttribute("education", "Lower educated");
            person.getAttributes().putAttribute("median_income", "22031");
            person.getAttributes().putAttribute("has_car", false);
            person.getAttributes().putAttribute("carAvail", "never");
            person.getAttributes().putAttribute("brussels_resident", true);
            person.getAttributes().putAttribute("subpopulation", "short_distance");

            // ---- locations (EPSG:31370, same as network) ----
            Coord home = new Coord(
                    150390,
                    171424
            );
            Coord work = new Coord(
                    150042,
                    170140
            );

            // ---- times: in seconds since midnight already  ----
            double depHome_s = 8.5*3600;
            double depWork_s = 17*3600;

            // ---- mode (car/bike/walk/public transport) ----
            String mode = "bike";

            // ---- build plan: home -> work -> home ----
            Plan plan = pf.createPlan();
            person.addPlan(plan);

            Activity homeAct1 = pf.createActivityFromCoord("home", home);
            homeAct1.setEndTime(depHome_s);
            plan.addActivity(homeAct1);

            Leg leg1 = pf.createLeg(mode);
            plan.addLeg(leg1);

            Activity workAct = pf.createActivityFromCoord("work", work);
            workAct.setEndTime(depWork_s);
            plan.addActivity(workAct);

            Leg leg2 = pf.createLeg(mode);
            plan.addLeg(leg2);

            Activity homeAct2 = pf.createActivityFromCoord("home", home);
            plan.addActivity(homeAct2);
        }
    }

    // -------- helpers --------

    private static double parseMinutesSinceMidnight(String raw) {
        return Double.parseDouble(raw.trim()) * 60;
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

//    private static String[] splitCsvLine(String line) {
//        // Minimal CSV splitter: OK only if there are no quoted commas in fields.
//        return line.split("\\s*,\\s*", -1);
//    }

    private static String[] splitCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        tokens.add(current.toString().trim());
        return tokens.toArray(new String[0]);
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
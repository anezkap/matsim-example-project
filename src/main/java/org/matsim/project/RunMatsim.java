package org.matsim.project;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.bicycle.AdditionalBicycleLinkScore;
import org.matsim.contrib.bicycle.AdditionalBicycleLinkScoreDefaultImpl;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.BicycleModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.VehiclesFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author nagel
 *
 */
public class RunMatsim {

	public static void main(String[] args) {
		Options options = Options.parse(args);

		Config config = ConfigUtils.loadConfig(options.configPath);

		config.controller().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

		if (options.outputDirectory != null) {
			config.controller().setOutputDirectory(options.outputDirectory);
		}
		if (options.runId != null) {
			config.controller().setRunId(options.runId);
		}
		if (options.iterations >= 0) {
			config.controller().setLastIteration(options.iterations);
		}

		if (options.yamlPath != null) {
			applyYamlScoringOverrides(config, options.yamlPath);
		}

		config.routing().setRoutingRandomness(3.);
		config.qsim().setPcuThresholdForFlowCapacityEasing(0.25);

		BicycleConfigGroup bicycleConfigGroup = ConfigUtils.addOrGetModule(config, BicycleConfigGroup.class);
		final String bicycle = bicycleConfigGroup.getBicycleMode();

		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.getConfig().qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

		final VehiclesFactory vf = VehicleUtils.getFactory();
		scenario.getVehicles().addVehicleType(vf.createVehicleType(Id.create(TransportMode.car, VehicleType.class)));
		scenario.getVehicles().addVehicleType(
				vf.createVehicleType(Id.create(bicycle, VehicleType.class))
						.setNetworkMode(bicycle)
						.setMaximumVelocity(5)
						.setPcuEquivalents(0.05)
						.setLength(1.5));

		Controler controler = new Controler(scenario);

		controler.addOverridingModule(new BicycleModule());
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				this.bind(AdditionalBicycleLinkScoreDefaultImpl.class);
				this.bind(AdditionalBicycleLinkScore.class).to(MyAdditionalBicycleLinkScore.class);
			}
		});

		controler.run();
	}

	private static void applyYamlScoringOverrides(Config config, String yamlPath) {
		try (InputStream input = new FileInputStream(yamlPath)) {
			Object loaded = new Yaml().load(input);

			if (!(loaded instanceof Map<?, ?> root)) {
				return;
			}

			Object scoringObj = root.get("scoring");
			if (!(scoringObj instanceof Map<?, ?> scoringMap)) {
				return;
			}

			Object scoringParametersObj = scoringMap.get("scoringParameters");
			if (!(scoringParametersObj instanceof List<?> scoringParametersList) || scoringParametersList.isEmpty()) {
				return;
			}

			Object firstScoringParametersObj = scoringParametersList.get(0);
			if (!(firstScoringParametersObj instanceof Map<?, ?> firstScoringParametersMap)) {
				return;
			}

			Object modeParamsObj = firstScoringParametersMap.get("modeParams");
			if (!(modeParamsObj instanceof List<?> modeParamsList)) {
				return;
			}

			for (Object entryObj : modeParamsList) {
				if (!(entryObj instanceof Map<?, ?> entryMap)) {
					continue;
				}

				Object modeObj = entryMap.get("mode");
				Object constantObj = entryMap.get("constant");
				if (!(modeObj instanceof String mode) || constantObj == null) {
					continue;
				}

				double constant = Double.parseDouble(constantObj.toString());
				config.scoring().getOrCreateModeParams(mode).setConstant(constant);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to apply YAML scoring overrides from " + yamlPath, e);
		}
	}

	private static class Options {
		private String configPath = "scenarios/brussels/config.xml";
		private String yamlPath = null;
		private String outputDirectory = null;
		private String runId = null;
		private int iterations = -1;

		static Options parse(String[] args) {
			Options options = new Options();

			if (args == null) {
				return options;
			}

			for (int i = 0; i < args.length; i++) {
				String arg = args[i];

				if ("run".equals(arg)) {
					continue;
				}

				switch (arg) {
					case "--config" -> {
						if (i + 1 < args.length) {
							options.configPath = args[++i];
						}
					}
					case "--yaml" -> {
						if (i + 1 < args.length) {
							options.yamlPath = args[++i];
						}
					}
					case "--output" -> {
						if (i + 1 < args.length) {
							options.outputDirectory = args[++i];
						}
					}
					case "--runId" -> {
						if (i + 1 < args.length) {
							options.runId = args[++i];
						}
					}
					case "--iterations" -> {
						if (i + 1 < args.length) {
							options.iterations = Integer.parseInt(args[++i]);
						}
					}
					default -> {
						if (arg.startsWith("--config:controler.outputDirectory") && i + 1 < args.length) {
							options.outputDirectory = args[++i];
						} else if (arg.startsWith("--config:controler.runId") && i + 1 < args.length) {
							options.runId = args[++i];
						} else if (arg.startsWith("--config:controler.lastIteration") && i + 1 < args.length) {
							options.iterations = Integer.parseInt(args[++i]);
						}
					}
				}
			}

			return options;
		}
	}

	private static class MyAdditionalBicycleLinkScore implements AdditionalBicycleLinkScore {

		@Inject
		private AdditionalBicycleLinkScoreDefaultImpl delegate;

		@Override
		public double computeLinkBasedScore(Link link, Id<Vehicle> vehicleId, String bicycleMode) {
			double distance = link.getLength();

			double bikingAllowancePerKm = 0.37;
			double bikingAllowance = (distance / 1000.0) * bikingAllowancePerKm;

			double amount = delegate.computeLinkBasedScore(link, vehicleId, bicycleMode);

			return amount + bikingAllowance;
		}
	}
}
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

import java.util.ArrayList;
import java.util.List;

/**
 * @author nagel
 *
 */
public class RunMatsim {

	public static void main(String[] args) {
		Options options = Options.parse(args);

		Config config = ConfigUtils.loadConfig(options.configPath);

		// If you have a YAML file with overrides, apply it here if your MATSim version supports it.
		// Otherwise it is ignored safely.
		if (options.yamlPath != null) {
			config = ConfigUtils.loadConfig(options.configPath);
		}

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

	private static class Options {
		private String configPath = "scenarios/brussels/config.xml";
		private String yamlPath = null;
		private String outputDirectory = null;
		private String runId = null;
		private int iterations = -1;

		static Options parse(String[] args) {
			Options options = new Options();
			List<String> remaining = new ArrayList<>();

			if (args != null) {
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
						default -> remaining.add(arg);
					}
				}
			}

			// Support MATSim-style overrides like:
			// --config:controller.outputDirectory ...
			// --config:controller.lastIteration ...
			for (int i = 0; i < remaining.size(); i++) {
				String arg = remaining.get(i);
				if (arg.startsWith("--config:controller.outputDirectory") && i + 1 < remaining.size()) {
					options.outputDirectory = remaining.get(++i);
				} else if (arg.startsWith("--config:controller.runId") && i + 1 < remaining.size()) {
					options.runId = remaining.get(++i);
				} else if (arg.startsWith("--config:controller.lastIteration") && i + 1 < remaining.size()) {
					options.iterations = Integer.parseInt(remaining.get(++i));
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
			double linkLength = link.getLength();

			double bikingAllowancePerKm = 0.37;
			double bikingAllowance = (linkLength / 1000.0) * bikingAllowancePerKm;

			double amount = delegate.computeLinkBasedScore(link, vehicleId, bicycleMode);

			return amount + bikingAllowance;
		}
	}
}
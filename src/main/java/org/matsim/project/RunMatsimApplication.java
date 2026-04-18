package org.matsim.project;

import com.google.inject.Inject;
import org.apache.logging.log4j.core.tools.picocli.CommandLine;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.bicycle.AdditionalBicycleLinkScore;
import org.matsim.contrib.bicycle.AdditionalBicycleLinkScoreDefaultImpl;
import org.matsim.contrib.bicycle.BicycleModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.VehiclesFactory;

/**
 * @author nagel
 *
 */
@CommandLine.Command(header = ":: MyScenario ::", version = "1.0")
public class RunMatsimApplication extends MATSimApplication {

	public RunMatsimApplication() {
		super("scenarios/brussels/config.xml");
	}

	public static void main(String[] args) {
		MATSimApplication.run(RunMatsimApplication.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {

		config.controller().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

		// possibly modify config here
		config.routing().setRoutingRandomness(3.);
		config.qsim().setPcuThresholdForFlowCapacityEasing(0.25);

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

		final String bicycle = "bike";

		// set config such that the mode vehicles come from vehicles data:
		scenario.getConfig().qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

		// now put the mode vehicles into the vehicles data:
		final VehiclesFactory vf = VehicleUtils.getFactory();
		scenario.getVehicles().addVehicleType(vf.createVehicleType(Id.create(TransportMode.car, VehicleType.class)));
		scenario.getVehicles().addVehicleType(
				vf.createVehicleType(Id.create(bicycle, VehicleType.class))
						.setNetworkMode(bicycle)
						.setMaximumVelocity(5)
						.setPcuEquivalents(0.05)
						.setLength(1.5));
	}

	@Override
	protected void prepareControler(Controler controler) {

		controler.addOverridingModule(new BicycleModule());
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				this.bind(AdditionalBicycleLinkScoreDefaultImpl.class);
				this.bind(AdditionalBicycleLinkScore.class).to(MyAdditionalBicycleLinkScore.class);
			}
		});
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
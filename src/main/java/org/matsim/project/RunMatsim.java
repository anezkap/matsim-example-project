/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
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
import org.matsim.contrib.bicycle.run.RunBicycleExample;
import org.matsim.contrib.otfvis.OTFVisLiveModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.VehiclesFactory;

/**
 * @author nagel
 *
 */
public class RunMatsim{

	public static void main(String[] args) {

		Config config;
		if ( args==null || args.length==0 || args[0]==null ){
			config = ConfigUtils.loadConfig( "scenarios/brussels/config.xml" );
		} else {
			config = ConfigUtils.loadConfig( args );
		}

		config.controller().setOverwriteFileSetting( OverwriteFileSetting.deleteDirectoryIfExists );

		// possibly modify config here

		config.routing().setRoutingRandomness(3.);

		config.qsim().setPcuThresholdForFlowCapacityEasing(0.5);

		BicycleConfigGroup bicycleConfigGroup = ConfigUtils.addOrGetModule( config, BicycleConfigGroup.class );

		final String bicycle = bicycleConfigGroup.getBicycleMode();
		
		Scenario scenario = ScenarioUtils.loadScenario(config) ;

		// possibly modify scenario here

		// set config such that the mode vehicles come from vehicles data:
		scenario.getConfig().qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

		// now put the mode vehicles into the vehicles data:
		final VehiclesFactory vf = VehicleUtils.getFactory();
		scenario.getVehicles().addVehicleType( vf.createVehicleType(Id.create(TransportMode.car, VehicleType.class ) ) );
		scenario.getVehicles().addVehicleType( vf.createVehicleType(Id.create("bike", VehicleType.class ) ).setMaximumVelocity(4.16666666 ).setPcuEquivalents(0.05 ).setLength(1.5) );

		Controler controler = new Controler( scenario ) ;
		
		// possibly modify controler here

		controler.addOverridingModule(new BicycleModule() );
//		controler.addOverridingModule( new AbstractModule(){
//			@Override public void install(){
//				this.bind( AdditionalBicycleLinkScoreDefaultImpl.class ); // so it can be used as delegate
//				this.bind( AdditionalBicycleLinkScore.class ).to( MyAdditionalBicycleLinkScore.class );
//			}
//		} );

//		controler.addOverridingModule( new OTFVisLiveModule() ) ;

//		controler.addOverridingModule( new SimWrapperModule() );
		
		// ---
		
		controler.run();
	}

	private static class MyAdditionalBicycleLinkScore implements AdditionalBicycleLinkScore {

		@Inject
		private AdditionalBicycleLinkScoreDefaultImpl delegate;

		@Override public double computeLinkBasedScore( Link link ){
			double link_length = (double) link.getAttributes().getAttribute( "length" );

//			double biking_allowance_per_km = 0.37;
//			double biking_allowance_per_km = 0.0;

//			change from m to km and multiply by the biking allowance constant
//			double biking_allowance = (link_length / 1000) * biking_allowance_per_km;

			double amount = delegate.computeLinkBasedScore( link );

//			return amount + biking_allowance;  // or some other way to augment the score

			return amount;

		}
	}
}

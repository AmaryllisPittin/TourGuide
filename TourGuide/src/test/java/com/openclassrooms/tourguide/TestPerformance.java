package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class TestPerformance {

	/*
	 * A note on performance improvements:
	 * 
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 * 
	 * InternalTestHelper.setInternalUserNumber(100000);
	 * 
	 * 
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 * 
	 * These are performance metrics that we are trying to hit:
	 * 
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

	@Test
	public void highVolumeTrackLocation() throws InterruptedException {
		System.out.println("Démarrage du test de localisation...");

		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		// Users should be incremented up to 100,000, and test finishes within 15
		// minutes
		InternalTestHelper.setInternalUserNumber(100000);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		tourGuideService.tracker.stopTracking();

		System.out.println("Utilisateurs initialisés: " + tourGuideService.getAllUsers().size());

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		tourGuideService.trackAllUsersLocations();
		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		double secondes = stopWatch.getTime() / 1000.0;

		System.out.printf("Duree : %.3f secondes%n ", secondes);
		System.out.printf("Durée moyenne par utilisateur: %.3f ms%n", 
			(double) stopWatch.getTime() / 100000); 

		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	@Test
	public void highVolumeGetRewards() throws Exception {

		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Users should be incremented up to 100,000, and test finishes within 20
		// minutes
		InternalTestHelper.setInternalUserNumber(100000);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		tourGuideService.tracker.stopTracking();

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();

		allUsers.forEach(user -> 
			user.addToVisitedLocations(
				new VisitedLocation(
					user.getUserId(), 
					attraction, 
					new Date()
				)
			)
		);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		ExecutorService executor = Executors.newFixedThreadPool(64);

		try {
			List<Future<?>> futures = new ArrayList<>();

			for (User user : allUsers) {
				futures.add(executor.submit(
					() -> rewardsService.calculateRewards(user)
				));
			}

			for (Future<?> future : futures) {
				future.get();
			}
		} finally {
			executor.shutdown();
		}

		stopWatch.stop();

		for (User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}

		System.out.printf("Utilisateurs traites: %d%n", allUsers.size());
		System.out.printf("Duree: %.2f secondes%n", stopWatch.getTime() / 1000.0);

		double secondes = stopWatch.getTime() / 1000.0;
		double estimationMinutes = secondes * (100000.0 / allUsers.size()) / 60.0;
		System.out.printf("Estimation pour 100 000 utilisateurs : %.2f minute%n", estimationMinutes);
	
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));

	}
}
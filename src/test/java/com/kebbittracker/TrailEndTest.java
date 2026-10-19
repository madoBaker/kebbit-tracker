package com.kebbittracker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

// Feldip weasel trails recorded in-game with a ring of pursuit (one set of tiles per segment)
public class TrailEndTest
{
	private static final WorldPoint WEST_BURROW = new WorldPoint(2525, 2889, 0);

	@Test
	public void trailEndingAtFarBush()
	{
		assertEquals(new WorldPoint(2553, 2889, 0), KebbitTrackerPlugin.findTrailEnd(farBushTrail(), WEST_BURROW));
	}

	@Test
	public void trailPassingBesideBurrow()
	{
		assertEquals(new WorldPoint(2532, 2886, 0), KebbitTrackerPlugin.findTrailEnd(loopingTrail(), WEST_BURROW));
	}

	private static Set<WorldPoint> tiles(int... xy)
	{
		final Set<WorldPoint> set = new HashSet<>();
		for (int i = 0; i < xy.length; i += 2)
		{
			set.add(new WorldPoint(xy[i], xy[i + 1], 0));
		}
		return set;
	}

	private static List<Set<WorldPoint>> farBushTrail()
	{
		return Arrays.asList(
			tiles(2518, 2882, 2518, 2883, 2518, 2884, 2518, 2885, 2518, 2886, 2518, 2887, 2519, 2882, 2519, 2887, 2519, 2888, 2519, 2889, 2520, 2880, 2520, 2881, 2520, 2882, 2520, 2889, 2520, 2890, 2521, 2880, 2521, 2890, 2521, 2891, 2522, 2880, 2522, 2891, 2523, 2880, 2523, 2891, 2524, 2880, 2524, 2881, 2524, 2882, 2524, 2890, 2524, 2891),
			tiles(2525, 2883, 2526, 2883, 2527, 2881, 2527, 2882, 2527, 2883, 2528, 2881, 2529, 2880, 2529, 2881, 2530, 2880, 2531, 2880, 2532, 2880, 2533, 2880),
			tiles(2534, 2880, 2535, 2880, 2536, 2880, 2536, 2881, 2537, 2881, 2538, 2881, 2539, 2881, 2540, 2880, 2540, 2881, 2541, 2880, 2542, 2880),
			tiles(2543, 2880, 2544, 2880, 2545, 2880, 2546, 2880, 2547, 2880, 2547, 2881, 2548, 2881, 2549, 2881, 2550, 2881, 2550, 2882, 2551, 2882, 2552, 2882),
			tiles(2553, 2881, 2553, 2889, 2554, 2880, 2554, 2881, 2554, 2889, 2554, 2890, 2555, 2880, 2555, 2890, 2556, 2880, 2556, 2890, 2557, 2880, 2557, 2886, 2557, 2887, 2557, 2888, 2557, 2889, 2557, 2890, 2558, 2880, 2558, 2881, 2558, 2884, 2558, 2885, 2558, 2886, 2559, 2881, 2559, 2882, 2559, 2883, 2559, 2884)
		);
	}

	private static List<Set<WorldPoint>> loopingTrail()
	{
		return Arrays.asList(
			tiles(2518, 2882, 2518, 2883, 2518, 2884, 2518, 2885, 2518, 2886, 2518, 2887, 2519, 2882, 2519, 2887, 2519, 2888, 2519, 2889, 2520, 2880, 2520, 2881, 2520, 2882, 2520, 2889, 2520, 2890, 2521, 2880, 2521, 2890, 2521, 2891, 2522, 2880, 2522, 2891, 2523, 2880, 2523, 2891, 2524, 2880, 2524, 2881, 2524, 2882, 2524, 2890, 2524, 2891),
			tiles(2524, 2883, 2524, 2884, 2524, 2885, 2524, 2889, 2525, 2885, 2525, 2886, 2525, 2887, 2525, 2888),
			tiles(2525, 2890, 2526, 2890, 2526, 2891, 2527, 2891, 2528, 2891, 2529, 2891, 2530, 2891),
			tiles(2529, 2886, 2529, 2887, 2529, 2888, 2529, 2889, 2530, 2886, 2530, 2889, 2530, 2890, 2531, 2886, 2532, 2886)
		);
	}
}

package com.kebbittracker;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

/**
 * Tracks Hunter creatures caught with a noose wand: Feldip weasels, polar, common and razor-backed
 * kebbits, and desert devils. Herbiboars are left to RuneLite's built-in Herbiboar plugin.
 *
 * Their trails are made of one multiloc per footprint tile, whose impostor switches to a
 * "visible"/"fade" track model (driven by varbits) once that part of the trail is revealed. The ring
 * of pursuit reveals every tile at once, so instead of relying on reveal order we walk the revealed
 * tiles outward from the burrow the player inspected and take the tile furthest along the trail as
 * its end. The hiding spot (bush) next to that tile is highlighted.
 */
@Slf4j
@PluginDescriptor(
	name = "Kebbit Tracker",
	description = "Highlights the hiding spot at the end of a hunter track",
	tags = {"hunter", "tracking", "kebbit", "weasel", "desert devil", "noose", "trail", "burrow", "pursuit"}
)
public class KebbitTrackerPlugin extends Plugin
{
	// Models a footprint tile takes on once it has been revealed
	private static final Set<Integer> SHOWN_TRACK_IDS = ImmutableSet.of(
		ObjectID.HUNTING_TRAIL_VISIBLE,
		ObjectID.HUNTING_TRAIL_VISIBLE_CORNER,
		ObjectID.HUNTING_TRAIL_FADE180_CORNER,
		ObjectID.HUNTING_TRAIL_VISIBLE180,
		ObjectID.HUNTING_TRAIL_FADE,
		ObjectID.HUNTING_TRAIL_FADE180,
		ObjectID.HUNTING_TRAIL_FADE_DARK,
		ObjectID.HUNTING_TRAIL_VISIBLE180_CORNER,
		ObjectID.HUNTING_TRAIL_FADE_CORNER,
		ObjectID.HUNTING_TRAIL_FADE_CORNER_DARK,
		ObjectID.HUNTING_TRAIL_VISIBLE_POLAR,
		ObjectID.HUNTING_TRAIL_VISIBLE_POLAR180,
		ObjectID.HUNTING_TRAIL_FADE_POLAR,
		ObjectID.HUNTING_TRAIL_FADE_POLAR180,
		ObjectID.HUNTING_TRAIL_VISIBLE_POLARCORNER,
		ObjectID.HUNTING_TRAIL_VISIBLE180_POLARCORNER,
		ObjectID.HUNTING_TRAIL_FADE_POLARCORNER,
		ObjectID.HUNTING_TRAIL_FADE180_POLARCORNER,
		ObjectID.HUNTING_TRAIL_VISIBLE_DARK,
		ObjectID.HUNTING_TRAIL_VISIBLE180_DARK,
		ObjectID.HUNTING_TRAIL_FADE180_DARK,
		ObjectID.HUNTING_TRAIL_VISIBLE_CORNER_DARK,
		ObjectID.HUNTING_TRAIL_VISIBLE180_CORNER_DARK,
		ObjectID.HUNTING_TRAIL_FADE180_CORNER_DARK
	);

	// Hiding spots: bushes, disturbed sand and snow drifts
	private static final Set<Integer> END_SPOT_IDS = ImmutableSet.of(
		ObjectID.HUNTING_TRAIL_END_JUNGLE,
		ObjectID.HUNTING_TRAIL_END_BUSH29,
		ObjectID.HUNTING_TRAIL_END_BUSH35_55,
		ObjectID.HUNTING_TRAIL_END_DESERT,
		ObjectID.HUNTING_TRAIL_END_POLAR
	);

	// Dens trails start from: burrows and holes
	private static final Set<Integer> BURROW_IDS = ImmutableSet.of(
		ObjectID.HUNTING_TRAIL_SPAWN1,
		ObjectID.HUNTING_TRAIL_SPAWN2,
		ObjectID.HUNTING_TRAIL_SPAWN3,
		ObjectID.HUNTING_TRAIL_SPAWN4,
		ObjectID.HUNTING_TRAIL_SPAWN5,
		ObjectID.HUNTING_TRAIL_SPAWN6,
		ObjectID.HUNTING_TRAIL_SPAWN_JUNGLE1,
		ObjectID.HUNTING_TRAIL_SPAWN_JUNGLE2,
		ObjectID.HUNTING_TRAIL_SPAWN_DESERT1,
		ObjectID.HUNTING_TRAIL_SPAWN_DESERT2,
		ObjectID.HUNTING_TRAIL_SPAWN_POLAR1,
		ObjectID.HUNTING_TRAIL_SPAWN_POLAR2
	);

	// Connectors (plants, cacti, rockslides, hollow logs, tunnels) inspected to reveal the next segment
	private static final int FIRST_CONNECTOR_ID = ObjectID.HUNTING_TRAIL_CLUE3_0;
	private static final int LAST_CONNECTOR_ID = ObjectID.HUNTING_TRAIL_CLUE8_8;

	// Footprint tiles this close (in tiles) are treated as connected; small gaps occur around plants
	private static final int MAX_STEP = 2;

	// Above this many revealed segments, fall back to a greedy chain instead of an exhaustive search
	private static final int MAX_CHAIN_SEGMENTS = 12;

	// How far a bush may be from the end of the trail
	private static final int MAX_END_SPOT_DISTANCE = 3;

	// How far the player may be from the burrow they inspected
	private static final int MAX_BURROW_DISTANCE = 10;

	private static final Set<Integer> NOOSE_WAND_IDS = ImmutableSet.of(
		ItemID.NOOSE_WAND,
		ItemID.NOOSE_WAND_WOOD,
		ItemID.NOOSE_WAND_POLAR,
		ItemID.NOOSE_WAND_DESERT,
		ItemID.NOOSE_WAND_JUNGLE,
		ItemID.NOOSE_WAND_RAZORBACK
	);

	private static final int RING_MAX_CHARGES = 10;
	private static final Pattern RING_CHARGES_PATTERN = Pattern.compile("ring of pursuit (?:has|reveals the entire trail to you\\. It has) (\\d+) charges? left");
	private static final String RING_CRUMBLE_MESSAGE = "ring of pursuit reveals the entire trail to you. <col=ff0000>It then crumbles to dust";

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KebbitTrackerOverlay overlay;

	@Inject
	private KebbitTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	private PursuitRingInfoBox ringInfoBox;
	private boolean wearingRing;

	@Getter
	private boolean nooseEquipped;

	private final Set<TileObject> tracks = new HashSet<>();
	private final Set<TileObject> endSpots = new HashSet<>();
	private final Set<TileObject> midSpots = new HashSet<>();
	private final Set<TileObject> burrows = new HashSet<>();

	private Set<WorldPoint> shownTiles = new HashSet<>();
	private WorldPoint trailStart;

	@Getter
	private WorldPoint trailEnd;

	@Getter
	private TileObject target;

	@Provides
	KebbitTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KebbitTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		clientThread.invokeLater(() ->
		{
			final ItemContainer worn = client.getItemContainer(InventoryID.WORN);
			wearingRing = isWearingRing(worn);
			nooseEquipped = isWieldingNoose(worn);
			updateRingInfoBox();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		removeRingInfoBox();
		wearingRing = false;
		clearObjects();
		reset();
	}

	/**
	 * Charges left on the ring of pursuit. They belong to the player rather than the ring, so they are
	 * stored per account; null until the game has told us via a chat message.
	 */
	Integer getRingCharges()
	{
		return configManager.getRSProfileConfiguration(KebbitTrackerConfig.GROUP, KebbitTrackerConfig.RING_CHARGES_KEY, Integer.class);
	}

	private void setRingCharges(int charges)
	{
		configManager.setRSProfileConfiguration(KebbitTrackerConfig.GROUP, KebbitTrackerConfig.RING_CHARGES_KEY, charges);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		final String message = event.getMessage();
		if (message.contains(RING_CRUMBLE_MESSAGE))
		{
			// The next ring worn starts on full charges
			setRingCharges(RING_MAX_CHARGES);
			return;
		}

		final Matcher matcher = RING_CHARGES_PATTERN.matcher(message);
		if (matcher.find())
		{
			setRingCharges(Integer.parseInt(matcher.group(1)));
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.WORN)
		{
			return;
		}

		wearingRing = isWearingRing(event.getItemContainer());
		nooseEquipped = isWieldingNoose(event.getItemContainer());
		updateRingInfoBox();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (KebbitTrackerConfig.GROUP.equals(event.getGroup()) && "showRingCharges".equals(event.getKey()))
		{
			clientThread.invokeLater(this::updateRingInfoBox);
		}
	}

	private static boolean isWearingRing(ItemContainer worn)
	{
		if (worn == null)
		{
			return false;
		}

		final Item ring = worn.getItem(EquipmentInventorySlot.RING.getSlotIdx());
		return ring != null && ring.getId() == ItemID.RING_OF_PURSUIT;
	}

	private static boolean isWieldingNoose(ItemContainer worn)
	{
		if (worn == null)
		{
			return false;
		}

		final Item weapon = worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon != null && NOOSE_WAND_IDS.contains(weapon.getId());
	}

	private void updateRingInfoBox()
	{
		if (!wearingRing || !config.showRingCharges())
		{
			removeRingInfoBox();
			return;
		}

		if (ringInfoBox == null)
		{
			ringInfoBox = new PursuitRingInfoBox(itemManager.getImage(ItemID.RING_OF_PURSUIT), this);
			infoBoxManager.addInfoBox(ringInfoBox);
		}
	}

	private void removeRingInfoBox()
	{
		if (ringInfoBox != null)
		{
			infoBoxManager.removeInfoBox(ringInfoBox);
			ringInfoBox = null;
		}
	}

	private void clearObjects()
	{
		tracks.clear();
		endSpots.clear();
		midSpots.clear();
		burrows.clear();
	}

	private void reset()
	{
		shownTiles = new HashSet<>();
		trailStart = null;
		trailEnd = null;
		target = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			// The scene is rebuilt and every object will spawn again
			clearObjects();
			reset();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		onObjectSpawned(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		onObjectDespawned(event.getGameObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		onObjectSpawned(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		onObjectDespawned(event.getGroundObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		onObjectSpawned(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		onObjectDespawned(event.getDecorativeObject());
	}

	private void onObjectSpawned(TileObject object)
	{
		final int id = object.getId();
		if (END_SPOT_IDS.contains(id))
		{
			endSpots.add(object);
			return;
		}
		if (BURROW_IDS.contains(id))
		{
			burrows.add(object);
			return;
		}
		if (id >= FIRST_CONNECTOR_ID && id <= LAST_CONNECTOR_ID)
		{
			midSpots.add(object);
			return;
		}

		final ObjectComposition def = client.getObjectDefinition(id);
		if (def == null)
		{
			return;
		}

		if (isTrack(def))
		{
			tracks.add(object);
		}
	}

	private void onObjectDespawned(TileObject object)
	{
		tracks.remove(object);
		endSpots.remove(object);
		midSpots.remove(object);
		burrows.remove(object);
		if (object == target)
		{
			target = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Each segment of a trail (spot to spot) is driven by its own varbit
		final Map<Integer, Set<WorldPoint>> segments = new HashMap<>();
		final Set<WorldPoint> shown = new HashSet<>();
		for (TileObject track : tracks)
		{
			final ObjectComposition def = client.getObjectDefinition(track.getId());
			if (isShown(def))
			{
				final WorldPoint loc = track.getWorldLocation();
				segments.computeIfAbsent(def.getVarbitId(), k -> new HashSet<>()).add(loc);
				shown.add(loc);
			}
		}

		if (shown.isEmpty())
		{
			reset();
			return;
		}

		if (shown.equals(shownTiles) && target != null)
		{
			return;
		}

		if (shownTiles.isEmpty() || trailStart == null)
		{
			// A new trail was just revealed; the player is standing at the burrow it starts from
			trailStart = findStart();
		}
		shownTiles = shown;

		trailEnd = findTrailEnd(new ArrayList<>(segments.values()), trailStart);
		target = findSpotAt(trailEnd);
		log.debug("Trail start {} segments {} end {} -> target {}", trailStart, segments.size(), trailEnd,
			target == null ? null : target.getWorldLocation());
	}

	private WorldPoint findStart()
	{
		final Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		final WorldPoint playerLoc = player.getWorldLocation();
		final TileObject burrow = nearest(burrows, playerLoc, MAX_BURROW_DISTANCE);
		return burrow != null ? burrow.getWorldLocation() : playerLoc;
	}

	/**
	 * Footprints of different segments can pass right next to each other (and the burrow), so walking
	 * tile by tile takes shortcuts. Instead each segment is reduced to its two ends, and we find the
	 * order and direction of segments that chains them end to end from the start with the smallest
	 * total gap. The exit of the last segment in that chain is the end of the trail.
	 */
	static WorldPoint findTrailEnd(List<Set<WorldPoint>> segments, WorldPoint start)
	{
		final int n = segments.size();
		final WorldPoint[][] ends = new WorldPoint[n][];
		for (int i = 0; i < n; i++)
		{
			ends[i] = segmentEnds(segments.get(i));
		}

		if (start == null)
		{
			start = ends[0][0];
		}

		if (n > MAX_CHAIN_SEGMENTS)
		{
			return greedyChainEnd(ends, start);
		}

		final ChainSolver solver = new ChainSolver(ends);
		double bestCost = Double.MAX_VALUE;
		WorldPoint bestEnd = null;
		for (int j = 0; j < n; j++)
		{
			for (int dir = 0; dir < 2; dir++)
			{
				final double cost = distance(start, ends[j][dir]) + solver.cost(1 << j, j, dir);
				if (cost < bestCost)
				{
					bestCost = cost;
					bestEnd = solver.end(1 << j, j, dir);
				}
			}
		}
		return bestEnd;
	}

	/**
	 * Memoised search over (segments used, last segment, direction it was walked) giving the cheapest
	 * way to chain the remaining segments and the trail end that results.
	 */
	private static final class ChainSolver
	{
		private final WorldPoint[][] ends;
		private final int full;
		private final double[][][] cost;
		private final WorldPoint[][][] end;

		ChainSolver(WorldPoint[][] ends)
		{
			this.ends = ends;
			final int n = ends.length;
			full = (1 << n) - 1;
			cost = new double[1 << n][n][2];
			end = new WorldPoint[1 << n][n][2];
		}

		// dir 0 enters at ends[i][0] and exits at ends[i][1]; dir 1 is the reverse
		double cost(int mask, int last, int dir)
		{
			solve(mask, last, dir);
			return cost[mask][last][dir];
		}

		WorldPoint end(int mask, int last, int dir)
		{
			solve(mask, last, dir);
			return end[mask][last][dir];
		}

		private void solve(int mask, int last, int dir)
		{
			if (end[mask][last][dir] != null)
			{
				return;
			}

			final WorldPoint exit = ends[last][1 - dir];
			if (mask == full)
			{
				cost[mask][last][dir] = 0;
				end[mask][last][dir] = exit;
				return;
			}

			double best = Double.MAX_VALUE;
			WorldPoint bestEnd = exit;
			for (int j = 0; j < ends.length; j++)
			{
				if ((mask & (1 << j)) != 0)
				{
					continue;
				}
				for (int d = 0; d < 2; d++)
				{
					final double c = distance(exit, ends[j][d]) + cost(mask | (1 << j), j, d);
					if (c < best)
					{
						best = c;
						bestEnd = end(mask | (1 << j), j, d);
					}
				}
			}
			cost[mask][last][dir] = best;
			end[mask][last][dir] = bestEnd;
		}
	}

	// Fallback for unexpectedly many segments: repeatedly hop to the segment end closest to where we are
	private static WorldPoint greedyChainEnd(WorldPoint[][] ends, WorldPoint start)
	{
		final boolean[] used = new boolean[ends.length];
		WorldPoint pos = start;
		for (int step = 0; step < ends.length; step++)
		{
			int bestSeg = -1;
			int bestDir = 0;
			double best = Double.MAX_VALUE;
			for (int j = 0; j < ends.length; j++)
			{
				for (int d = 0; d < 2 && !used[j]; d++)
				{
					final double c = distance(pos, ends[j][d]);
					if (c < best)
					{
						best = c;
						bestSeg = j;
						bestDir = d;
					}
				}
			}
			used[bestSeg] = true;
			pos = ends[bestSeg][1 - bestDir];
		}
		return pos;
	}

	/**
	 * The two ends of a segment: the tile furthest (by walking along the footprints) from an arbitrary
	 * tile, and the tile furthest from that one.
	 */
	private static WorldPoint[] segmentEnds(Set<WorldPoint> tiles)
	{
		final List<WorldPoint> nodes = new ArrayList<>(tiles);
		final int a = furthestAlong(nodes, 0);
		final int b = furthestAlong(nodes, a);
		return new WorldPoint[]{nodes.get(a), nodes.get(b)};
	}

	// Dijkstra over the tiles of one segment; segments are short so O(n^2) is fine
	private static int furthestAlong(List<WorldPoint> nodes, int from)
	{
		final int n = nodes.size();
		final double[] dist = new double[n];
		final boolean[] done = new boolean[n];
		Arrays.fill(dist, Double.MAX_VALUE);
		dist[from] = 0;

		int furthest = from;
		for (int iter = 0; iter < n; iter++)
		{
			int u = -1;
			for (int i = 0; i < n; i++)
			{
				if (!done[i] && dist[i] != Double.MAX_VALUE && (u == -1 || dist[i] < dist[u]))
				{
					u = i;
				}
			}
			if (u == -1)
			{
				break;
			}
			done[u] = true;
			if (dist[u] > dist[furthest])
			{
				furthest = u;
			}

			final WorldPoint a = nodes.get(u);
			for (int v = 0; v < n; v++)
			{
				final WorldPoint b = nodes.get(v);
				if (done[v] || Math.abs(a.getX() - b.getX()) > MAX_STEP || Math.abs(a.getY() - b.getY()) > MAX_STEP)
				{
					continue;
				}
				final double alt = dist[u] + distance(a, b);
				if (alt < dist[v])
				{
					dist[v] = alt;
				}
			}
		}
		return furthest;
	}

	private static double distance(WorldPoint a, WorldPoint b)
	{
		final int dx = a.getX() - b.getX();
		final int dy = a.getY() - b.getY();
		return Math.sqrt(dx * dx + dy * dy);
	}

	/**
	 * Picks the hiding spot at the end of the trail. With a ring of pursuit that is where the animal
	 * is hiding; otherwise it is the next hiding spot or connector to search.
	 */
	private TileObject findSpotAt(WorldPoint end)
	{
		if (end == null)
		{
			return null;
		}

		final TileObject bush = nearest(endSpots, end, MAX_END_SPOT_DISTANCE);
		final TileObject plant = nearest(midSpots, end, MAX_END_SPOT_DISTANCE);
		if (bush == null || plant == null)
		{
			return bush != null ? bush : plant;
		}
		return distance(bush.getWorldLocation(), end) <= distance(plant.getWorldLocation(), end) ? bush : plant;
	}

	private static TileObject nearest(Set<TileObject> objects, WorldPoint to, int maxDistance)
	{
		TileObject best = null;
		double bestDistance = Double.MAX_VALUE;
		for (TileObject object : objects)
		{
			final WorldPoint loc = object.getWorldLocation();
			if (loc.getPlane() != to.getPlane())
			{
				continue;
			}
			final double distance = distance(loc, to);
			if (distance <= maxDistance && distance < bestDistance)
			{
				best = object;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static boolean isShown(ObjectComposition def)
	{
		if (def.getImpostorIds() == null)
		{
			return SHOWN_TRACK_IDS.contains(def.getId());
		}

		final ObjectComposition impostor = def.getImpostor();
		return impostor != null && SHOWN_TRACK_IDS.contains(impostor.getId());
	}

	private static boolean isTrack(ObjectComposition def)
	{
		final int[] impostorIds = def.getImpostorIds();
		if (impostorIds == null)
		{
			return false;
		}

		for (int id : impostorIds)
		{
			if (SHOWN_TRACK_IDS.contains(id))
			{
				return true;
			}
		}
		return false;
	}
}

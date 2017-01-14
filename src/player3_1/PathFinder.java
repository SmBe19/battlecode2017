package player3_1;

import battlecode.common.*;

import java.util.*;

/**
 * Path Finder
 */
public class PathFinder {

	private static class PqEntry implements Comparable<PqEntry>{
		final float dist;
		final MapLocation location;

		public PqEntry(float dist, MapLocation location) {
			this.dist = dist;
			this.location = location;
		}

		@Override
		public int compareTo(PqEntry o) {
			if(dist == o.dist){
				return location.compareTo(o.location);
			}
			return (int) Math.signum(dist - o.dist);
		}
	}

	private static class DistEntry {
		final MapLocation location;
		float dist;
		DistEntry parent;
		boolean done = false;

		public DistEntry(MapLocation location, float dist, DistEntry parent) {
			this.location = location;
			this.dist = dist;
			this.parent = parent;
		}
	}

	private float stride, bodyRadius, strideSquared, distStartDestSquared;
	private float meshSize = Consts.PATHFINDER_MESH_SIZE;
	private MapLocation start, dest;
	private boolean useRobots;
	private PriorityQueue<PqEntry> pq;
	private Map<MapLocation, DistEntry> dist;
	private List<BodyInfo> obstacles;
	private DistEntry root;

	private boolean calculating = false;
	private boolean ready = false;
	private boolean foundDest = false;

	private int oldByteCount = 0;
	private int oldRoundNum = 0;

	public void init() {
		stride = RobotPlayer.rc.getType().strideRadius;
		bodyRadius = RobotPlayer.rc.getType().bodyRadius;
		strideSquared = stride * stride;
		pq = new PriorityQueue<>();
		dist = new HashMap<>();
		obstacles = new ArrayList<>();
	}

	private static void addToRes(List<MapLocation> res, DistEntry element) {
		if(element != null){
			addToRes(res, element.parent);
			res.add(element.location);
		}
	}

	private static float round(float val){
		return Math.round(val * Consts.PATHFINDER_ROUND) / Consts.PATHFINDER_ROUND;
	}

	public void reset(){
		calculating = false;
		ready = false;
		foundDest = false;
	}

	public void findPath(MapLocation exactStart, MapLocation exactDest, boolean useRobots) throws GameActionException {
		calculating = true;
		ready = false;
		foundDest = false;
		pq.clear();
		dist.clear();
		obstacles.clear();

		start = exactStart;
		dest = new MapLocation(
				Math.min(RobotPlayer.mapPos[1], Math.max(RobotPlayer.mapPos[3], round(exactDest.x))),
				Math.min(RobotPlayer.mapPos[0], Math.max(RobotPlayer.mapPos[2], round(exactDest.y))));
		this.useRobots = useRobots;
		distStartDestSquared = start.distanceSquaredTo(dest);

		// clean trees and robots
		for (TreeInfo tree : RobotPlayer.trees) {
			if (start.distanceSquaredTo(tree.location) <= distStartDestSquared) {
				obstacles.add(tree);
			}
		}
		if(useRobots) {
			for (RobotInfo robot : RobotPlayer.robots) {
				if (start.distanceSquaredTo(robot.location) <= distStartDestSquared) {
					obstacles.add(robot);
				}
			}
		}

		meshSize = distStartDestSquared > Consts.PATHFINDER_DISTSQ_TOO_LARGE ? Consts.PATHFINDER_MESH_SIZE_LARGE : Consts.PATHFINDER_MESH_SIZE;

		root = new DistEntry(start, 0, null);
		dist.put(start, root);
		pq.add(new PqEntry(0, start));
	}

	public void update() throws GameActionException {
		if (!calculating) {
			return;
		}
//		oldRoundNum = RobotPlayer.rc.getRoundNum();
		while (!pq.isEmpty() && Clock.getBytecodesLeft() > Consts.PATHFINDER_BYTECODE_PER_CYCLE) {
			PqEntry aepq = pq.poll();
			MapLocation aepqlocation = aepq.location;
			DistEntry aedist = dist.get(aepqlocation);
			if(aedist.done || aedist.location.distanceSquaredTo(start) > distStartDestSquared * Consts.PATHFINDER_MAX_DISTSQ_MULT){
				continue;
			}
			aedist.done = true;

			// System.out.println("aepq " + aepqlocation + " " + aepq.dist);
//			RobotPlayer.rc.setIndicatorDot(aepqlocation, 256, 0, 0);
//			RobotPlayer.rc.setIndicatorDot(dest, 256, 128, 128);

			if(dest.distanceSquaredTo(aepqlocation) <= strideSquared){
				dist.put(dest, new DistEntry(dest, aepq.dist + dest.distanceTo(aepqlocation), aedist));
				ready = true;
				foundDest = true;
				calculating = false;
				return;
			}

			float startx = Math.max(RobotPlayer.mapPos[3], aepqlocation.x - stride);
			float starty = Math.max(RobotPlayer.mapPos[2], aepqlocation.y - stride);
			float endx = Math.min(RobotPlayer.mapPos[1], aepqlocation.x + stride);
			float endy = Math.min(RobotPlayer.mapPos[0], aepqlocation.y + stride);
			// System.out.println("start / end: " + startx + " " + starty + " " + endx + " " + endy);
			for(float x = startx; x <= endx; x += meshSize) {
				yloop:
				for(float y = starty; y <= endy; y += meshSize) {
					MapLocation newLoc = new MapLocation(round(x), round(y));
					float distToNewLoc = aepqlocation.distanceTo(newLoc);
					if(distToNewLoc <= stride){
						for (BodyInfo body : obstacles) {
							if(newLoc.distanceTo(body.getLocation()) <= bodyRadius + body.getRadius()){
//								RobotPlayer.rc.setIndicatorDot(body.getLocation(), 0, 256, 0);
								continue yloop;
							}
						}
						float newDist = aedist.dist + distToNewLoc;
						DistEntry distEntry = dist.get(newLoc);
						if (distEntry == null) {
							dist.put(newLoc, new DistEntry(newLoc, newDist, aedist));
							// squared is kinda wrong, but this will get faster results
							pq.add(new PqEntry(newDist + newLoc.distanceSquaredTo(dest), newLoc));
						} else {
							if(newDist < distEntry.dist){
								distEntry.dist = newDist;
								distEntry.parent = aedist;
							}
						}
					}
				}
			}
		}
		if (pq.isEmpty()) {
			ready = true;
			calculating = false;
		}
//		if(oldRoundNum != RobotPlayer.rc.getRoundNum()){
//			System.out.println("!!!!!!!!!!!! PathFinder takes too long !!!!!!!!!!!!");
//		}
	}

	public float getMeshSize() {
		return meshSize;
	}

	public void setMeshSize(float meshSize) {
		this.meshSize = meshSize;
	}

	public boolean isCalculating() {
		return calculating;
	}

	public boolean isReady(){
		return ready;
	}

	public boolean isFoundDest() {
		return foundDest;
	}

	public List<MapLocation> getPath(){
		List<MapLocation> res = new ArrayList<>();
		DistEntry destElement = dist.get(dest);
		addToRes(res, destElement);
		return res;
	}

	public List<MapLocation> getRandomPath(){
		List<MapLocation> res = new ArrayList<>();
		if (dist.isEmpty()) {
			return res;
		}
		DistEntry destElement = (DistEntry) dist.values().toArray()[RobotPlayer.rnd.nextInt(dist.size())];
		addToRes(res, destElement);
		return res;
	}
}

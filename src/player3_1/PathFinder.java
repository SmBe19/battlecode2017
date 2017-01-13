package player3_1;

import battlecode.common.*;

import java.util.*;

/**
 * Path Finder
 */
public class PathFinder {

	private static class pqEntry implements Comparable<pqEntry>{
		final float dist;
		final MapLocation location;

		public pqEntry(float dist, MapLocation location) {
			this.dist = dist;
			this.location = location;
		}

		@Override
		public int compareTo(pqEntry o) {
			if(dist == o.dist){
				return location.compareTo(o.location);
			}
			return (int) Math.signum(dist - o.dist);
		}
	}

	private static class distEntry{
		final MapLocation location;
		float dist;
		distEntry parent;
		boolean done = false;

		public distEntry(MapLocation location, float dist, distEntry parent) {
			this.location = location;
			this.dist = dist;
			this.parent = parent;
		}
	}

	private static void addToRes(List<MapLocation> res, Map<MapLocation, distEntry> dist, distEntry element) {
		if(element != null){
			addToRes(res, dist, element.parent);
			res.add(element.location);
		}
	}

	private static float round(float val){
		return Math.round(val * Consts.PATHFINDER_ROUND) / Consts.PATHFINDER_ROUND;
	}

	public static List<MapLocation> findPath(MapLocation start, MapLocation dest, boolean chooseRandomIfNotFound, boolean useRobots) throws GameActionException {
		float stride = RobotPlayer.rc.getType().strideRadius;
		float bodyRadius = RobotPlayer.rc.getType().bodyRadius;
		float strideSquared = stride * stride;
		List<MapLocation> res = new ArrayList<>();
		PriorityQueue<pqEntry> pq = new PriorityQueue<>();
		Map<MapLocation, distEntry> dist = new HashMap<>();

		MapLocation niceDest = new MapLocation(
				Math.min(RobotPlayer.mapPos[1], Math.max(RobotPlayer.mapPos[3], round(dest.x))),
				Math.min(RobotPlayer.mapPos[0], Math.max(RobotPlayer.mapPos[2], round(dest.y))));
		// System.out.println("dest / nicedest: " + dest + " / " + niceDest);

		// clean trees and robots
		float distStartDestSquared = start.distanceSquaredTo(niceDest);
		List<BodyInfo> obstacles = new ArrayList<>();
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

		// System.out.println("obstacles: " + obstacles.size());

		distEntry root = new distEntry(start, 0, null);
		dist.put(start, root);
		pq.add(new pqEntry(0, start));

		while (!pq.isEmpty()) {
			pqEntry aepq = pq.poll();
			MapLocation aepqlocation = aepq.location;
			distEntry aedist = dist.get(aepqlocation);
			if(aedist.done || aedist.location.distanceSquaredTo(start) > distStartDestSquared * Consts.PATHFINDER_MAX_DISTSQ_MULT){
				continue;
			}
			aedist.done = true;

			// System.out.println("aepq " + aepqlocation + " " + aepq.dist);
			RobotPlayer.rc.setIndicatorDot(aepqlocation, 256, 0, 0);
			RobotPlayer.rc.setIndicatorDot(niceDest, 256, 128, 128);

			if(niceDest.distanceSquaredTo(aepqlocation) <= strideSquared){
				dist.put(niceDest, new distEntry(niceDest, aepq.dist + niceDest.distanceTo(aepqlocation), aedist));
				break;
			}

			float startx = Math.max(RobotPlayer.mapPos[3], aepqlocation.x - stride);
			float starty = Math.max(RobotPlayer.mapPos[2], aepqlocation.y - stride);
			float endx = Math.min(RobotPlayer.mapPos[1], aepqlocation.x + stride);
			float endy = Math.min(RobotPlayer.mapPos[0], aepqlocation.y + stride);
			// System.out.println("start / end: " + startx + " " + starty + " " + endx + " " + endy);
			for(float x = startx; x <= endx; x += Consts.PATHFINDER_MESH_SIZE) {
				for(float y = starty; y <= endy; y += Consts.PATHFINDER_MESH_SIZE) {
					MapLocation newLoc = new MapLocation(round(x), round(y));
					if(aepqlocation.distanceSquaredTo(newLoc) <= strideSquared){
						boolean possible = true;
						for (BodyInfo body : obstacles) {
							if(newLoc.distanceTo(body.getLocation()) <= bodyRadius + body.getRadius() + Consts.PATHFINDER_COLLISION_MARGIN){
								RobotPlayer.rc.setIndicatorDot(body.getLocation(), 0, 256, 0);
								possible = false;
								break;
							}
						}
						if(!possible){
							continue;
						}
						float newDist = aedist.dist + aepqlocation.distanceTo(newLoc);
						if (!dist.containsKey(newLoc)) {
							dist.put(newLoc, new distEntry(newLoc, newDist, aedist));
							// squared is kinda wrong, but this will get faster results
							pq.add(new pqEntry(newDist + newLoc.distanceSquaredTo(niceDest), newLoc));
						} else {
							distEntry distEntry = dist.get(newLoc);
							if(newDist < distEntry.dist){
								distEntry.dist = newDist;
								distEntry.parent = aedist;
							}
						}
					}
				}
			}
		}

		distEntry destElement = dist.get(niceDest);
		if (destElement == null && chooseRandomIfNotFound) {
			destElement = (distEntry) dist.values().toArray()[RobotPlayer.rnd.nextInt(dist.size())];
		}
		addToRes(res, dist, destElement);

		return res;
	}
}

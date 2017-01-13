package player3_1;

import battlecode.common.*;

import java.util.*;

/**
 * Player3_1
 *
 * Broadcast:
 *  0: top border
 *  1: right border
 *  2: bottom border
 *  3: left border
 *  42: ID of master
 */
public class RobotPlayer {

	private enum State{
		init,
		run,
	}

	static final float twoPi = (float) (Math.PI * 2);

	static RobotController rc = null;
	static RobotType robotType = null;
	static Random rnd;
	static State state = State.init;
	static boolean iAmMaster = false;

	static int[] mapPos = new int[] { Consts.MAX_MAP_SIZE, Consts.MAX_MAP_SIZE, 0, 0 };
	static boolean[] fmapPos = new boolean[4];
	static Direction[] mapPosDirections = new Direction[] { Direction.getNorth(), Direction.getEast(), Direction.getSouth(), Direction.getWest() };
	static float[][] mapPosOff = new float[][] { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };

	static int builtGardeners = 0;
	static boolean foundGoodPosition = false;

	static List<TreeInfo> trees = new ArrayList<>();
	static Map<Integer, Integer> idToTree = new HashMap<>();
	static List<TreeInfo> maTrees = new ArrayList<>();
	static Map<Integer, Integer> idToMaTrees = new HashMap<>();
	static List<RobotInfo> robots = new ArrayList<>();
	static Map<Integer, Integer> idToRobots = new HashMap<>();
	static int lastSenseTree = 0;
	static int lastSenseRobots = 0;

	static MapLocation currentDest = null;
	static MapLocation originalDest = null;
	static boolean currentDestIsRandom = false;
	static List<MapLocation> currentPath = null;
	static int currentPathIndex = 0;
	static int lastSuccessfullMove = 0;
	static int timeSinceLastSuccessfullMove = 0;

	static void drawRadius(float radius, int r, int g, int b) throws GameActionException {
		rc.setIndicatorDot(rc.getLocation().add(0, radius), r, g, b);
		rc.setIndicatorDot(rc.getLocation().add((float) Math.PI/2, radius), r, g, b);
		rc.setIndicatorDot(rc.getLocation().add((float) Math.PI, radius), r, g, b);
		rc.setIndicatorDot(rc.getLocation().add((float) Math.PI*3/2, radius), r, g, b);
	}

	static void senseTrees() throws GameActionException {
		if(rc.getRoundNum() - lastSenseTree < Consts.SENSE_TREE_COOLDOWN){
			return;
		}
		lastSenseTree = rc.getRoundNum();
		TreeInfo[] sTrees = rc.senseNearbyTrees();
		for (TreeInfo tree : sTrees) {
			if(idToTree.containsKey(tree.ID)){
				trees.set(idToTree.get(tree.ID), tree);
			} else {
				idToTree.put(tree.ID, trees.size());
				trees.add(tree);
				rc.setIndicatorDot(tree.getLocation(), 0, 0, 256);
			}
			if (idToMaTrees.containsKey(tree.getID())) {
				maTrees.set(tree.ID, tree);
			}
		}
	}

	static void senseRobots() {
		if(rc.getRoundNum() - lastSenseRobots < Consts.SENSE_ROBOTS_COOLDOWN){
			return;
		}
		lastSenseRobots = rc.getRoundNum();
		RobotInfo[] sRobots = rc.senseNearbyRobots();
		robots.clear();
		for (RobotInfo robot : sRobots) {
			robots.add(robot);
		}
	}

	static void senseMap() throws GameActionException {
		for(int i = 0; i < 4; i++){
			if(fmapPos[i]){
				continue;
			}
			if(rc.readBroadcast(i) != -1){
				mapPos[i] = rc.readBroadcast(i);
				fmapPos[i] = true;
			} else {
				if(!rc.onTheMap(rc.getLocation().add(mapPosDirections[i], robotType.sensorRadius))){
					float a = 0, b = robotType.sensorRadius;
					while(b - a > 1){
						float mid = (a + b) / 2;
						if(rc.onTheMap(rc.getLocation().add(mapPosDirections[i], mid))){
							a = mid;
						} else {
							b = mid;
						}
					}
					MapLocation finalPoint = rc.getLocation().add(mapPosDirections[i], a);
					if(i == 0 || i == 2){
						mapPos[i] = Math.round(finalPoint.y - mapPosOff[i][1]/2);
					} else {
						mapPos[i] = Math.round(finalPoint.x - mapPosOff[i][0]/2);
					}
					fmapPos[i] = true;
					System.out.println("found border " + i + ": " + mapPos[i]);
					rc.broadcast(i, mapPos[i]);
				}
			}
		}
	}

	static Direction findDirection(conditionCheckerDirection checker) {
		return findDirection(checker, Consts.DIRECTION_SEARCH_INCREMENT);
	}

	static Direction findDirection(conditionCheckerDirection checker, int increment){
		int start = rnd.nextInt(increment);
		for(int i = 0; i < increment; i++) {
			float adir = ((start + i) % increment) * twoPi / increment;
			Direction direction = new Direction(adir);
			if(checker.check(direction)){
				return direction;
			}
		}
		return null;
	}

	static void setDest(MapLocation destination) {
		currentDest = destination;
		originalDest = destination;
		currentDestIsRandom = false;
		lastSuccessfullMove = rc.getRoundNum();
	}

	static void updatePath() throws GameActionException {
		timeSinceLastSuccessfullMove = rc.getRoundNum() - lastSuccessfullMove;
		// System.out.println(timeSinceMove);
		if(currentPath == null || timeSinceLastSuccessfullMove > Consts.RESEARCH_PATH_UNSUCCESSFULL) {
			boolean arrived = currentDest != null && currentDest.distanceTo(rc.getLocation()) <= Consts.MOVE_ARRIVED_MARGIN;
			if (currentDest == null
					|| arrived
					|| timeSinceLastSuccessfullMove > Consts.RESEARCH_PATH_UNSUCCESSFULL2) {
				if(!currentDestIsRandom){
					originalDest = currentDest;
				}
				if(arrived && currentDestIsRandom && originalDest != null) {
					setDest(originalDest);
					System.out.println("reset original destination");
				} else {
					if (arrived && !currentDestIsRandom) {
						originalDest = null;
					}
					currentDest = rc.getLocation().add(rnd.nextFloat() * twoPi, rnd.nextFloat() * Consts.RANDOM_MOVE_DIST);
					currentDestIsRandom = true;
					lastSuccessfullMove = rc.getRoundNum();
					System.out.println("choose random destination");
				}
			}
			currentPath = PathFinder.findPath(rc.getLocation(), currentDest, true, true);
			currentPathIndex = 0;
			if(currentPath.isEmpty()){
				currentPath = null;
			} else if (!currentDestIsRandom){
				lastSuccessfullMove = rc.getRoundNum();
			}
		}
	}

	static void followPath() throws GameActionException {
		if (currentPath == null) {
			return;
		}
		MapLocation dest = currentPath.get(currentPathIndex);
		if(rc.canMove(dest)){
			if(dest.distanceTo(rc.getLocation()) > Consts.COUNTS_AS_MOVE) {
				lastSuccessfullMove = rc.getRoundNum();
			}
			rc.move(dest);
			currentPathIndex++;
			if (currentPathIndex >= currentPath.size()) {
				currentPath = null;
			}
		} else {
			rc.setIndicatorDot(dest, 255, 0, 0);
		}
	}

	static void initArchon() throws GameActionException {
		if(rc.getLocation().isWithinDistance(rc.getInitialArchonLocations(rc.getTeam())[0], 0.1f)){
			iAmMaster = true;
			System.out.println("I am master!");
			rc.broadcast(Consts.BROADCAST_MASTER, rc.getID());
			for(int i = 0; i < 4; i++){
				rc.broadcast(i, -1);
			}
		}
		state = State.run;
	}

	static void runArchon() throws GameActionException {
		if(iAmMaster){
			// Donate Bullets
			if(rc.getTeamBullets() >= GameConstants.BULLET_EXCHANGE_RATE * GameConstants.VICTORY_POINTS_TO_WIN){
				rc.donate(rc.getTeamBullets());
				System.out.println("We're done, donate all");
			} else if(rc.getTeamBullets() > Consts.DONATE_MIN_BULLETS + GameConstants.BULLET_EXCHANGE_RATE){
				float donate = rc.getTeamBullets() - Consts.DONATE_MIN_BULLETS;
				System.out.println("Donate1 " + donate);
				donate = Math.min(donate, Consts.DONATE_MAX_PER_ROUND);
				System.out.println("Donate2 " + donate);
				donate -= donate % GameConstants.BULLET_EXCHANGE_RATE;
				System.out.println("Donate " + donate + " bullets");
				rc.donate(donate);
			}
			if(rc.getRoundNum() >= rc.getRoundLimit() - 1){
				float donate = rc.getTeamBullets();
				donate -= donate % GameConstants.BULLET_EXCHANGE_RATE;
				System.out.println("Donate " + donate + " bullets (everything)");
				rc.donate(donate);
			}
		}

		senseMap();
		senseTrees();
		senseRobots();

		// hire gardeners
		Direction dir = findDirection(direction -> rc.canHireGardener(direction));
		if(dir != null && builtGardeners < Consts.GARDENER_COUNT){
			System.out.println("hire gardener " + dir);
			rc.hireGardener(dir);
			builtGardeners++;
		}

		updatePath();
		followPath();
	}

	static void initGardener() throws GameActionException{
		state = State.run;
	}

	static void runGardener() throws GameActionException{
		senseMap();
		senseTrees();
		senseRobots();

		if(originalDest == null || timeSinceLastSuccessfullMove > Consts.RESEARCH_PATH_UNSUCCESSFULL2) {
			Direction dir = findDirection(direction -> rc.canBuildRobot(RobotType.SOLDIER, direction));
			if (dir != null) {
				rc.buildRobot(RobotType.SOLDIER, dir);
			}
			setDest(rc.getLocation().add(rnd.nextFloat() * twoPi, 42));
		}

		updatePath();
		followPath();
	}

	static void initLumberjack() throws GameActionException{
		state = State.run;
	}

	static void runLumberjack() throws GameActionException{

	}

	static void initSoldier() throws GameActionException{
		state = State.run;
	}

	static void runSoldier() throws GameActionException{

	}

	static void initTank() throws GameActionException{
		state = State.run;
	}

	static void runTank() throws GameActionException{

	}

	static void initScout() throws GameActionException{
		state = State.run;
	}

	static void runScout() throws GameActionException{

	}

	interface conditionCheckerDirection{
		boolean check(Direction direction);
	}

	@SuppressWarnings("unused")
	public static void run(RobotController rc) throws GameActionException{
		RobotPlayer.rc = rc;
		robotType = rc.getType();
		rnd = new Random(rc.getID());
		while(true){
			try {
				switch (robotType) {
					case ARCHON:
						switch (state){
							case init:
								initArchon();
								break;
							default:
								runArchon();
								break;
						}
						break;
					case GARDENER:
						switch (state){
							case init:
								initGardener();
								break;
							default:
								runGardener();
								break;
						}
						break;
					case LUMBERJACK:
						switch (state){
							case init:
								initLumberjack();
								break;
							default:
								runLumberjack();
								break;
						}
						break;
					case SOLDIER:
						switch (state){
							case init:
								initSoldier();
								break;
							default:
								runSoldier();
								break;
						}
						break;
					case TANK:
						switch (state){
							case init:
								initTank();
								break;
							default:
								runTank();
								break;
						}
						break;
					case SCOUT:
						switch (state){
							case init:
								initScout();
								break;
							default:
								runScout();
								break;
						}
						break;
				}

				//drawRadius(robotType.sensorRadius, 256, 128, 128);
				//drawRadius(robotType.strideRadius, 128, 256, 128);
				//drawRadius(robotType.bulletSightRadius, 128, 128, 256);

				Clock.yield();
			} catch (Exception e) {
				System.out.println("Exception " + robotType.name() + " " + rc.getID() + ": " + e.getMessage());
				e.printStackTrace(System.out);
			}
		}
	}
}

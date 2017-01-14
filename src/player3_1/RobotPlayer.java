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
 *  42: gardenercount 0
 *  43: gardenercount 1
 *  44: gardenercount 2
 */
public class RobotPlayer {

	private enum State{
		init,
		run,
		trees,
		attack,
	}

	static final float twoPi = (float) (Math.PI * 2);

	static RobotController rc = null;
	static Team myTeam = Team.NEUTRAL, enemyTeam = Team.NEUTRAL;
	static RobotType robotType = null;
	static MapLocation[] initialArchonLocations;
	static Random rnd;
	static PathFinder pathFinder = new PathFinder();
	static State state = State.init;
	static boolean iAmMaster = false;
	static int myArcheonNumber = -1;

	static int[] mapPos = new int[] { Consts.MAX_MAP_SIZE, Consts.MAX_MAP_SIZE, 0, 0 };
	static boolean[] fmapPos = new boolean[4];
	static boolean fmapAll = false;
	static final Direction[] mapPosDirections = new Direction[] { Direction.getNorth(), Direction.getEast(), Direction.getSouth(), Direction.getWest() };
	static final float[][] mapPosOff = new float[][] { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };

	static int builtGardeners = 0;
	static int deadGardeners = 0;
	static boolean toldImDead = false;
	static boolean iWantTree = false;
	static boolean foundGoodPosition = false;
	static int maTreeIndex = 0;
	static boolean plantedNewTree = false;
	static final RobotType[] buildOrder = new RobotType[]{RobotType.SOLDIER, RobotType.SOLDIER, RobotType.SOLDIER};
	static int currentBuild;
	static int scoutCurrentDirection;
	static Direction directionBias;

	static List<TreeInfo> trees = new ArrayList<>();
	static Map<Integer, Integer> idToTree = new HashMap<>();
	static List<TreeInfo> maTrees = new ArrayList<>();
	static Map<Integer, Integer> idToMaTrees = new HashMap<>();
	static List<RobotInfo> robots = new ArrayList<>();
	static Map<Integer, Integer> idToRobots = new HashMap<>();
	static List<BulletInfo> bullets = new ArrayList<>();
	static int lastSenseTree = 0;
	static int lastSenseRobots = 0;

	static boolean allowRandomMove = true;
	static MapLocation originalDest = null;
	static MapLocation currentDest = null;
	static int lastSuccessfullMove = 0;
	static int lastSuccessfullPathProgress = 0;
	static int timeSinceLastSuccessfullMove = 0;
	static int timeSinceLastSuccessfullPathProgress = 0;

	static List<MapLocation> currentPath = null;
	static int currentPathIndex = 0;

	static void drawRadius(float radius, int r, int g, int b) throws GameActionException {
		rc.setIndicatorDot(rc.getLocation().add(0, radius), r, g, b);
		rc.setIndicatorDot(rc.getLocation().add((float) Math.PI/2, radius), r, g, b);
		rc.setIndicatorDot(rc.getLocation().add((float) Math.PI, radius), r, g, b);
		rc.setIndicatorDot(rc.getLocation().add((float) Math.PI*3/2, radius), r, g, b);
	}

	static void findMyArcheon(){
		float mindist = Consts.MAX_MAP_SIZE;
		int minval = 0;
		for(int i = 0; i < initialArchonLocations.length; i++) {
			float dist = initialArchonLocations[i].distanceTo(rc.getLocation());
			if (dist < mindist) {
				mindist = dist;
				minval = i;
			}
		}
		myArcheonNumber = minval;
//		System.out.println("I am with archeon " + myArcheonNumber);
	}

	static void setDirectionBias(){
		directionBias = initialArchonLocations[myArcheonNumber].directionTo(rc.getInitialArchonLocations(enemyTeam)[myArcheonNumber]);
	}

	static void senseTrees() throws GameActionException {
		if(rc.getRoundNum() - lastSenseTree < Consts.SENSE_TREE_COOLDOWN){
			return;
		}
		lastSenseTree = rc.getRoundNum();
		TreeInfo[] sTrees = rc.senseNearbyTrees();
		TreeInfo closestTree = null;
		float closestDist = Consts.MAX_MAP_SIZE;
		for (TreeInfo tree : sTrees) {
			if(idToTree.containsKey(tree.ID)){
				trees.set(idToTree.get(tree.ID), tree);
			} else {
				idToTree.put(tree.ID, trees.size());
				trees.add(tree);
//				rc.setIndicatorDot(tree.getLocation(), 0, 0, 256);
			}
			if(tree.team == myTeam) {
				if (idToMaTrees.containsKey(tree.getID())) {
					maTrees.set(idToMaTrees.get(tree.ID), tree);
				} else if (plantedNewTree) {
					float dist = tree.location.distanceTo(rc.getLocation());
					if (dist < closestDist) {
						closestDist = dist;
						closestTree = tree;
					}
				}
			}
		}
		if (plantedNewTree && closestTree != null) {
			idToMaTrees.put(closestTree.ID, maTrees.size());
			maTrees.add(closestTree);
			plantedNewTree = false;
//			System.out.println("found ma tree");
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

	static void senseBullets(){
		BulletInfo[] sBullets = rc.senseNearbyBullets();
		bullets.clear();
		for (BulletInfo bullet : sBullets) {
			bullets.add(bullet);
		}
	}

	static void senseMap() throws GameActionException {
		fmapAll = true;
		for(int i = 0; i < 4; i++){
			if(fmapPos[i]){
				continue;
			}
			fmapAll = false;
			if(rc.readBroadcast(i) != -1){
				mapPos[i] = rc.readBroadcast(i);
				fmapPos[i] = true;
			} else {
				if(!rc.onTheMap(rc.getLocation().add(mapPosDirections[i], Math.nextDown(robotType.sensorRadius)))){
					float a = 0, b = Math.nextDown(robotType.sensorRadius);
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
//					System.out.println("found border " + i + ": " + mapPos[i]);
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
		currentDest = null;
		originalDest = destination;
		currentPath = null;
		lastSuccessfullMove = rc.getRoundNum();
	}

	static void resetMove(){
		originalDest = null;
		currentDest = null;
		currentPath = null;
	}

	static void updatePath() throws GameActionException {
		if (pathFinder.isCalculating()) {
			pathFinder.update();
			return;
		}
		timeSinceLastSuccessfullMove = rc.getRoundNum() - lastSuccessfullMove;
		timeSinceLastSuccessfullPathProgress = rc.getRoundNum() - lastSuccessfullPathProgress;
		// System.out.println(timeSinceLastSuccessfullMove + " / " + timeSinceLastSuccessfullPathProgress);
		if(timeSinceLastSuccessfullMove > Consts.RESEARCH_PATH_UNSUCCESSFULL){
			currentPath = null;
		}
		if(originalDest != null && originalDest.distanceTo(rc.getLocation()) <= Consts.MOVE_ARRIVED_MARGIN){
			originalDest = null;
			// System.out.println("Arrived");
		}
		if(currentPath == null){
			if(pathFinder.isReady()) {
				if (pathFinder.isFoundDest()) {
					currentPath = pathFinder.getPath();
					currentPathIndex = 0;
				} else {
					currentPath = pathFinder.getRandomPath();
					currentPathIndex = 0;
				}
				if (currentPath.isEmpty()) {
					currentPath = null;
				}
				pathFinder.reset();
				lastSuccessfullMove = rc.getRoundNum();
			} else {
				if (originalDest == null) {
					if(allowRandomMove) {
						currentDest = rc.getLocation().add(rnd.nextFloat() * twoPi, rnd.nextFloat() * Consts.RANDOM_MOVE_BORED_DIST);
					} else {
						return;
					}
				} else if(currentDest == originalDest) {
					if(timeSinceLastSuccessfullPathProgress > Consts.RESEARCH_PATH_UNSUCCESSFULL2){
						currentDest = rc.getLocation().add(rnd.nextFloat() * twoPi, rnd.nextFloat() * Consts.RANDOM_MOVE_DIST);
						// System.out.println("move a bit");
					} else {
						// System.out.println("research path");
					}
				} else {
					currentDest = originalDest;
					// System.out.println("goto original dest");
				}
				pathFinder.findPath(rc.getLocation(), currentDest, true);
			}
		}
	}

	static void followPath() throws GameActionException {
		if (currentPath == null) {
			return;
		}
		MapLocation dest = currentPath.get(currentPathIndex);
		boolean canMove = rc.canMove(dest) && !rc.hasMoved();
		if(!canMove){
			dest = rc.getLocation().add(rc.getLocation().directionTo(dest), Consts.SMALL_MOVE);
			canMove = rc.canMove(dest) && !rc.hasMoved();
		}
		if(canMove){
			if(dest.distanceTo(rc.getLocation()) > Consts.COUNTS_AS_MOVE) {
				lastSuccessfullMove = rc.getRoundNum();
				if(currentDest == originalDest) {
					lastSuccessfullPathProgress = rc.getRoundNum();
				}
			}
			rc.move(dest);
			currentPathIndex++;
			if (currentPathIndex >= currentPath.size()) {
				currentPath = null;
			}
		} else {
//			rc.setIndicatorDot(dest, 255, 0, 0);
		}
	}

	static void shakeTrees() throws GameActionException {
		for (TreeInfo tree : trees) {
			if (tree.team == Team.NEUTRAL && rc.canShake(tree.ID)) {
				rc.shake(tree.ID);
				return;
			}
		}
	}

	static void ommitBullets() throws GameActionException {
		for(BulletInfo bullet : bullets){
			if(rc.getLocation().isWithinDistance(bullet.location.add(bullet.dir, bullet.speed), robotType.bodyRadius)){
				for(int i = 1; i <= 3; i += 2) {
					Direction direction = bullet.dir.rotateLeftDegrees(i * 90);
					if(rc.canMove(direction, robotType.strideRadius)){
						rc.move(direction, robotType.strideRadius);
//						System.out.println("ommit");
						return;
					}
				}
			}
		}
	}

	static void attack() throws GameActionException {
		if(!rc.canFireSingleShot()){
			return;
		}
		enemyRobot:
		for (RobotInfo robot : robots) {
			if (robot.team == myTeam) {
				continue;
			}
			MapLocation location = rc.getLocation();
			float distsq = robot.location.distanceSquaredTo(location) - Consts.ATTACK_MARGIN;
			for(RobotInfo robot2 : robots){
				if(robot.team == myTeam){
					if(robot2.location.distanceSquaredTo(location) < distsq){
						continue enemyRobot;
					}
				}
			}
			Direction direction = location.directionTo(robot.location);
			if(distsq <= Consts.DISTSQ_PENTAD && rc.canFirePentadShot()){
				rc.firePentadShot(direction);
			} else if (distsq <= Consts.DISTSQ_TRIAD && rc.canFireTriadShot()){
				rc.fireTriadShot(direction);
			} else {
				rc.fireSingleShot(direction);
			}
			break;
		}
	}

	static boolean isProbablyOnMap(MapLocation location, float radius){
		return !(location.x - radius < mapPos[3] || location.x + radius > mapPos[1]
				|| location.y - radius < mapPos[2] || location.y + radius > mapPos[0]);
	}

	static boolean isGoodArchonPosition(){
		return isProbablyOnMap(rc.getLocation(), Consts.ARCHON_NEED_SPACE)
				&& initialArchonLocations[myArcheonNumber].distanceTo(rc.getLocation()) >= Consts.ARCHON_MIN_DIST_TO_START
				&& rc.senseNearbyTrees(Consts.ARCHON_NEED_SPACE).length == 0;
	}

	static boolean isGoodGardenerPosition(){
		return isProbablyOnMap(rc.getLocation(), Consts.GARDENER_NEED_SPACE)
				&& initialArchonLocations[myArcheonNumber].distanceTo(rc.getLocation()) >= Consts.GARDENER_MIN_DIST_TO_START
				&& rc.senseNearbyTrees(Consts.GARDENER_NEED_SPACE).length == 0
				&& rc.senseNearbyRobots(Consts.GARDENER_NEED_SPACE, myTeam).length == 0;
	}

	static void initArchon() throws GameActionException {
		if(myArcheonNumber == 0){
			iAmMaster = true;
//			System.out.println("I am master!");
			for(int i = 0; i < 4; i++){
				rc.broadcast(i, -1);
			}
		}
		rc.broadcast(Consts.BROADCAST_MASTER + myArcheonNumber, 0);
		state = State.run;
	}

	static void runArchon() throws GameActionException {
		if(iAmMaster){
			// Donate Bullets
			if(rc.getTeamBullets() >= GameConstants.BULLET_EXCHANGE_RATE * (GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints())){
				rc.donate(rc.getTeamBullets());
				System.out.println("We're done, donate all");
			} else if(rc.getTeamBullets() > Consts.DONATE_MIN_BULLETS + GameConstants.BULLET_EXCHANGE_RATE){
				float donate = rc.getTeamBullets() - Consts.DONATE_MIN_BULLETS;
//				System.out.println("Donate1 " + donate);
				donate = Math.min(donate, Consts.DONATE_MAX_PER_ROUND);
//				System.out.println("Donate2 " + donate);
				donate -= donate % GameConstants.BULLET_EXCHANGE_RATE;
//				System.out.println("Donate " + donate + " bullets");
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
		senseBullets();

		// hire gardeners
		deadGardeners = rc.readBroadcast(Consts.BROADCAST_MASTER + myArcheonNumber);
		if(builtGardeners - deadGardeners < Consts.GARDENER_COUNT){
			Direction dir = findDirection(direction -> rc.canHireGardener(direction));
			if(dir != null){
//				System.out.println("hire gardener " + dir);
				rc.hireGardener(dir);
				builtGardeners++;
			}
		}

		if(!foundGoodPosition){
			if (originalDest == null || timeSinceLastSuccessfullPathProgress > Consts.RESEARCH_PATH_UNSUCCESSFULL2) {
				setDest(rc.getLocation().add(rnd.nextFloat() * twoPi, rnd.nextFloat() * Consts.GOOD_POSITION_SEARCH_RADIUS).add(directionBias, Consts.BIAS_STRENGTH));
				lastSuccessfullPathProgress = rc.getRoundNum();
//				System.out.println("try different pos");
			}
			if(isGoodArchonPosition()){
				resetMove();
				allowRandomMove = false;
				foundGoodPosition = true;
//				System.out.println("found good position");
			}
		}

		if(!foundGoodPosition) {
			ommitBullets();
		}
		updatePath();
		followPath();
	}

	static void initGardener() throws GameActionException{
		state = State.run;
		iWantTree = rnd.nextFloat() < Consts.GARDENER_TREES_PROBABILITY;
		System.out.println("I want tree");
	}

	static void runGardener() throws GameActionException{
		senseMap();
		senseTrees();
		senseRobots();
		senseBullets();

		if (!toldImDead && rc.getHealth() < Consts.GARDENER_MIN_HEALTH) {
			int broadcastNumber = Consts.BROADCAST_MASTER + myArcheonNumber;
			rc.broadcast(broadcastNumber, rc.readBroadcast(broadcastNumber) + 1);
			toldImDead = true;
		}

		if (originalDest == null || timeSinceLastSuccessfullPathProgress > Consts.RESEARCH_PATH_UNSUCCESSFULL2) {
			setDest(rc.getLocation().add(rnd.nextFloat() * twoPi, rnd.nextFloat() * Consts.GOOD_POSITION_SEARCH_RADIUS).add(directionBias, Consts.BIAS_STRENGTH));
			lastSuccessfullPathProgress = rc.getRoundNum();
//			System.out.println("try different pos");
		}
		if(iWantTree && !foundGoodPosition && rc.getRoundNum() >= rc.getRoundLimit() * Consts.NO_TREES_BEFORE_ROUND){
			if(isGoodGardenerPosition() || rc.getRoundNum() > rc.getRoundLimit() * Consts.FACK_GOOD_PLACE_ROUND){
				resetMove();
				foundGoodPosition = true;
//				System.out.println("found good position");
				state = State.trees;
				allowRandomMove = false;
				System.out.println("I now tree");
			}
		}
		if(rc.getBuildCooldownTurns() == 0 && rnd.nextFloat() < Consts.GARDENER_BUILD_PROBABILITY) {
			RobotType currentBuildType = buildOrder[currentBuild];
			Direction dir = findDirection(direction -> rc.canBuildRobot(currentBuildType, direction));
			if (dir != null) {
				rc.buildRobot(currentBuildType, dir);
				currentBuild++;
				currentBuild %= buildOrder.length;
			}
		}

		ommitBullets();
		shakeTrees();
		updatePath();
		followPath();
	}

	static void treesGardener() throws GameActionException{
		senseTrees();

		if (!toldImDead && rc.getHealth() < Consts.GARDENER_MIN_HEALTH) {
			int broadcastNumber = Consts.BROADCAST_MASTER + myArcheonNumber;
			rc.broadcast(broadcastNumber, rc.readBroadcast(broadcastNumber) + 1);
			toldImDead = true;
		}

		if (!plantedNewTree && rc.getBuildCooldownTurns() == 0) {
			Direction dir = findDirection(direction -> rc.canPlantTree(direction), 4);
			if (dir != null) {
				rc.plantTree(dir);
				plantedNewTree = true;
			}
		}
		if(maTrees.size() > 0) {
			TreeInfo maTree = maTrees.get(maTreeIndex);
			int oldTreeIndex = maTreeIndex;
			while(maTree.health > Consts.TREE_HEALTH_UNTIL_NEXT || maTree.health < Consts.TREE_HEALTH_FOR_DEAD || !rc.canWater(maTree.ID)) {
				maTreeIndex++;
				maTreeIndex %= maTrees.size();
				maTree = maTrees.get(maTreeIndex);
				if (maTreeIndex == oldTreeIndex) {
					break;
				}
			}
			if (rc.canWater(maTree.ID)) {
				rc.water(maTree.ID);
			}
		}
	}

	static void initLumberjack() throws GameActionException{
		state = State.run;
	}

	static void runLumberjack() throws GameActionException{

	}

	static void initSoldier() throws GameActionException{
		state = State.run;
		if(rnd.nextFloat() < Consts.SOLDIER_ATTACK_PROBABILITY){
			state = State.attack;
			System.out.println("I now boom");
		}
	}

	static void runSoldier() throws GameActionException{
		if(!fmapAll){
			attackSoldier();
			return;
		}
		senseMap();
		senseTrees();
		senseRobots();
		senseBullets();

		if (originalDest == null || timeSinceLastSuccessfullPathProgress > Consts.RESEARCH_PATH_UNSUCCESSFULL2) {
			setDest(new MapLocation(rnd.nextFloat() * (mapPos[1] - mapPos[3]) + mapPos[3],
					rnd.nextFloat() * (mapPos[0] - mapPos[2]) + mapPos[2]).add(directionBias, Consts.BIAS_STRENGTH));
			lastSuccessfullPathProgress = rc.getRoundNum();
		}

		ommitBullets();
		attack();
		shakeTrees();
		updatePath();
		followPath();
	}

	static void attackSoldier() throws GameActionException{
		senseMap();
		senseTrees();
		senseRobots();
		senseBullets();

		if (originalDest == null || timeSinceLastSuccessfullPathProgress > Consts.RESEARCH_PATH_UNSUCCESSFULL2) {
			setDest(rc.getLocation().add(rnd.nextFloat() * twoPi, rnd.nextFloat() * Consts.ATACK_POSITION_SEARCH_RADIUS).add(directionBias, Consts.BIAS_STRENGTH));
			lastSuccessfullPathProgress = rc.getRoundNum();
		}

		ommitBullets();
		attack();
		shakeTrees();
		updatePath();
		followPath();
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
		senseMap();
		senseTrees();
		senseRobots();
		senseBullets();

		if(currentDest == null || timeSinceLastSuccessfullPathProgress > Consts.RESEARCH_PATH_UNSUCCESSFULL2) {
			setDest(rc.getLocation().add(mapPosDirections[scoutCurrentDirection], rnd.nextFloat() * Consts.SCOUT_SEARCH_RADIUS));
			lastSuccessfullPathProgress = rc.getRoundNum();
			scoutCurrentDirection++;
			scoutCurrentDirection %= mapPosDirections.length;
		}

		ommitBullets();
		attack();
		shakeTrees();

		if(currentDest != null && currentDest.distanceTo(rc.getLocation()) < Consts.MOVE_ARRIVED_MARGIN){
			currentDest = null;
		}
		if(currentDest != null){
//			rc.setIndicatorDot(currentDest, 256, 128, 128);
			Direction direction = rc.getLocation().directionTo(currentDest);
			if(rc.canMove(direction, robotType.strideRadius)){
				rc.move(direction, robotType.strideRadius);
			}
		}
	}

	interface conditionCheckerDirection{
		boolean check(Direction direction);
	}

	@SuppressWarnings("unused")
	public static void run(RobotController rc) throws GameActionException{
		RobotPlayer.rc = rc;
		myTeam = rc.getTeam();
		enemyTeam = myTeam == Team.A ? Team.B : Team.A;
		robotType = rc.getType();
		initialArchonLocations = rc.getInitialArchonLocations(myTeam);
		rnd = new Random(rc.getID());
		pathFinder.init();
		findMyArcheon();
		setDirectionBias();
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
							case trees:
								treesGardener();
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
							case attack:
								attackSoldier();
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
			} catch (Exception e) {
				System.out.println("Exception " + robotType.name() + " " + rc.getID() + ": " + e.getMessage());
				e.printStackTrace(System.out);
			}
			Clock.yield();
		}
	}
}

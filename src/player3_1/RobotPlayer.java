package player3_1;

import battlecode.common.*;

/**
 * Player3_1
 */
public class RobotPlayer {
	private enum State{
		init,
		run,
	}

	static final Direction directions[] = new Direction[]{Direction.getNorth(), Direction.getEast(), Direction.getSouth(), Direction.getWest()};

	static RobotController rc = null;
	static State state = State.init;
	static boolean iAmMaster = false;

	static int builtGardeners = 0;

	@SuppressWarnings("unused")
	public static void run(RobotController rc) throws GameActionException{
		RobotPlayer.rc = rc;
		while(true){
			try {
				switch (rc.getType()) {
					case ARCHON:
						switch (state){
							case init:
								initArchon();
								break;
							case run:
								runArchon();
								break;
						}
						break;
					case GARDENER:
						switch (state){
							case init:
								initGardener();
								break;
							case run:
								runGardener();
								break;
						}
						break;
					case LUMBERJACK:
						runLumberjack();
						break;
					case SOLDIER:
						runSoldier();
						break;
					case TANK:
						runTank();
						break;
					case SCOUT:
						runScout();
						break;
				}
				Clock.yield();
			} catch (Exception e) {
				System.out.println("Exception " + rc.getType().name() + " " + rc.getID() + ": " + e.getMessage());
			}
		}
	}

	public static void initArchon() throws GameActionException {
		if(rc.getLocation().isWithinDistance(rc.getInitialArchonLocations(rc.getTeam())[0], 0.1f)){
			iAmMaster = true;
			System.out.println("I am master!");
		}
		state = State.run;
	}

	public static void runArchon() throws GameActionException {
		if(iAmMaster){
			// Donate Bullets
			if(rc.getTeamBullets() > Consts.DONATE_MIN_BULLETS){
				float donate = rc.getTeamBullets() - Consts.DONATE_MIN_BULLETS;
				donate -= donate % GameConstants.BULLET_EXCHANGE_RATE;
				System.out.println("Donate " + donate + " bullets");
				rc.donate(donate);
			}
		}

		// hire gardeners
		Direction dir = null;
		for(Direction adir : directions){
			if(rc.canHireGardener(adir)){
				dir = adir;
				break;
			}
		}

		if(dir != null && builtGardeners < Consts.GARDENER_COUNT){
			rc.hireGardener(dir);
			builtGardeners++;
		}
	}

	public static void initGardener() throws GameActionException{
		state = State.run;
	}

	public static void runGardener() throws GameActionException{

	}

	public static void runLumberjack() throws GameActionException{

	}

	public static void runSoldier() throws GameActionException{

	}

	public static void runTank() throws GameActionException{

	}

	public static void runScout() throws GameActionException{

	}
}

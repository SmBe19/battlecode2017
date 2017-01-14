package player3_14;

import battlecode.common.*;

/**
 * Player3_14
 */
public class RobotPlayer {

	static RobotController rc;
	static RobotType robotType;
	static Team myTeam, enemyTeam;
	static MapLocation[] initialArchonLocations, initialArchonLocationsEnemy;

	static void printException(Exception e) {
		System.out.println("Exception: " + e.getMessage());
		e.printStackTrace(System.out);
	}

	static void runArchon(){
		while (true) {
			try {
				// TODO
			} catch (Exception e) {
				printException(e);
			}
			Clock.yield();
		}
	}

	public static void run(RobotController rc) {
		RobotPlayer.rc = rc;
		robotType = rc.getType();
		myTeam = rc.getTeam();
		enemyTeam = myTeam == Team.A ? Team.B : Team.A;
		initialArchonLocations = rc.getInitialArchonLocations(myTeam);
		initialArchonLocationsEnemy = rc.getInitialArchonLocations(enemyTeam);

		switch (robotType) {
			case ARCHON:
				runArchon();
				break;
			case GARDENER:
				break;
			case LUMBERJACK:
				break;
			case SOLDIER:
				break;
			case TANK:
				break;
			case SCOUT:
				break;
		}
	}
}

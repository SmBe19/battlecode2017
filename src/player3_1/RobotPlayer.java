package player3_1;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

/**
 * Player3_1
 */
public class RobotPlayer {
	static RobotController rc;

	@SuppressWarnings("unused")
	public static void run(RobotController rc) throws GameActionException{
		RobotPlayer.rc = rc;

		switch (rc.getType()) {
			case ARCHON:
				runArchon();
				break;
			case GARDENER:
				runGardener();
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
	}

	public static void runArchon(){
	}

	public static void runGardener(){

	}

	public static void runLumberjack(){

	}

	public static void runSoldier(){

	}

	public static void runTank(){

	}

	public static void runScout(){

	}
}

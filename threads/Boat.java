package nachos.threads;

import nachos.ag.BoatGrader;

public class Boat {
	static BoatGrader bg;

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		// System.out.println("\n ***Testing Boats with only 2 children***");
		begin(3, 4, b);

		// System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		// begin(1, 2, b);

		// System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		// begin(3, 3, b);
	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.

		communicator = new Communicator();
		oahu = new Information();
		molokai = new Information();
		oahu.hasBoat = true;
		mutex = new Lock();
		waitMolokai = new Condition(mutex);
		waitOahu = new Condition(mutex);
		waitToGo = new Condition(mutex);

		Runnable r = new Runnable() {
			public void run() {
				ChildItinerary();
			}
		};

		for (int i = 0; i < children; i++) {
			KThread t = new KThread(r);
			t.setName("Child " + i);
			t.fork();
		}

		r = new Runnable() {
			public void run() {
				AdultItinerary();
			}
		};

		for (int i = 0; i < adults; i++) {
			KThread t = new KThread(r);
			t.setName("Adult " + i);
			t.fork();
		}

		while (communicator.listen() != adults + children)
			;
	}

	static boolean can(int type, Information loc) {
		if (!loc.hasBoat)
			return false;
		if (type == ADULT)
			return loc.waitingChildren == 0;
		else
			return loc.waitingChildren <= 1;
	}

	static void report() {
		communicator.speak(molokai.cnt[0] + molokai.cnt[1]);
	}

	static void travel(int type, boolean row, boolean ride, Information from,
			Information to) {
		int mask = type * 4;
		if (row)
			mask += 2;
		if (to == oahu)
			mask += 1;

		switch (mask) {
		case 0:// 000
			bg.AdultRideToMolokai();
			break;
		case 1:// 001
			bg.AdultRideToOahu();
			break;
		case 2:// 010
			bg.AdultRowToMolokai();
			break;
		case 3:// 011
			bg.AdultRowToOahu();
			break;
		case 4:// 100
			bg.ChildRideToMolokai();
			break;
		case 5:
			bg.ChildRideToOahu();
			break;
		case 6:
			bg.ChildRowToMolokai();
			break;
		case 7:
			bg.ChildRowToOahu();
			break;
		default:
			break;
		}
		from.cnt[type]--;
		to.cnt[type]++;
		if (type == CHILD)
			from.waitingChildren--;

		if (row)
			from.hasBoat = false;

		if (ride) {
			to.hasBoat = true;
			if (to == molokai) {
				report();
				waitMolokai.wakeAll();
			} else {
				waitOahu.wakeAll();
			}
		}
	}

	static void AdultItinerary() {
		bg.initializeAdult(); // Required for autograder interface. Must be the
								// first thing called.
		// DO NOT PUT ANYTHING ABOVE THIS LINE.

		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */

		oahu.cnt[ADULT]++;
		mutex.acquire();
		while (!(can(ADULT, oahu)) || oahu.cnt[CHILD] >= 2) {
			if (oahu.hasBoat)
				waitOahu.wakeAll();
			waitOahu.sleep();
		}
		travel(ADULT, true, true, oahu, molokai);
		mutex.release();
	}

	static void ChildItinerary() {
		bg.initializeChild(); // Required for autograder interface. Must be the
								// first thing called.
		// DO NOT PUT ANYTHING ABOVE THIS LINE.

		oahu.cnt[CHILD]++;
		Information where = oahu;
		for (;;) {
			mutex.acquire();
			if (where == oahu) {
				while (!can(CHILD, oahu)
						|| (oahu.cnt[ADULT] > 0 && oahu.cnt[CHILD] == 1)) {
					if (oahu.hasBoat)
						waitOahu.wakeAll();
					waitOahu.sleep();
				}
				oahu.waitingChildren++;
				if (oahu.cnt[CHILD] >= 2) {
					if (oahu.waitingChildren == 1) {
						waitToGo.sleep();
						travel(CHILD, false, true, oahu, molokai);
					} else {
						waitToGo.wake();
						travel(CHILD, true, false, oahu, molokai);
					}
				} else {
					travel(CHILD, true, true, oahu, molokai);
				}
				where = molokai;
			} else {
				while (!can(CHILD, molokai)) {
					if (molokai.hasBoat)
						waitMolokai.wakeAll();
					waitMolokai.sleep();
				}
				travel(CHILD, true, true, molokai, oahu);
				where = oahu;
			}
			mutex.release();
		}
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out
				.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

	private static Communicator communicator;
	private static Information oahu, molokai;
	private static Lock mutex;
	private static Condition waitMolokai, waitOahu, waitToGo;

	private static class Information {
		int waitingChildren;
		int[] cnt = new int[2];
		boolean hasBoat;
	}

	private static final int ADULT = 0;
	private static final int CHILD = 1;
}

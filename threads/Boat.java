package nachos.threads;
import nachos.ag.BoatGrader;
import java.util.LinkedList;

public class Boat
{
    static BoatGrader bg;
    
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();
	
//	System.out.println("\n ***Testing Boats with only 2 children***");
//	begin(0, 2, b);

//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
 // 	begin(1, 2, b);

 	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
  	begin(3, 3, b);
    }
    
    static int childrenOnOahu = 0;
    static int adultOnOahu = 0;
	static boolean First = false;
	static boolean boatOnOahu = true;
	static Lock boat = new Lock();
	static Condition back = new Condition(boat);
	static Condition waitForAnother = new Condition(boat);
	static Condition waitForBack = new Condition(boat);
	static Condition waitForSolo = new Condition(boat);

    public static void begin( int adults, int children, BoatGrader b )
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;
	
	// Instantiate global variables here
	
	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.
    
	/*
	Runnable r = new Runnable() {
	    public void run() {
                SampleItinerary();
            }
        };
        KThread t = new KThread(r);
        t.setName("Sample Boat Thread");
        t.fork();
        boat.acquire();
        waitForBack.sleep();
        boat.release();
    */
	KThread[] Adult = new KThread[10];
	KThread[] Child = new KThread[10];
	
	Runnable r1 = new Runnable() {
		public void run() {
			ChildItinerary();
		}
	};
	Runnable r2 = new Runnable() {
		public void run() {
			AdultItinerary();
		}
	};	
	
	for (int i = 0; i < adults; i++) {
		Adult[i] = new KThread(r2);
		Adult[i].fork();
	}
	for (int i = 0; i < children; i++) {
		Child[i] = new KThread(r1);
		Child[i].fork();
	}	
	
	boat.acquire();
    back.sleep();
    boat.release();

    }
    

    static void AdultItinerary()
    {
	bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 

	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
		boolean isOnOahu = true;
	
		adultOnOahu++;
		while (isOnOahu) {
			boat.acquire();
			
			waitForSolo.sleep();
			isOnOahu = false;

			bg.AdultRowToMolokai();
			waitForBack.wake();
			adultOnOahu--;
			
			boat.release();
		}
    }

    static void ChildItinerary()
    {
	bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 
		boolean isOnOahu = true;
		boolean isPilot = false;
		boolean flag = false;
		
		childrenOnOahu++;
		while (!flag) {
			boat.acquire();

			if (isOnOahu) {
				if (boatOnOahu) {
					if (!First) {
						isPilot = true;
						First = true;
																		
						waitForAnother.sleep();
						bg.ChildRideToMolokai();					
						childrenOnOahu--;
					}
					else {												
						waitForAnother.wake();
						bg.ChildRowToMolokai();							
						childrenOnOahu--;
					}
					isOnOahu = false;
					boatOnOahu = false;		
				}
				else {
					waitForAnother.sleep();
				}
			}
			else {
				if (!boatOnOahu && isPilot) {					
					if (childrenOnOahu >= 1) {
						bg.ChildRowToOahu();						
						waitForAnother.wake();
						childrenOnOahu--;
						bg.ChildRowToMolokai();
												
					}
					else {			
						bg.ChildRowToOahu();	

						isOnOahu = true;
						isPilot = false;
						First = false;
						boatOnOahu = true;
						waitForSolo.wake();						
						
						if (adultOnOahu == 0) {
							bg.ChildRowToMolokai();
							flag = true;
						}
					}
				}
				else {					
					waitForBack.sleep();
					bg.ChildRowToOahu();			
					boatOnOahu = true;
					isOnOahu = true;
				}
			}
			
			boat.release();
		}
		boat.acquire();
		back.wake();
		boat.release();
    }

    static void SampleItinerary()
    {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
	boat.acquire();
    waitForBack.wake();
    boat.release();
    }
    
}

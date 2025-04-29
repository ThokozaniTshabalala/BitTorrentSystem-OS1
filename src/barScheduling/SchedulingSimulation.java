//M. M. Kuttel 2025 mkuttel@gmail.com

package barScheduling;
// the main class, starts all threads and the simulation

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;


public class SchedulingSimulation {
	static int noPatrons = 10; //number of customers - default value if not provided on command line
	static int sched = 0; //default scheduling algorithm, 0= FCFS, 1=SJF, 2=RR
	static int q = 10000, s = 0;
	static long seed = 0;
	static CountDownLatch startSignal;	
	static Patron[] patrons; // array for customer threads
	static Barman Sarah;

	public static void main(String[] args) throws InterruptedException, IOException {

		//deal with command line arguments if provided
		if (args.length >= 1) noPatrons = Integer.parseInt(args[0]);  //total people to enter room
		if (args.length >= 2) sched = Integer.parseInt(args[1]); 	// alg to use
		if (args.length >= 3) s = Integer.parseInt(args[2]);  //context switch 
		if (args.length >= 4) q = Integer.parseInt(args[3]);  // time slice for RR
		if (args.length >= 5) seed = Integer.parseInt(args[4]); // random number seed- set to compare apples with apples		
		
		// Validate input parameters
		if (sched < 0 || sched > 2) {
			System.err.println("Invalid scheduling algorithm. Must be 0 (FCFS), 1 (SJF), or 2 (RR)");
			System.exit(1);
		}
		
		if (s < 0) {
			System.err.println("Context switch time must be non-negative");
			System.exit(1);
		}
		
		if (q <= 0 && sched == 2) {
			System.err.println("Time quantum for RR must be positive");
			System.exit(1);
		}

		startSignal = new CountDownLatch(noPatrons + 2); //Barman and patrons and main method must be ready
		
		//create barman
        Sarah = new Barman(startSignal, sched, q, s); 
     	Sarah.start();
  
	    //create all the patrons, who all need access to Barman
		patrons = new Patron[noPatrons];
		for (int i = 0; i < noPatrons; i++) {
			patrons[i] = new Patron(i, startSignal, Sarah, seed);
			patrons[i].start();
		}
		
		if (seed > 0) DrinkOrder.random = new Random(seed); // for consistent Patron behaviour
		
		System.out.println("------Sarah the Zombie Barman Scheduling Simulation------");
		System.out.println("-------------- with " + Integer.toString(noPatrons) + " patrons---------------");
		System.out.println("-------------- context switch time: " + s + " ms ---------------");
		
		switch(sched) {
		  case 0:
			  System.out.println("-------------- and FCFS scheduling ---------------");
		    break;
		  case 1:
			  System.out.println("-------------- and SJF scheduling ---------------");
		    break;
		  case 2:
			  System.out.println("-------------- and RR scheduling with q=" + q + " ms -------------");
		}
		
      	startSignal.countDown(); //main method ready
      	
      	//wait till all patrons done, otherwise race condition on the file closing!
      	for (int i = 0; i < noPatrons; i++) patrons[i].join();

    	System.out.println("------Waiting for Barman------");
    	Sarah.interrupt();   //tell Barman to close up
    	Sarah.join(); //wait till she has
      	System.out.println("------Bar closed------");
 	}
}
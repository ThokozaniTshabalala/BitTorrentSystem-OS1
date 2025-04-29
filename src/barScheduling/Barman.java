//M. M. Kuttel 2025 mkuttel@gmail.com
package barScheduling;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/*
 Barman Thread class.
 */

public class Barman extends Thread {
	
	private CountDownLatch startSignal;
	private BlockingQueue<DrinkOrder> orderQueue;
	int schedAlg = 0;
	int q = 10000; //really big if not set, so FCFS
	private int switchTime;
	private long simulationStartTime;
	
	// Maps to store metrics for each order without modifying the DrinkOrder class
	private Map<DrinkOrder, Long> arrivalTimes = new HashMap<>();
	private Map<DrinkOrder, Long> startTimes = new HashMap<>();
	private Map<DrinkOrder, Long> completionTimes = new HashMap<>();
	private Map<DrinkOrder, Integer> switchCounts = new HashMap<>();
	private List<DrinkOrder> completedOrders = new ArrayList<>();
	
	// Algorithm names for file output
	private static final String[] ALGORITHM_NAMES = {"FCFS", "SJF", "RR"};
	
	Barman(CountDownLatch startSignal, int sAlg) {
		//which scheduling algorithm to use
		this.schedAlg = sAlg;
		if (schedAlg == 1) this.orderQueue = new PriorityBlockingQueue<>(5000, Comparator.comparingInt(DrinkOrder::getExecutionTime)); //SJF
		else this.orderQueue = new LinkedBlockingQueue<>(); //FCFS & RR
	    this.startSignal = startSignal;
	}
	
	Barman(CountDownLatch startSignal, int sAlg, int quantum, int sTime) { //overloading constructor for RR which needs q
		this(startSignal, sAlg);
		q = quantum;
		switchTime = sTime;
	}

	public void placeDrinkOrder(DrinkOrder order) throws InterruptedException {
        // Record arrival time when order is placed
        arrivalTimes.put(order, System.currentTimeMillis());
        
        // Initialize switch count to 0
        switchCounts.put(order, 0);
        
        orderQueue.put(order);
    }
	
	public void run() {
		int interrupts = 0;
		
		try {
			DrinkOrder currentOrder;
			
			startSignal.countDown(); //barman ready
			startSignal.await(); //check latch - don't start until told to do so
			
			// Record simulation start time
			simulationStartTime = System.currentTimeMillis();

			if ((schedAlg == 0) || (schedAlg == 1)) { //FCFS and non-preemptive SJF
				while(true) {
					currentOrder = orderQueue.take();
					System.out.println("---Barman preparing drink for patron " + currentOrder.toString());
					
					// Record start time when processing begins
					if (!startTimes.containsKey(currentOrder)) {
					    startTimes.put(currentOrder, System.currentTimeMillis());
					}
					
					sleep(currentOrder.getExecutionTime()); //processing order (="CPU burst")
					System.out.println("---Barman has made drink for patron " + currentOrder.toString());
					
					// Record completion time
					completionTimes.put(currentOrder, System.currentTimeMillis());
					completedOrders.add(currentOrder);
					
					currentOrder.orderDone();
					
					sleep(switchTime);//cost for switching orders
				}
			}
			else { // RR 
				int burst = 0;
				int timeLeft = 0;
				System.out.println("---Barman started with q= " + q);

				while(true) {
					System.out.println("---Barman waiting for next order ");
					currentOrder = orderQueue.take();
					
					// Record start time when processing begins or continues
					if (!startTimes.containsKey(currentOrder)) {
					    startTimes.put(currentOrder, System.currentTimeMillis());
					}

					System.out.println("---Barman preparing drink for patron " + currentOrder.toString());
					burst = currentOrder.getExecutionTime();
					if(burst <= q) { //within the quantum
						sleep(burst); //processing complete order ="CPU burst"
						System.out.println("---Barman has made drink for patron " + currentOrder.toString());
						
						// Record completion time
						completionTimes.put(currentOrder, System.currentTimeMillis());
						completedOrders.add(currentOrder);
						
						currentOrder.orderDone();
					}
					else {
						sleep(q);
						timeLeft = burst - q;
						System.out.println("--INTERRUPT---preparation of drink for patron " + currentOrder.toString() + " time left=" + timeLeft);
						interrupts++;
						
						// Increment switch count for this order
						switchCounts.put(currentOrder, switchCounts.getOrDefault(currentOrder, 0) + 1);
						
						currentOrder.setRemainingPreparationTime(timeLeft);
						orderQueue.put(currentOrder); //put back on queue at end
					}
					sleep(switchTime);//switching orders
				}
			}
				
		} catch (InterruptedException e1) {
			System.out.println("---Barman is packing up ");
			System.out.println("---number interrupts=" + interrupts);
			
			// When the simulation ends, write metrics to file
			try {
				writeMetricsToFile();
			} catch (IOException e) {
				System.err.println("Error writing metrics to file: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
	private void writeMetricsToFile() throws IOException {
		// Create a file for the metrics with the scheduling algorithm in the filename
		String algorithm = ALGORITHM_NAMES[schedAlg];
		String filename = "metrics_" + algorithm + "_q" + q + "_switch" + switchTime + ".csv";
		
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
			// Write CSV header
			writer.write("PatronID,DrinkName,ExecutionTime,ArrivalTime,StartTime,CompletionTime,WaitingTime,TurnaroundTime,ResponseTime,SwitchCount\n");
			
			// Write data for each completed order
			for (DrinkOrder order : completedOrders) {
				long arrival = arrivalTimes.getOrDefault(order, 0L);
				long start = startTimes.getOrDefault(order, 0L);
				long completion = completionTimes.getOrDefault(order, 0L);
				int switchCount = switchCounts.getOrDefault(order, 0);
				
				// Calculate metrics
				long waitingTime = start - arrival;
				long turnaroundTime = completion - arrival;
				long responseTime = start - arrival;
				
				// Calculate relative times from simulation start
				long relativeArrival = arrival - simulationStartTime;
				long relativeStart = start - simulationStartTime;
				long relativeCompletion = completion - simulationStartTime;
				
				writer.write(String.format("%d,%s,%d,%d,%d,%d,%d,%d,%d,%d\n",
						Integer.parseInt(order.toString().split(":")[0].trim()),  // Patron ID
						order.toString().split(":")[1].trim(),  // Drink name
						order.getExecutionTime(),
						relativeArrival,
						relativeStart,
						relativeCompletion,
						waitingTime,
						turnaroundTime,
						responseTime,
						switchCount));
			}
		}
		
		// Write a summary metrics file
		String summaryFilename = "summary_" + algorithm + "_q" + q + "_switch" + switchTime + ".txt";
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFilename))) {
			// Calculate averages for each metric
			double avgWaitingTime = completedOrders.stream()
					.mapToLong(order -> startTimes.getOrDefault(order, 0L) - arrivalTimes.getOrDefault(order, 0L))
					.average().orElse(0);
			
			double avgTurnaroundTime = completedOrders.stream()
					.mapToLong(order -> completionTimes.getOrDefault(order, 0L) - arrivalTimes.getOrDefault(order, 0L))
					.average().orElse(0);
			
			double avgResponseTime = completedOrders.stream()
					.mapToLong(order -> startTimes.getOrDefault(order, 0L) - arrivalTimes.getOrDefault(order, 0L))
					.average().orElse(0);
			
			// Calculate medians
			List<Long> waitingTimes = completedOrders.stream()
					.map(order -> startTimes.getOrDefault(order, 0L) - arrivalTimes.getOrDefault(order, 0L))
					.sorted().toList();
			
			List<Long> turnaroundTimes = completedOrders.stream()
					.map(order -> completionTimes.getOrDefault(order, 0L) - arrivalTimes.getOrDefault(order, 0L))
					.sorted().toList();
			
			List<Long> responseTimes = completedOrders.stream()
					.map(order -> startTimes.getOrDefault(order, 0L) - arrivalTimes.getOrDefault(order, 0L))
					.sorted().toList();
			
			long medianWaitingTime = waitingTimes.get(waitingTimes.size() / 2);
			long medianTurnaroundTime = turnaroundTimes.get(turnaroundTimes.size() / 2);
			long medianResponseTime = responseTimes.get(responseTimes.size() / 2);
			
			// Calculate maximum values
			long maxWaitingTime = waitingTimes.get(waitingTimes.size() - 1);
			long maxTurnaroundTime = turnaroundTimes.get(turnaroundTimes.size() - 1);
			long maxResponseTime = responseTimes.get(responseTimes.size() - 1);
			
			// Average switch count for RR
			double avgSwitchCount = completedOrders.stream()
					.mapToInt(order -> switchCounts.getOrDefault(order, 0))
					.average().orElse(0);
			
			// Write summary statistics
			writer.write("=== Summary Statistics for " + algorithm + " ===\n");
			writer.write("Time Quantum: " + q + "\n");
			writer.write("Context Switch Time: " + switchTime + "\n");
			writer.write("Total Orders Completed: " + completedOrders.size() + "\n\n");
			
			writer.write("--- Waiting Time ---\n");
			writer.write(String.format("Average: %.2f ms\n", avgWaitingTime));
			writer.write(String.format("Median: %d ms\n", medianWaitingTime));
			writer.write(String.format("Maximum: %d ms\n\n", maxWaitingTime));
			
			writer.write("--- Turnaround Time ---\n");
			writer.write(String.format("Average: %.2f ms\n", avgTurnaroundTime));
			writer.write(String.format("Median: %d ms\n", medianTurnaroundTime));
			writer.write(String.format("Maximum: %d ms\n\n", maxTurnaroundTime));
			
			writer.write("--- Response Time ---\n");
			writer.write(String.format("Average: %.2f ms\n", avgResponseTime));
			writer.write(String.format("Median: %d ms\n", medianResponseTime));
			writer.write(String.format("Maximum: %d ms\n", maxResponseTime));
			
			if (schedAlg == 2) { // Only for RR
				writer.write("\n--- Context Switches ---\n");
				writer.write(String.format("Average switches per order: %.2f\n", avgSwitchCount));
			}
		}
		
		System.out.println("Metrics written to " + filename + " and " + summaryFilename);
	}
}
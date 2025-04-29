#!/bin/bash

# Parameters
NUM_PATRONS=30
SEED=42  # Fixed seed for reproducibility
ITERATIONS=5  # Number of iterations for each configuration

# Context switch times to test
CONTEXT_SWITCH_TIMES=(2 5 10 20)

# Time quantum values to test for RR
TIME_QUANTUMS=(10 20 30 50 75 100)

# Function to run a single test
run_test() {
  local alg=$1
  local cs=$2
  local q=$3
  local seed=$4
  local iteration=$5

  echo "Running test: Algorithm=$alg, Context Switch=$cs, Quantum=$q, Iteration=$iteration"

  # Ensure compiled
  make

  # Run with correct classpath and package
  java -cp bin barScheduling.SchedulingSimulation $NUM_PATRONS $alg $cs $q $seed

  # Create a directory to store this run's results
  mkdir -p "results/alg${alg}_cs${cs}_q${q}/iteration${iteration}"

  # Move the metrics files to the results directory
  mv metrics_*.csv results/alg${alg}_cs${cs}_q${q}/iteration${iteration}/ 2>/dev/null
  mv summary_*.txt results/alg${alg}_cs${cs}_q${q}/iteration${iteration}/ 2>/dev/null
}

# Create results directory
mkdir -p results

echo "Starting tests..."

# 1. Test FCFS with various context switch times
echo "Testing different context switch times with FCFS..."
for cs in "${CONTEXT_SWITCH_TIMES[@]}"; do
  for i in $(seq 1 $ITERATIONS); do
    run_test 0 $cs 10000 $SEED $i
  done
done

# 2. Use an optimal context switch time for SJF and RR (adjust after analysis)
CS_OPTIMAL=5
echo "Testing SJF with optimal context switch time..."
for i in $(seq 1 $ITERATIONS); do
  run_test 1 $CS_OPTIMAL 10000 $SEED $i
done

# 3. Test various quantum values for RR
echo "Testing different quantum values for RR..."
for q in "${TIME_QUANTUMS[@]}"; do
  for i in $(seq 1 $ITERATIONS); do
    run_test 2 $CS_OPTIMAL $q $SEED $i
  done
done

# 4. Final comparative tests with selected optimal parameters
echo "Running final comparative tests..."
Q_OPTIMAL=30  # Adjust based on RR test results

# FCFS
for i in $(seq 1 $ITERATIONS); do
  run_test 0 $CS_OPTIMAL 10000 $SEED $i
done

# SJF
for i in $(seq 1 $ITERATIONS); do
  run_test 1 $CS_OPTIMAL 10000 $SEED $i
done

# RR
for i in $(seq 1 $ITERATIONS); do
  run_test 2 $CS_OPTIMAL $Q_OPTIMAL $SEED $i
done

echo "All tests completed."
echo "Now analyze the results to finalize CS and Q values."

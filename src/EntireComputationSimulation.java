import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;


public class EntireComputationSimulation extends Thread
{
	String FILE_NAME;
	String RESULTS_FOLDER;
	List<Integer> computationNumbers;
	Map<String, Float> sortedComputationAreaMap;
	int INDEX;
	
	public EntireComputationSimulation(String fileName, 
									   String resultsFolder,
									   List<Integer> computationNumbers,
									   Map<String, Float> sortedComputationAreaMap, 
									   int index)
	{
		FILE_NAME = fileName;
		RESULTS_FOLDER = resultsFolder;
		this.computationNumbers = computationNumbers;
		this.sortedComputationAreaMap = sortedComputationAreaMap;
		INDEX = index;
		
	}
	
	public void run()
	{
		for (int computationNumber : computationNumbers) {
			try {
				System.out.println("Running simluation for: " + computationNumber);
				
				List<Integer> computation = getComputation(computationNumber);
				
				generateEntireComputationInputFile(computation, computationNumber);
				
				Long start = System.currentTimeMillis();
				
				int result = runOCMExecutable();
				
				Long end = System.currentTimeMillis();
				
				if (result == -1){
					System.out.println("In use");
					return;
				}else if (result == -2){
					System.out.println("Failed");
					return;
				}
				
				String parentFolder;
				if (FILE_NAME.contains("bd")) {
					parentFolder = "ocm_" + INDEX + "/BigDitchWs/";
				} else {
					parentFolder = "ocm_" + INDEX + "/BigLongCreekWs/";
				}
				
				copyFile(parentFolder + "ClientRequestOutput.txt", RESULTS_FOLDER + "/client_output_entire_computation_" + computationNumber + ".txt");
				copyFile(parentFolder + "output.hru", RESULTS_FOLDER + "/output_" + computationNumber + ".hru");
				copyFile(parentFolder + "output.rch", RESULTS_FOLDER + "/output_" + computationNumber + ".rch");
				copyFile(parentFolder + "output.sed", RESULTS_FOLDER + "/output_" + computationNumber + ".sed");
				copyFile(parentFolder + "output.std", RESULTS_FOLDER + "/output_" + computationNumber + ".std");
				copyFile(parentFolder + "output.sub", RESULTS_FOLDER + "/output_" + computationNumber + ".sub");
				copyFile(parentFolder + "watout.dat", RESULTS_FOLDER + "/watout_" + computationNumber + ".dat");
				
				System.out.println("Entire Computation " + computationNumber + " Simulated: " + (((double) end - start)/1000));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	
	/*
	 * Helper method to run the OCM fortran executable.
	 */
	private int runOCMExecutable() {
		Process process = null;
		String workingDirectory;
		if (FILE_NAME.contains("bd")) {
			workingDirectory = "ocm_" + INDEX + "/BigDitchWs/";
		} else {
			workingDirectory = "ocm_" + INDEX + "/BigLongCreekWs/";
		}
		
		try {
			ProcessBuilder builder = new ProcessBuilder(workingDirectory +"OCM_AMGA2_IndBMP_LHS.exe");
			builder.directory(new File(workingDirectory));
			builder.redirectErrorStream(true);
			File output = new File(workingDirectory+"spark_ocm_output.txt");
			builder.redirectOutput(output);
			
			process = builder.start();
		} catch (Exception e) {
			System.out.println("Error: " + workingDirectory +  e.getMessage());
			e.printStackTrace();
			return -1;
		}

		try {
			//System.out.println("Execution Started : " + (new Date()).toString());
			String result = null;
			process.waitFor();
			//System.out.println("Execution completed : " + (new Date()).toString());
			result = FileUtils.readFileToString(new File(workingDirectory + "spark_ocm_output.txt"));
			if (result == null || !result.contains("Execution successfully completed")){
				//System.out.println("***Execution Failed***");
				return -2;
			}
			else{
				//System.out.println("***Execution Successful***");
			}
		} catch (Exception e) {
			// Handle exception that could occur when waiting
			// for a spawned process to terminate
			//System.out.println("***Exception Occurred***");
			e.printStackTrace();
			return -2;
		}
		return 0;
	}
	
	/**
	 * Generates the input file for the subbasin (to be used by the model) and places a copy
	 * in the results folder
	 * 
	 * @param finalVals
	 * @param subbasinId
	 * @throws IOException
	 */
	public void generateEntireComputationInputFile(List<Integer> finalVals, int computationNumber) throws IOException
	{
		BufferedWriter bw, bwCopy = new BufferedWriter(new FileWriter(RESULTS_FOLDER + "/result_input_entire_computation_" + computationNumber + ".txt"));
		if (FILE_NAME.contains("bd")) {
			bw = new BufferedWriter(new FileWriter("ocm_" + INDEX + "/BigDitchWs/Client_Input.txt"));
			bw.write("1\n");
			bw.write("1\n");
			bwCopy.write("1\n");
			bwCopy.write("1\n");
		} else {
			bw = new BufferedWriter(new FileWriter("ocm_" + INDEX + "/BigLongCreekWs/Client_Input.txt"));
			bw.write("1\n");
			bw.write("2\n");
			bwCopy.write("1\n");
			bwCopy.write("2\n");
		}
		
		for (int val : finalVals) {
			bw.write(val + "\n");
			bwCopy.write(val + "\n");
		}
		
		bw.close();
		bwCopy.close();
	}
	
	/**
	 * Copies the file from src to destination
	 * @param read
	 * @param write
	 * @throws IOException
	 */
	public void copyFile(String read, String write) throws IOException
	{
		BufferedReader br = new BufferedReader(new FileReader(read));
		BufferedWriter bw = new BufferedWriter(new FileWriter(write));
		String str;
		
		while ((str = br.readLine()) != null) {
			bw.write(str + "\n");
		}
		
		br.close();
		bw.close();
	}
	

	/**
	 * Gets a particular computation from the sorted list of computations (based on the
	 * input of the user)
	 * @param sortedComputationAreaMap
	 * @return
	 */
	private List<Integer> getComputation(int computationNumber)
	{
		String s = "";
		int count = 0;
		
		for (Entry<String, Float> e : sortedComputationAreaMap.entrySet()) {
			count++;
			if (count == computationNumber) {
				s = e.getKey();
			}
		}
		
		String[] strList = s.split("( |\t)+");
		
		// Reference array computed here
		List<Integer> arr = new ArrayList<>();
		for (int i=0; i<strList.length - 6; i++) {
			arr.add(Integer.parseInt(strList[i]));
		}
		
		return arr;
	}
}

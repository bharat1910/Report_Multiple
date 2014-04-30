import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;

public class EachSubbasinComputation extends Thread
{
	String FILE_NAME;
	String RESULTS_FOLDER;
	int INDEX;
	
	Map<String, Float> sortedComputationAreaMap;
	Map<Integer, Integer> referenceMap;
	List<Integer> computation;
	Map<Integer, List<Integer>> hrusForSubbasin;
	Map<String, Float> baseCaseResultValues;
	List<Integer> subbasins;
	int plotComputation;
	public static Lock fileWriteLock = new ReentrantLock();
	
	public EachSubbasinComputation(String fileName,
								   String resultsFolder,
								   Map<String, Float> sortedComputationAreaMap,
								   Map<Integer, Integer> referenceMap,
								   List<Integer> computation, Map<Integer,
								   List<Integer>> hrusForSubbasin,
								   Map<String, Float> baseCaseResultValues,
								   List<Integer> subbasins,
								   int index,
								   int plotComputation)
	{
		FILE_NAME = fileName;
		
		RESULTS_FOLDER = resultsFolder;

		// Sorts the computations by Area
		this.sortedComputationAreaMap = sortedComputationAreaMap;
		

		// Computes the reference map. A map of HRU number to the index it
		// corresponds to
		this.referenceMap = referenceMap;
		

		// Based on the computation specified (in the input), the
		// corresponding value is
		// picked from the list of computations
		this.computation = computation;
		

		// Gets the list of HRUs corresponding to each subbasin
		this.hrusForSubbasin = hrusForSubbasin;
		

		// Gets the base case values by parsing the result file generated by
		// the executable
		this.baseCaseResultValues = baseCaseResultValues;
		
		this.subbasins = subbasins;
		
		INDEX = index;
		
		this.plotComputation = plotComputation;
	}

	
	private void recursiveCopy(File fSource, File fDest) {
	     try {
	          if (fSource.isDirectory()) {
	          // A simple validation, if the destination is not exist then create it
	               if (!fDest.exists()) {
	                    fDest.mkdirs();
	               }
	 
	               // Create list of files and directories on the current source
	               // Note: with the recursion 'fSource' changed accordingly
	               String[] fList = fSource.list();
	 
	               for (int index = 0; index < fList.length; index++) {
	                    File dest = new File(fDest, fList[index]);
	                    File source = new File(fSource, fList[index]);
	 
	                    // Recursion call take place here
	                    recursiveCopy(source, dest);
	               }
	          }
	          else {
	               // Found a file. Copy it into the destination, which is already created in 'if' condition above
	 
	               // Open a file for read and write (copy)
	               FileInputStream fInStream = new FileInputStream(fSource);
	               FileOutputStream fOutStream = new FileOutputStream(fDest);
	 
	               // Read 2K at a time from the file
	               byte[] buffer = new byte[2048];
	               int iBytesReads;
	 
	               // In each successful read, write back to the source
	               while ((iBytesReads = fInStream.read(buffer)) >= 0) {
	                    fOutStream.write(buffer, 0, iBytesReads);
	               }
	 
	               // Safe exit
	               if (fInStream != null) {
	                    fInStream.close();
	               }
	 
	               if (fOutStream != null) {
	                    fOutStream.close();
	               }
	          }
	     }
	     catch (Exception ex) {
	          // Please handle all the relevant exceptions here
	     }
	}
	
	public void run()
	{
		File dir = new File("ocm_" + INDEX);
		if (!dir.exists()) {
			System.out.println("Creating a copy of ocm : " + "ocm_" + INDEX + " ...");
			recursiveCopy(new File("ocm"), new File("ocm_" + INDEX));			
		}

		try {
			String str;
			if (FILE_NAME.contains("bd")) {
				 str = "ocm_" + INDEX + "/BigDitchWs/ClientRequestOutput.txt";	
			} else {
				str = "ocm_" + INDEX + "/BigLongCreekWs/ClientRequestOutput.txt";			
			}
			
			for (int subbasin : subbasins) {
				List<Integer> finalVals = generateInputFileDataForSubbasin(subbasin);
				generateInputFileForSubbasin(finalVals, subbasin);
				
				if (plotComputation == 2) {
					System.out.println("Thread " + INDEX + " is running the corresponding executable for Subbasin : " + subbasin + " ...");					
				} else {
					System.out.println("Thread " + INDEX + " is running the corresponding executable for HRU : " + subbasin + " ...");
				}

				Long start = System.currentTimeMillis();
				
				int result = runOCMExecutable();
				
				Long end = System.currentTimeMillis();
				if (plotComputation == 2) {
					System.out.println("Thread " + INDEX + " has finished running the executable for subbasin : " + subbasin + ". Time taken : " + (((double) end - start)/1000) + ".");					
				} else {
					System.out.println("Thread " + INDEX + " has finished running the executable for HRU : " + subbasin + ". Time taken : " + (((double) end - start)/1000) + ".");
				}
			
				
				if (result == -1) {
					System.out.println("In use");
					return;
				} else if (result == -2) {
					System.out.println("Failed");
					return;
				}

				
				if (plotComputation == 2) {
					copyFile(str, RESULTS_FOLDER + "/Subbasin/result_output_" + subbasin
							+ ".txt");
				} else {
					copyFile(str, RESULTS_FOLDER + "/HRU/result_output_" + subbasin
							+ ".txt");

				}

				// Computes the pollutant load percent reduction
				Map<String, Float> resultValues = getBaseCaseResultValues();
				Float val1 = (baseCaseResultValues.get("sed") - resultValues
						.get("sed")) / baseCaseResultValues.get("sed") * 100;
				Float val2 = (baseCaseResultValues.get("nit") - resultValues
						.get("nit")) / baseCaseResultValues.get("nit") * 100;
				Float val3 = (baseCaseResultValues.get("pho") - resultValues
						.get("pho")) / baseCaseResultValues.get("pho") * 100;

				fileWriteLock.lock();
				PrintWriter resultWrite = new PrintWriter(new BufferedWriter(new FileWriter(
					RESULTS_FOLDER + "/result.csv", true)));
				resultWrite.write(subbasin + "," + val1 + "," + val2 + ","
						+ val3 + "\n");
				resultWrite.close();
				fileWriteLock.unlock();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		}

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
	 * Gets the base case values (based on the computation of the executable)
	 * @return
	 * @throws IOException
	 */
	private Map<String, Float> getBaseCaseResultValues() throws IOException
	{
		BufferedReader br;
		String str;
		if (FILE_NAME.contains("bd")) {
			 br = new BufferedReader(new FileReader("ocm_" + INDEX + "/BigDitchWs/ClientRequestOutput.txt"));	
		} else {
			 br = new BufferedReader(new FileReader("ocm_" + INDEX + "/BigLongCreekWs/ClientRequestOutput.txt"));			
		}
		
		str = br.readLine();
		str = br.readLine();
		
		String[] strList = str.trim().split("( |\t)+");
		
		Map<String, Float> map = new HashMap<>();
		map.put("sed", Float.parseFloat(strList[0]));
		map.put("nit", Float.parseFloat(strList[1]));
		map.put("pho", Float.parseFloat(strList[2]));
		
		br.close();
		
		return map;
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
	public void generateInputFileForSubbasin(List<Integer> finalVals, int subbasin) throws IOException
	{
		BufferedWriter bw, bwCopy;
		
		if (plotComputation == 2) {
			bwCopy = new BufferedWriter(new FileWriter(RESULTS_FOLDER + "/Subbasin/result_input_" + subbasin + ".txt"));				
		} else {
			bwCopy = new BufferedWriter(new FileWriter(RESULTS_FOLDER + "/HRU/result_input_" + subbasin + ".txt"));
		}
		
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
	 * Generates the value to be keyed in to the input file for the subbasin. Basically,
	 * non zero values are assigned to only those HRU's that are a part of the subbasin 
	 * bing simulated, provided the computation being referenced has a non zero value as
	 * well.
	 * 
	 * @param referenceMap
	 * @param computation
	 * @param hrusForSubbasin
	 * @param subbasin
	 * @return
	 */
	private List<Integer> generateInputFileDataForSubbasin(int subbasin)
	{
		List<Integer> acceptedHrus = hrusForSubbasin.get(subbasin);
		List<Integer> finalVals = new ArrayList<>();
		
		for (int i = 0; i < computation.size(); i++) {
			finalVals.add(0);
		}
		
		for (Integer i : referenceMap.keySet()) {
			if (acceptedHrus.contains(i)) {
				finalVals.set(referenceMap.get(i), computation.get(referenceMap.get(i)));
			}
		}
		
		return finalVals;
	}
	
	public void simulate() throws NumberFormatException, IOException
	{
		run();	
	}
}